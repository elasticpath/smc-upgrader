package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Ref;

/**
 * Resolves diff conflicts.
 */
public class DiffConflictResolver extends AbstractConflictResolver {
	private final ChangeFactory changeFactory;

	private final DiffResolutionDeterminer diffResolutionDeterminer;

	private final SafeOverwriteDeterminer safeOverwriteDeterminer;

	/**
	 * Constructor.
	 *
	 * @param gitClient the git client
	 */
	public DiffConflictResolver(final GitClient gitClient) {
		super(gitClient);
		changeFactory = new ChangeFactory();
		diffResolutionDeterminer = new DiffResolutionDeterminer();
		safeOverwriteDeterminer = new SafeOverwriteDeterminer(gitClient);
	}

	/**
	 * Resolves diff conflicts.
	 *
	 * @param upstreamRemoteName the name of the upstream remote
	 * @param version            the version being upgraded to
	 */
	public void resolveDiffConflicts(final String upstreamRemoteName, final String version) {
		final Ref releaseBranch = getGitClient().getReleaseBranch(upstreamRemoteName, version);
		final List<DiffEntry> diff = getGitClient().getDiff(releaseBranch);

		final List<Change> diffConflictChanges = changeFactory.createChanges(diff);

		LOGGER.info(diffConflictChanges.size() + " non-conflict file(s) found that differ from upstream " + version + " branch.");

		if (!diffConflictChanges.isEmpty()) {
			LOGGER.info("Processing diffs to attempt automatic resolution...");
		}

		long resolvedDiffCount = ProcessCollectionInParallelWithProgress.process(diffConflictChanges, change -> {
			ConflictResolutionStrategy strategy = diffResolutionDeterminer.determineResolution(change,
					() -> safeOverwriteDeterminer.pathIsSafeToOverwrite(change.getPath(), upstreamRemoteName)
			);
			synchronized (this) {
				resolveConflict(change, strategy);
			}
			return strategy == ConflictResolutionStrategy.ACCEPT_THEIRS;
		});

		if (resolvedDiffCount > 0) {
			LOGGER.info("Resolved {} diff(s) by accepting the upstream change.", resolvedDiffCount);
		}
	}

	private void resolveConflict(final Change change, final ConflictResolutionStrategy conflictResolutionStrategy) {
		if (conflictResolutionStrategy == ConflictResolutionStrategy.ACCEPT_THEIRS) {
			LOGGER.debug("Resolving diff on " + change.getPath() + " by accepting the upstream change.");
			resolveConflictTheirs(change);
		} else {
			LOGGER.debug("Ignoring diff on " + change.getPath() + ".");
		}
	}
}
