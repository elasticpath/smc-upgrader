package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Ref;

/**
 * Manages the merge operation aspect of the upgrade.
 */
public class Merger {
	private final GitClient gitClient;

	private final ChangeFactory changeFactory;

	/**
	 * Constructor.
	 *
	 * @param gitClient the git client
	 */
	public Merger(final GitClient gitClient) {
		this.gitClient = gitClient;
		changeFactory = new ChangeFactory();
	}

	/**
	 * Performs a merge of the branch containing the upgraded codebase.
	 *
	 * @param doCleanWorkingDirectoryCheck check if the working directory is clean
	 * @param upstreamRemoteName   the name of the upstream remote
	 * @param upgradeTargetVersion the target version to upgrade to
	 * @param report               receives the target branch, the counts, and a bullet for each file the merge changed
	 */
	public void merge(final boolean doCleanWorkingDirectoryCheck, final String upstreamRemoteName, final String upgradeTargetVersion,
			final UpgradeReport report) {
		if (doCleanWorkingDirectoryCheck && gitClient.workingDirectoryHasChanges()) {
			throw new LoggableException(
					"The working directory for Git repository "
							+ gitClient.getWorkingDir().getAbsolutePath()
							+ " currently has changes.\n"
							+ "Please ensure the working directory is clean before re-running this tool.\n"
							+ "For example, to keep current changes:\n\n"
							+ "git add -A .\n"
							+ "git commit\n\n"
							+ "To remove current changes and restore the working directory to the latest commit:\n\n"
							+ "git reset --hard HEAD\n"
							+ "git clean -f -d");
		}

		final Ref releaseBranch = gitClient.getReleaseBranch(upstreamRemoteName, upgradeTargetVersion);

		report.setTargetBranch(shortBranchName(releaseBranch.getName()));
		if (releaseBranch.getObjectId() != null) {
			report.setTargetCommitId(releaseBranch.getObjectId().getName());
		}

		LOGGER.info("Upgrading current working branch to version {}...", upgradeTargetVersion);

		// JGit commits the merge when there are no conflicts, so record HEAD first to find the changed paths afterwards.
		final String headBeforeMerge = gitClient.getHeadCommitId();

		gitClient.merge(releaseBranch);

		// JGit will not stage files changed during a merge when the only modification was to the file mode.
		final List<DiffEntry> diff = gitClient.getDiff();
		changeFactory.createChanges(diff).stream()
				.filter(change -> !change.isConflict())
				.map(Change::getPath)
				.forEach(gitClient::stage);

		if (headBeforeMerge != null) {
			recordMergeChanges(headBeforeMerge, releaseBranch, report);
		}
	}

	private void recordMergeChanges(final String headBeforeMerge, final Ref releaseBranch, final UpgradeReport report) {
		final Map<String, IndexDiff.StageState> conflicts = gitClient.getConflicts();
		final Set<String> pathsDifferentFromRelease = new HashSet<>();
		for (DiffEntry entry : gitClient.getDiff(releaseBranch)) {
			pathsDifferentFromRelease.add(entry.getOldPath());
			pathsDifferentFromRelease.add(entry.getNewPath());
		}

		final Map<String, String> cleanChanges = new TreeMap<>();
		for (DiffEntry entry : gitClient.getChangesSince(headBeforeMerge)) {
			final String path = entry.getChangeType() == DiffEntry.ChangeType.ADD ? entry.getNewPath() : entry.getOldPath();
			if (!conflicts.containsKey(path) && !Constants.isUpgraderFile(path)) {
				cleanChanges.put(path, explainCleanMerge(entry.getChangeType(), pathsDifferentFromRelease.contains(path)));
			}
		}

		final Map<String, String> conflictChanges = new TreeMap<>();
		conflicts.forEach((path, stageState) -> {
			if (!Constants.isUpgraderFile(path)) {
				conflictChanges.put(path, explainConflict(stageState));
			}
		});

		final Map<String, String> allChanges = new TreeMap<>(cleanChanges);
		allChanges.putAll(conflictChanges);

		report.setCleanMergeCount(cleanChanges.size());
		report.setConflictCount(conflictChanges.size());
		report.addFileChanges(allChanges.entrySet().stream()
				.map(change -> UpgradeReport.fileChange(change.getKey(), change.getValue()))
				.collect(Collectors.toList()));
	}

	/**
	 * Explains a file that Git merged without a conflict.
	 *
	 * @param changeType             how the merge changed the file
	 * @param differentFromRelease   true if the merged file differs from the release branch version
	 * @return the explanation
	 */
	static String explainCleanMerge(final DiffEntry.ChangeType changeType, final boolean differentFromRelease) {
		if (changeType == DiffEntry.ChangeType.ADD) {
			return "added from theirs because the target release added it.";
		}
		if (changeType == DiffEntry.ChangeType.DELETE) {
			return "deleted because the target release deleted it and it had no local changes.";
		}
		if (differentFromRelease) {
			return "Git combined local and upstream changes without a conflict.";
		}
		return "replaced with theirs because it had no local changes.";
	}

	/**
	 * Explains a file that Git left in conflict.
	 *
	 * @param stageState the conflict type
	 * @return the explanation
	 */
	static String explainConflict(final IndexDiff.StageState stageState) {
		switch (stageState) {
			case BOTH_MODIFIED:
			case BOTH_ADDED:
				return "conflict markers added because local and upstream changes overlap.";
			case DELETED_BY_US:
			case DELETED_BY_THEM:
				return "left in conflict because one side deleted it and the other side changed it.";
			default:
				return "left in conflict (" + stageState.name().toLowerCase(Locale.ROOT).replace('_', ' ') + ").";
		}
	}

	private static String shortBranchName(final String refName) {
		final String remotePrefix = "refs/remotes/";
		return refName != null && refName.startsWith(remotePrefix) ? refName.substring(remotePrefix.length()) : refName;
	}
}
