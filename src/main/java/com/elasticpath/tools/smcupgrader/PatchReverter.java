package com.elasticpath.tools.smcupgrader;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reverts patches for the current version.
 */
public class PatchReverter {
	/**
	 * The Logger for the application.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(PatchReverter.class);

	/**
	 * Pattern to match commit messages that contain version strings or "Initial commit".
	 * Matches strings like "8.6.0.20240628135836-3288d0" or "Initial commit".
	 */
	private static final Pattern UPGRADE_COMMIT_PATTERN = Pattern.compile("^(?:[0-9]+\\.[0-9]+\\.[0-9]+|Initial commit)");

	private final GitClient gitClient;

	/**
	 * Constructor.
	 *
	 * @param gitClient the git client
	 */
	public PatchReverter(final GitClient gitClient) {
		this.gitClient = gitClient;
	}

	/**
	 * Reverts patches for the current version.
	 *
	 * @param upstreamRemoteName the name of the upstream remote
	 * @param version            the current version
	 */
	public void revertPatches(final String upstreamRemoteName, final String version) {
		LOGGER.info("Reverting patches...");

		final List<RevCommit> localCommits = StreamSupport.stream(gitClient.getAllCommits().spliterator(), false)
				// Skip merge commits
				.filter(commit -> commit.getParentCount() == 1)
				.collect(Collectors.toList());

		final Ref releaseBranch = gitClient.getReleaseBranch(upstreamRemoteName, version);
		final Map<String, String> patchCommitContentHashes =
				StreamSupport.stream(gitClient.getAllCommitsForBranch(releaseBranch).spliterator(), false)
				// Skip upgrade commits
				.filter(commit -> {
					Matcher matcher = UPGRADE_COMMIT_PATTERN.matcher(commit.getShortMessage());
					return !matcher.find();
				})
				.collect(Collectors.toMap(gitClient::getContentHash, RevCommit::getShortMessage));

		long revertedCommitCount = ProcessCollectionInSerialWithProgress.process(localCommits, localCommit -> {
			final String localCommitContentHash = gitClient.getContentHash(localCommit);
			if (patchCommitContentHashes.containsKey(localCommitContentHash)) {
				LOGGER.info("Reverting commit '{}' (matches with remote commit '{}')", localCommit.getShortMessage(),
						patchCommitContentHashes.get(localCommitContentHash));
				try {
					gitClient.revert(localCommit);
					return true;
				} catch (RuntimeException ex) {
					LOGGER.warn("Failed to revert commit '{}': {}", localCommit.getShortMessage(), ex.getMessage());
				}
			}
			return false;
		});

		if (revertedCommitCount > 0) {
			LOGGER.info("Reverted {} patches.", revertedCommitCount);
		}
	}
}
