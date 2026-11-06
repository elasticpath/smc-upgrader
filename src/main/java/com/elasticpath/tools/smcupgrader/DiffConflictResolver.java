package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.util.ArrayList;
import java.util.Comparator;
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
	 * @return one journal entry for each file the pass changed, sorted by path, and the number of files left unchanged
	 */
	public PassResult resolveDiffConflicts(final String upstreamRemoteName, final String version) {
		final Ref releaseBranch = getGitClient().getReleaseBranch(upstreamRemoteName, version);
		final List<DiffEntry> diff = getGitClient().getDiff(releaseBranch);
		final List<Change> diffConflictChanges = changeFactory.createChanges(diff);
		LOGGER.info(diffConflictChanges.size() + " non-conflict file(s) found that differ from upstream " + version + " branch.");

		if (!diffConflictChanges.isEmpty()) {
			LOGGER.info("Processing diffs to attempt automatic resolution...");
		}

		final List<JournalEntry> entries = new ArrayList<>();

		long resolvedDiffCount = ProcessCollectionInParallelWithProgress.process(diffConflictChanges, change -> {
			ResolutionDecision decision = diffResolutionDeterminer.determineResolution(change,
					() -> safeOverwriteDeterminer.findUpstreamMatch(change.getPath(), upstreamRemoteName)
			);
			synchronized (this) {
				resolveConflict(change, decision.getStrategy());
				if (decision.getRule() != null) {
					entries.add(createJournalEntry(change, decision));
				}
			}
			return decision.getStrategy() == ConflictResolutionStrategy.ACCEPT_THEIRS;
		});

		if (resolvedDiffCount > 0) {
			LOGGER.info("Resolved {} diff(s) by accepting the upstream change.", resolvedDiffCount);
		}

		entries.sort(Comparator.comparing(entry -> (String) entry.get(JournalEntry.FILE)));
		return new PassResult(entries, diffConflictChanges.size() - entries.size());
	}

	private static JournalEntry createJournalEntry(final Change change, final ResolutionDecision decision) {
		final ResolutionRule rule = decision.getRule();
		final UpstreamMatch match = decision.getUpstreamMatch().orElseThrow(IllegalStateException::new);
		final boolean deletes = acceptingTheirsDeletes(change);
		final String action;
		if (deletes) {
			action = "deleted";
		} else if (rule == ResolutionRule.D_READD) {
			action = "restored";
		} else {
			action = "took upstream";
		}
		return JournalEntry.forFile(change.getPath(), "diff")
				.with(JournalEntry.RULE, rule.getCode())
				.with(JournalEntry.ACTION, action)
				.with(JournalEntry.REGION, JournalEntry.WHOLE_FILE)
				.with(JournalEntry.MATCHED_UPSTREAM_COMMIT, match.getCommitId())
				.with(JournalEntry.MATCHED_UPSTREAM_BLOB, match.getBlobId())
				.with(JournalEntry.RAW_MERGE_BLOB, change.getOurVersionSHA() == null ? "missing" : change.getOurVersionSHA())
				.with(JournalEntry.FINAL_BLOB, deletes ? "deleted" : change.getTheirVersionSHA())
				.with(JournalEntry.REASON, rule.getReason())
				.with(JournalEntry.EVIDENCE, matchEvidence(match))
				.with(JournalEntry.REJECTED_ALTERNATIVES, rule.getRejectedAlternatives())
				.with(JournalEntry.NEEDS_REVIEW, false);
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
