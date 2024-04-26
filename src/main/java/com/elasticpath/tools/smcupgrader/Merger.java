package com.elasticpath.tools.smcupgrader;


import java.util.List;

import org.eclipse.jgit.diff.DiffEntry;
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
	 * @param upstreamRemoteName   the name of the upstream remote
	 * @param upgradeTargetVersion the target version to upgrade to
	 */
	public void merge(final String upstreamRemoteName, final String upgradeTargetVersion) {
		if (gitClient.workingDirectoryHasChanges()) {
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

		gitClient.merge(releaseBranch);

		// JGit will not stage files changed during a merge when the only modification was to the file mode.
		final List<DiffEntry> diff = gitClient.getDiff();
		changeFactory.createChanges(diff).stream()
				.filter(change -> !change.isConflict())
				.map(Change::getPath)
				.forEach(gitClient::stage);
	}
}
