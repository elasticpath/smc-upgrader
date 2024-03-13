package com.elasticpath.tools.smcupgrader;

import java.util.Optional;

/**
 * Manages the configuration for upstream remote repositories.
 */
public class UpstreamRemoteManager {
	private static final String UPGRADE_REMOTE_NAME = "smc-upgrades";

	private final GitClient gitClient;

	private final String upstreamRemoteRepositoryUrl;

	private String remoteRepositoryName;

	/**
	 * Constructor.
	 *
	 * @param gitClient                   the Git client
	 * @param upstreamRemoteRepositoryUrl the URL of the upstream remote repository containing upgrade commits
	 */
	public UpstreamRemoteManager(final GitClient gitClient, final String upstreamRemoteRepositoryUrl) {
		this.gitClient = gitClient;
		this.upstreamRemoteRepositoryUrl = upstreamRemoteRepositoryUrl;
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
				.filter(remoteRepository -> remoteRepository.getUrl().contains(upstreamRemoteRepositoryUrl))
				.map(RemoteRepository::getName)
				.findFirst();

		if (existingRemoteRepositoryName.isPresent()) {
			remoteRepositoryName = existingRemoteRepositoryName.get();
		} else {
			remoteRepositoryName = createRemoteRepositoryName();

			gitClient.addUpstreamRemote(remoteRepositoryName, upstreamRemoteRepositoryUrl);
		}

		return remoteRepositoryName;
	}

	/**
	 * Returns the name to use for the upstream remote containing upgrade commits when that remote has not already been set on the working
	 * Git repository.
	 *
	 * @return the name to use for the upstream remote containing upgrade commits
	 */
	protected String createRemoteRepositoryName() {
		return UPGRADE_REMOTE_NAME;
	}
}
