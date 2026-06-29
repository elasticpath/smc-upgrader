package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class UpstreamRemoteManagerTest {

	private static final String REMOTE_REPO_NAME = "upstream";
	private static final String NON_MATCHING_URL = "git@example.com/otherproject.git";

	@Mock
	private GitClient gitClient;

	private UpstreamRemoteManager upstreamRemoteManager;

	@BeforeEach
	public void setUp() {
		upstreamRemoteManager = new UpstreamRemoteManager(gitClient);
	}

	@Test
	public void verifyRemoteNameReturnedWhenSshUrl() {
		givenUpstreamRemote(Constants.UPSTREAM_REPO_URL);

		assertThat(upstreamRemoteManager.getUpstreamRemoteName()).isEqualTo(REMOTE_REPO_NAME);
	}

	@Test
	public void verifyRemoteNameReturnedWhenHttpsUrl() {
		givenUpstreamRemote("https://code.elasticpath.com/ep-commerce/ep-commerce.git");

		assertThat(upstreamRemoteManager.getUpstreamRemoteName()).isEqualTo(REMOTE_REPO_NAME);
	}

	@Test
	public void verifyRemoteNameReturnedWhenHttpsUrlWithToken() {
		givenUpstreamRemote("https://oauth2:glpat-XXXX@code.elasticpath.com/ep-commerce/ep-commerce.git");

		assertThat(upstreamRemoteManager.getUpstreamRemoteName()).isEqualTo(REMOTE_REPO_NAME);
	}

	@Test
	public void verifyExceptionThrownWhenSamePathButDifferentHost() {
		givenOnlyNonMatchingRemotes("https://other-host.com/ep-commerce/ep-commerce.git");

		assertThatThrownBy(() -> upstreamRemoteManager.getUpstreamRemoteName())
				.isInstanceOf(LoggableException.class);
	}

	@Test
	public void verifyExceptionThrownWhenNotFound() {
		givenOnlyNonMatchingRemotes(NON_MATCHING_URL);

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

	private void givenUpstreamRemote(final String upstreamUrl) {
		final RemoteRepository repoOther = new RemoteRepository("otherName", NON_MATCHING_URL);
		final RemoteRepository upstreamRepo = new RemoteRepository(REMOTE_REPO_NAME, upstreamUrl);

		when(gitClient.getRemoteRepositories())
				.thenReturn(Sets.newLinkedHashSet(repoOther, upstreamRepo));
	}

	private void givenOnlyNonMatchingRemotes(final String url) {
		when(gitClient.getRemoteRepositories())
				.thenReturn(Sets.newLinkedHashSet(new RemoteRepository("otherName", url)));
	}
}
