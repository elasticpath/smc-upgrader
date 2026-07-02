package com.elasticpath.tools.smcupgrader;

import java.util.Optional;
import java.util.stream.Collectors;

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

		final String commands = Constants.UPSTREAM_REPO_URLS.stream()
				.map(url -> "git remote add " + UPGRADE_REMOTE_NAME + " " + url)
				.collect(Collectors.joining("\n"));

		throw new LoggableException("No upstream repository found in git configuration."
				+ " Please add the remote via one of the following commands:\n\n" + commands);
	}

	private static boolean isUpstreamUrl(final String url) {
		if (url == null) {
			return false;
		}
		final String normalizedUrl = url.replaceFirst("://[^@]+@", "://");
		return Constants.UPSTREAM_REPO_URLS.contains(normalizedUrl);
	}
}
