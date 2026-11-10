package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
	 * @param report             receives a bullet for each file resolved with the upstream version
	 */
	public void resolveMergeConflicts(final String upstreamRemoteName, final UpgradeReport report) {
		final Map<String, IndexDiff.StageState> conflicts = getGitClient().getConflicts();
		final Set<IndexEntry> statusIndexEntries = getGitClient().getStatusIndexEntries();

		LOGGER.info(conflicts.size() + " conflict(s) found.");

		if (!conflicts.isEmpty()) {
			LOGGER.info("Processing merge conflicts to attempt automatic resolution...");
		}

		final List<Change> mergeConflictChanges = changeFactory.createChanges(conflicts, statusIndexEntries).stream()
				.filter(change -> !Constants.isUpgraderFile(change.getPath()))
				.collect(Collectors.toList());
		final List<String> fileChanges = Collections.synchronizedList(new ArrayList<>());

		long resolvedDiffCount = ProcessCollectionInParallelWithProgress.process(mergeConflictChanges, change -> {
			ConflictResolutionStrategy strategy = conflictResolutionDeterminer.determineResolution(change,
					() -> safeOverwriteDeterminer.pathIsSafeToOverwrite(change.getPath(), upstreamRemoteName),
					() -> changeContentsEquivalence.oursTheirsChangeContentsAreEqual(change));
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
		report.setConflictPassChangeCount(resolvedDiffCount);

		if (resolvedDiffCount > 0) {
			LOGGER.info("Resolved " + resolvedDiffCount + " conflict(s) by accepting the upstream change.");
		}
	}

	/**
	 * Explains a conflict resolved with the upstream version.
	 *
	 * @param change the resolved conflict
	 * @return the explanation
	 */
	static String explainTheirs(final Change change) {
		if (change.getTheirChangeType() == ChangeType.DELETED) {
			return "deleted because the target release deleted it.";
		}
		final String action = resolvingTheirsDeletesFile(change) ? "deleted" : "replaced with theirs";
		return action + " because its contents matched a known older platform version.";
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
