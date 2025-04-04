package com.elasticpath.tools.smcupgrader;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.revwalk.RevCommit;

/**
 * This class provides the ability to determine if a path is safe to overwrite. That is, that there are no customizations to the latest commit
 * at the specified path by the project team. As long as the contents of the file matches the contents of any commit of that file from
 * the upstream remote, we consider it "not customized".
 */
public class SafeOverwriteDeterminer {
	private final GitClient gitClient;

	/**
	 * Constructor.
	 *
	 * @param gitClient the git client
	 */
	public SafeOverwriteDeterminer(final GitClient gitClient) {
		this.gitClient = gitClient;
	}

	/**
	 * This method determines if the latest commit at the specified path matches the contents of any commit of that file from
	 * the upstream remote. Meaning that the file does not appear to contain customizations.
	 *
	 * @param path               the file to evaluate
	 * @param upstreamRemoteName the upstream remote name that contains commits that are considered authoritative
	 * @return true if the file at path has does not appear to contain customizations
	 */
	public boolean pathIsSafeToOverwrite(final String path, final String upstreamRemoteName) {
		RevCommit latestCommitForPath = gitClient.getLatestCommitForPath(path);
		if (latestCommitForPath == null) {
			return false;
		}
		Optional<String> latestCommitContentHash = gitClient.getContentHashOfPathAtCommit(path, latestCommitForPath);
		if (!latestCommitContentHash.isPresent()) {
			return false;
		}
		Iterable<RevCommit> authoritativeCommits = gitClient.getAllCommitsForPathInAllBranches(path, upstreamRemoteName);
		Set<String> authoritativeContentHashes = StreamSupport.stream(authoritativeCommits.spliterator(), false)
				.map(revCommit -> gitClient.getContentHashOfPathAtCommit(path, revCommit))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toSet());
		return authoritativeContentHashes.contains(latestCommitContentHash.get());
	}

}
