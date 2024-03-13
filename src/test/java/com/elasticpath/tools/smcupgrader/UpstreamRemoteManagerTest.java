package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.assertj.core.util.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for {@link UpstreamRemoteManager}.
 */
@ExtendWith(MockitoExtension.class)
public class UpstreamRemoteManagerTest {

	private static final String REMOTE_REPO_NAME = "upstream";

	private static final String REMOTE_REPO_URL = "git@example.com/project.git";

	private static final RemoteRepository REMOTE_REPOSITORY = new RemoteRepository(REMOTE_REPO_NAME, REMOTE_REPO_URL);

	@Mock
	private GitClient gitClient;

	private UpstreamRemoteManager upstreamRemoteManager;

	@BeforeEach
	public void setUp() {
		upstreamRemoteManager = spy(new UpstreamRemoteManager(gitClient, REMOTE_REPO_URL));
	}

	@Test
	public void verifyRemoteNameReturnedWhenFound() {
		final RemoteRepository repoOther = new RemoteRepository("otherName", "git@example.com/otherproject.git");

		when(gitClient.getRemoteRepositories())
				.thenReturn(Sets.newLinkedHashSet(repoOther, REMOTE_REPOSITORY));

		assertThat(upstreamRemoteManager.getUpstreamRemoteName()).isEqualTo(REMOTE_REPO_NAME);
	}

	@Test
	public void verifyRemoteCreatedWhenNotFound() {
		final RemoteRepository repoOther = new RemoteRepository("otherName", "git@example.com/otherproject.git");
		final String newRemoteRepoName = UUID.randomUUID().toString();

		when(gitClient.getRemoteRepositories())
				.thenReturn(Sets.newLinkedHashSet(repoOther));
		when(upstreamRemoteManager.createRemoteRepositoryName())
				.thenReturn(newRemoteRepoName);

		assertThat(upstreamRemoteManager.getUpstreamRemoteName())
				.isEqualTo(newRemoteRepoName);

		verify(gitClient).addUpstreamRemote(newRemoteRepoName, REMOTE_REPO_URL);
	}

	@Test
	public void verifyRemoteCreatedWhenRemotesListEmpty() {
		final RemoteRepository repoOther = new RemoteRepository("otherName", "git@example.com/otherproject.git");
		final String newRemoteRepoName = UUID.randomUUID().toString();

		when(gitClient.getRemoteRepositories())
				.thenReturn(Sets.newLinkedHashSet(repoOther));
		when(upstreamRemoteManager.createRemoteRepositoryName())
				.thenReturn(newRemoteRepoName);

		assertThat(upstreamRemoteManager.getUpstreamRemoteName())
				.isEqualTo(newRemoteRepoName);

		verify(gitClient).addUpstreamRemote(newRemoteRepoName, REMOTE_REPO_URL);
	}

}