package com.elasticpath.tools.smcupgrader;

import java.util.Optional;

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
		return findUpstreamMatch(path, upstreamRemoteName).isPresent();
	}

	/**
	 * Find the first upstream commit of the specified path whose contents match the latest local commit of that path.
	 *
	 * @param path               the file to evaluate
	 * @param upstreamRemoteName the upstream remote name that contains commits that are considered authoritative
	 * @return the matching upstream commit and blob, or Optional.empty if the file appears to contain customizations
	 */
	public Optional<UpstreamMatch> findUpstreamMatch(final String path, final String upstreamRemoteName) {
		RevCommit latestCommitForPath = gitClient.getLatestCommitForPath(path);
		if (latestCommitForPath == null) {
			return Optional.empty();
		}
		Optional<String> latestBlobId = gitClient.getContentHashOfPathAtCommit(path, latestCommitForPath);
		if (!latestBlobId.isPresent()) {
			return Optional.empty();
		}

		for (RevCommit authoritativeCommit : gitClient.getAllCommitsForPathInAllBranches(path, upstreamRemoteName)) {
			Optional<String> authoritativeBlobId = gitClient.getContentHashOfPathAtCommit(path, authoritativeCommit);
			if (authoritativeBlobId.isPresent() && authoritativeBlobId.get().equals(latestBlobId.get())) {
				return Optional.of(new UpstreamMatch(authoritativeCommit.getName(), authoritativeBlobId.get()));
			}
		}
		return Optional.empty();
	}
}
