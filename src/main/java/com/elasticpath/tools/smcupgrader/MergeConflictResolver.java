package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.IndexDiff;

/**
 * Resolves merge conflicts.
 */
public class MergeConflictResolver extends AbstractConflictResolver {
	private final ChangeFactory changeFactory;

	private final ChangeContentsEquivalence changeContentsEquivalence;

	private final ConflictResolutionDeterminer conflictResolutionDeterminer;

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
	}

	/**
	 * Resolves merge conflicts.
	 *
	 * @param upstreamRemoteName the name of the upstream remote
	 * @return a list of {@link Change} instances representing the changes that could not be resolved automatically
	 */
	public List<Change> resolveMergeConflicts(final String upstreamRemoteName) {
		final Map<String, IndexDiff.StageState> conflicts = getGitClient().getConflicts();
		final Set<IndexEntry> statusIndexEntries = getGitClient().getStatusIndexEntries();

		LOGGER.info("\n" + conflicts.size() + " conflict(s) found.");

		if (!conflicts.isEmpty()) {
			LOGGER.info("Processing merge conflicts to attempt automatic resolution...");
		}

		final List<Change> mergeConflictChanges = changeFactory.createChanges(conflicts, statusIndexEntries);

		final Map<Change, ConflictResolutionStrategy> changeResolutionMap = mergeConflictChanges.stream()
				.collect(Collectors.toMap(Function.identity(),
						change -> conflictResolutionDeterminer.determineResolution(change,
								() -> getGitClient().allLocalCommitsExistInRemote(change, upstreamRemoteName),
								() -> changeContentsEquivalence.oursTheirsChangeContentsAreEqual(change))));

		changeResolutionMap.forEach(this::resolveConflict);

		final long resolvedDiffCount = changeResolutionMap.values().stream()
				.filter(conflictResolutionStrategy -> conflictResolutionStrategy == ConflictResolutionStrategy.ACCEPT_THEIRS)
				.count();

		if (resolvedDiffCount > 0) {
			LOGGER.info("Resolved " + resolvedDiffCount + " conflict(s) by accepting the upstream change.");
		}

		return changeResolutionMap.entrySet().stream()
				.filter(entry -> entry.getValue() == ConflictResolutionStrategy.MANUAL_RESOLUTION_REQUIRED)
				.map(Map.Entry::getKey)
				.sorted(Comparator.comparing(Change::getPath))
				.collect(Collectors.toList());
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
