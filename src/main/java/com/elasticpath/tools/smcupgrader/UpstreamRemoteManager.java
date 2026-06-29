package com.elasticpath.tools.smcupgrader;

import java.util.Optional;

/**
 * Manages the configuration for upstream remote repositories.
 */
public class UpstreamRemoteManager {
	private static final String UPGRADE_REMOTE_NAME = "smc-upgrades";

	private final GitClient gitClient;

	private String remoteRepositoryName;

	/**
	 * Constructor.
	 *
	 * @param gitClient the Git client
	 */
	public UpstreamRemoteManager(final GitClient gitClient) {
		this.gitClient = gitClient;
	}

	/**
	 * Returns the name of the upstream remote repository.
	 *
	 * @return the name of the upstream remote repository
	 */
	public String getUpstreamRemoteName() {
		if (remoteRepositoryName != null) {
			return remoteRepositoryName;
		}

		final Optional<String> existingRemoteRepositoryName = gitClient.getRemoteRepositories().stream()
				.filter(remoteRepository -> isUpstreamUrl(remoteRepository.getUrl()))
				.map(RemoteRepository::getName)
				.findFirst();

		if (existingRemoteRepositoryName.isPresent()) {
			remoteRepositoryName = existingRemoteRepositoryName.get();
			return remoteRepositoryName;
		}

		throw new LoggableException("No upstream repository found in git configuration. Please add the remote via the following command:\n\n"
				+ "git remote add " + UPGRADE_REMOTE_NAME + " " + Constants.UPSTREAM_REPO_URL);
	}

	private static boolean isUpstreamUrl(final String url) {
		return url != null
				&& url.contains(Constants.UPSTREAM_REPO_HOST)
				&& url.contains(Constants.UPSTREAM_REPO_PATH);
	}
}
