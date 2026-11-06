package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.util.ArrayList;
import java.util.Collections;
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
	 * @param report             receives a bullet for each file resolved with the upstream version
	 */
	public void resolveDiffConflicts(final String upstreamRemoteName, final String version, final UpgradeReport report) {
		final Ref releaseBranch = getGitClient().getReleaseBranch(upstreamRemoteName, version);
		final List<DiffEntry> diff = getGitClient().getDiff(releaseBranch);

		final List<Change> diffConflictChanges = changeFactory.createChanges(diff);
		final List<String> fileChanges = Collections.synchronizedList(new ArrayList<>());

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
			if (strategy == ConflictResolutionStrategy.ACCEPT_THEIRS) {
				fileChanges.add(UpgradeReport.fileChange(change.getPath(), explainTheirs(change)));
			}
			return strategy == ConflictResolutionStrategy.ACCEPT_THEIRS;
		});

		Collections.sort(fileChanges);
		report.addFileChanges(fileChanges);
		report.setDiffPassChangeCount(resolvedDiffCount);

		if (resolvedDiffCount > 0) {
			LOGGER.info("Resolved {} diff(s) by accepting the upstream change.", resolvedDiffCount);
		}
	}

	/**
	 * Explains a difference resolved with the upstream version.
	 *
	 * @param change the resolved difference
	 * @return the explanation
	 */
	static String explainTheirs(final Change change) {
		final String action;
		if (resolvingTheirsDeletesFile(change)) {
			action = "deleted";
		} else if (change.getTheirChangeType() == ChangeType.CREATED) {
			action = "restored from theirs";
		} else {
			action = "replaced with theirs";
		}
		return action + " because it was out of date and matched a known older platform version, even though it had no conflict.";
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
