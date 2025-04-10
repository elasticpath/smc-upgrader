package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.IndexDiff;

/**
 * Resolves merge conflicts.
 */
public class MergeConflictResolver extends AbstractConflictResolver {
	private final ChangeFactory changeFactory;

	private final ChangeContentsEquivalence changeContentsEquivalence;

	private final ConflictResolutionDeterminer conflictResolutionDeterminer;

	private final SafeOverwriteDeterminer safeOverwriteDeterminer;

	/**
	 * Constructor.
	 *
	 * @param gitClient the Git client
	 */
	public MergeConflictResolver(final GitClient gitClient) {
		super(gitClient);
		changeFactory = new ChangeFactory();
		changeContentsEquivalence = new ChangeContentsEquivalence(gitClient);
		conflictResolutionDeterminer = new ConflictResolutionDeterminer();
		safeOverwriteDeterminer = new SafeOverwriteDeterminer(gitClient);
	}

	/**
	 * Resolves merge conflicts.
	 *
	 * @param upstreamRemoteName the name of the upstream remote
	 */
	public void resolveMergeConflicts(final String upstreamRemoteName) {
		final Map<String, IndexDiff.StageState> conflicts = getGitClient().getConflicts();
		final Set<IndexEntry> statusIndexEntries = getGitClient().getStatusIndexEntries();

		LOGGER.info("\n" + conflicts.size() + " conflict(s) found.");

		if (!conflicts.isEmpty()) {
			LOGGER.info("Processing merge conflicts to attempt automatic resolution...");
		}

		final List<Change> mergeConflictChanges = changeFactory.createChanges(conflicts, statusIndexEntries);

		long resolvedDiffCount = ProcessCollectionInParallelWithProgress.process(mergeConflictChanges, change -> {
			ConflictResolutionStrategy strategy = conflictResolutionDeterminer.determineResolution(change,
					() -> safeOverwriteDeterminer.pathIsSafeToOverwrite(change.getPath(), upstreamRemoteName),
					() -> changeContentsEquivalence.oursTheirsChangeContentsAreEqual(change));
			synchronized (this) {
				resolveConflict(change, strategy);
			}
			return strategy == ConflictResolutionStrategy.ACCEPT_THEIRS;
		});

		if (resolvedDiffCount > 0) {
			LOGGER.info("Resolved " + resolvedDiffCount + " conflict(s) by accepting the upstream change.");
		}
	}

	private void resolveConflict(final Change change, final ConflictResolutionStrategy conflictResolutionStrategy) {
		if (conflictResolutionStrategy == ConflictResolutionStrategy.ACCEPT_OURS) {
			LOGGER.debug("Resolving conflict on " + change.getPath() + " by rejecting the incoming change.");
			resolveConflictOurs(change);
		} else if (conflictResolutionStrategy == ConflictResolutionStrategy.ACCEPT_THEIRS) {
			LOGGER.debug("Resolving conflict on " + change.getPath() + " by accepting the incoming change.");
			resolveConflictTheirs(change);
		} else {
			LOGGER.debug("Manual resolution required for file " + change.getPath());
		}
	}
}
