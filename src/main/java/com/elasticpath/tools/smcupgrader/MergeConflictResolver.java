package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.util.ArrayList;
import java.util.Comparator;
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
	 * @return one journal entry for each conflicted file, sorted by path
	 */
	public PassResult resolveMergeConflicts(final String upstreamRemoteName) {
		final Map<String, IndexDiff.StageState> conflicts = getGitClient().getConflicts();
		final Set<IndexEntry> statusIndexEntries = getGitClient().getStatusIndexEntries();
		LOGGER.info(conflicts.size() + " conflict(s) found.");

		if (!conflicts.isEmpty()) {
			LOGGER.info("Processing merge conflicts to attempt automatic resolution...");
		}

		final List<Change> mergeConflictChanges = changeFactory.createChanges(conflicts, statusIndexEntries);
		final List<JournalEntry> entries = new ArrayList<>();

		long resolvedDiffCount = ProcessCollectionInParallelWithProgress.process(mergeConflictChanges, change -> {
			ResolutionDecision decision = conflictResolutionDeterminer.determineResolution(change,
					() -> safeOverwriteDeterminer.findUpstreamMatch(change.getPath(), upstreamRemoteName),
					() -> changeContentsEquivalence.oursTheirsChangeContentsAreEqual(change));
			synchronized (this) {
				resolveConflict(change, decision.getStrategy());
				entries.add(createJournalEntry(change, conflicts.get(change.getPath()), decision));
			}
			return decision.getStrategy() == ConflictResolutionStrategy.ACCEPT_THEIRS;
		});

		if (resolvedDiffCount > 0) {
			LOGGER.info("Resolved " + resolvedDiffCount + " conflict(s) by accepting the upstream change.");
		}

		entries.sort(Comparator.comparing(entry -> (String) entry.get(JournalEntry.FILE)));
		return new PassResult(entries, 0);
	}

	private static JournalEntry createJournalEntry(final Change change, final IndexDiff.StageState stageState,
			final ResolutionDecision decision) {
		final ResolutionRule rule = decision.getRule();
		final JournalEntry entry = JournalEntry.forFile(change.getPath(), "conflict")
				.with(JournalEntry.GIT_STAGE, gitStage(stageState))
				.with(JournalEntry.RULE, rule.getCode())
				.with(JournalEntry.ACTION, action(change, decision.getStrategy()))
				.with(JournalEntry.REGION, JournalEntry.WHOLE_FILE)
				.with(JournalEntry.REASON, rule.getReason())
				.with(JournalEntry.EVIDENCE, evidence(change, stageState, decision))
				.with(JournalEntry.REJECTED_ALTERNATIVES, rule.getRejectedAlternatives())
				.with(JournalEntry.NEEDS_REVIEW, rule == ResolutionRule.R_MANUAL);
		decision.getUpstreamMatch().ifPresent(match -> entry
				.with(JournalEntry.MATCHED_UPSTREAM_COMMIT, match.getCommitId())
				.with(JournalEntry.MATCHED_UPSTREAM_BLOB, match.getBlobId()));
		return entry;
	}

	private static String action(final Change change, final ConflictResolutionStrategy strategy) {
		switch (strategy) {
			case ACCEPT_OURS:
				return "kept ours";
			case ACCEPT_THEIRS:
				return acceptingTheirsDeletes(change) ? "deleted" : "took upstream";
			default:
				return "left for manual resolution";
		}
	}

	private static String evidence(final Change change, final IndexDiff.StageState stageState, final ResolutionDecision decision) {
		switch (decision.getRule()) {
			case R_DEL:
				return "Git stage " + gitStage(stageState) + ": the upstream side deleted the file.";
			case R_SAME:
				return "Local blob " + change.getOurVersionSHA() + " and upstream blob " + change.getTheirVersionSHA()
						+ " have identical contents.";
			case R_SAFE:
				return matchEvidence(decision.getUpstreamMatch().orElseThrow(IllegalStateException::new));
			default:
				return "No upstream commit of this file has the same blob as the latest local commit.";
		}
	}

	/**
	 * Map a JGit stage state to the two-letter code that {@code git status --short} shows.
	 *
	 * @param stageState the stage state
	 * @return the two-letter code
	 */
	static String gitStage(final IndexDiff.StageState stageState) {
		switch (stageState) {
			case BOTH_DELETED:
				return "DD";
			case ADDED_BY_US:
				return "AU";
			case DELETED_BY_THEM:
				return "UD";
			case ADDED_BY_THEM:
				return "UA";
			case DELETED_BY_US:
				return "DU";
			case BOTH_ADDED:
				return "AA";
			case BOTH_MODIFIED:
				return "UU";
			default:
				throw new IllegalArgumentException("Unexpected stage state " + stageState);
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
