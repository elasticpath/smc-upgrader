package com.elasticpath.tools.smcupgrader;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elasticpath.tools.smcupgrader.impl.GitClientImpl;

/**
 * Main class that orchestrates a code update, by merging an upstream branch and resolving conflicts.
 */
public class UpgradeController {
	/**
	 * The Logger for the application.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(UpgradeController.class);

	private final UpstreamRemoteManager upstreamRemoteManager;

	private final Merger merger;

	private final MergeConflictResolver mergeConflictResolver;

	private final DiffConflictResolver diffConflictResolver;

	/**
	 * Constructor.
	 *
	 * @param workingDir                  the working directory containing the git repo to be upgraded
	 * @param upstreamRemoteRepositoryUrl the URL of the upstream repository containing upgrade commits
	 */
	public UpgradeController(final File workingDir, final String upstreamRemoteRepositoryUrl) {
		final FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try {
			// scan environment GIT_* variables
			final Repository repository = builder.setGitDir(new File(workingDir, ".git"))
					.readEnvironment() // scan environment GIT_* variables
					.build();

			final GitClient gitClient = new GitClientImpl(repository);
			upstreamRemoteManager = new UpstreamRemoteManager(gitClient, upstreamRemoteRepositoryUrl);
			merger = new Merger(gitClient);
			mergeConflictResolver = new MergeConflictResolver(gitClient);
			diffConflictResolver = new DiffConflictResolver(gitClient);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Performs the upgrade, by merging an upstream branch and resolving conflicts.
	 * Setting both {@code doMerge} and {@code doConflictResolution} to {@code false} will not modify the local working state and is akin to a dry
	 * run.
	 *
	 * @param version              the target version to upgrade to
	 * @param doMerge              perform the code merge
	 * @param doConflictResolution perform conflict resolution
	 * @param doDiffResolution     perform diff resolution
	 */
	public void performUpgrade(final String version, final boolean doMerge, final boolean doConflictResolution, final boolean doDiffResolution) {
		final String upstreamRemoteName = upstreamRemoteManager.getUpstreamRemoteName();

		LOGGER.debug("Upgrading from remote repository '{}'", upstreamRemoteName);

		if (doMerge) {
			merger.merge(upstreamRemoteName, version);
			LOGGER.info("Merge succeeded.");
		} else {
			LOGGER.info("Skipping merge.");
		}

		if (doConflictResolution) {
			mergeConflictResolver.resolveMergeConflicts(upstreamRemoteName);
		} else {
			LOGGER.info("Skipping merge conflict resolution.");
		}

		if (doDiffResolution) {
			diffConflictResolver.resolveDiffConflicts(upstreamRemoteName, version);
		} else {
			LOGGER.info("Skipping diff conflict resolution.");
		}

		LOGGER.info("\n\nUse your IDE to resolve any remaining merge conflicts, or run the following command:\n\n"
				+ "git mergetool\n\n"
				+ "Once all conflicts have been resolved, stage the changes and commit to complete the merge:\n\n"
				+ "git add -A .\n"
				+ "git commit");
	}
}
