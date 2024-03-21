package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.assertj.core.util.Sets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
	public void verifyExceptionThrownWhenNotFound() {
		final RemoteRepository repoOther = new RemoteRepository("otherName", "git@example.com/otherproject.git");

		when(gitClient.getRemoteRepositories())
				.thenReturn(Sets.newLinkedHashSet(repoOther));

		assertThatThrownBy(() -> upstreamRemoteManager.getUpstreamRemoteName())
				.isInstanceOf(LoggableException.class);


	}

	@Test
	public void verifyExceptionThrownWhenRemotesListEmpty() {
		when(gitClient.getRemoteRepositories())
				.thenReturn(Collections.emptySet());

		assertThatThrownBy(() -> upstreamRemoteManager.getUpstreamRemoteName())
				.isInstanceOf(LoggableException.class);
	}

}