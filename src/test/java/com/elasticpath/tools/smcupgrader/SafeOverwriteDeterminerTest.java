package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for {@link SafeOverwriteDeterminer}.
 */
@ExtendWith(MockitoExtension.class)
class SafeOverwriteDeterminerTest {
	private static final String PATH = "commerce-engine/core/pom.xml";
	private static final String REMOTE = "smc-upgrades";

	private final RevWalk revWalk = new RevWalk((ObjectReader) null);
	private final RevCommit localCommit = commit("1111111111111111111111111111111111111111");
	private final RevCommit upstreamNewer = commit("2222222222222222222222222222222222222222");
	private final RevCommit upstreamOlder = commit("3333333333333333333333333333333333333333");

	@Mock
	private GitClient gitClient;

	private SafeOverwriteDeterminer determiner;

	@BeforeEach
	void setUp() {
		determiner = new SafeOverwriteDeterminer(gitClient);
	}

	@Test
	void returnsFirstUpstreamCommitWithSameBlob() {
		when(gitClient.getLatestCommitForPath(PATH)).thenReturn(localCommit);
		when(gitClient.getContentHashOfPathAtCommit(PATH, localCommit)).thenReturn(Optional.of("blobA"));
		when(gitClient.getAllCommitsForPathInAllBranches(PATH, REMOTE)).thenReturn(Arrays.asList(upstreamNewer, upstreamOlder));
		when(gitClient.getContentHashOfPathAtCommit(PATH, upstreamNewer)).thenReturn(Optional.of("blobB"));
		when(gitClient.getContentHashOfPathAtCommit(PATH, upstreamOlder)).thenReturn(Optional.of("blobA"));

		Optional<UpstreamMatch> match = determiner.findUpstreamMatch(PATH, REMOTE);

		assertThat(match).isPresent();
		assertThat(match.get().getCommitId()).isEqualTo(upstreamOlder.getName());
		assertThat(match.get().getBlobId()).isEqualTo("blobA");
		assertThat(determiner.pathIsSafeToOverwrite(PATH, REMOTE)).isTrue();
	}

	@Test
	void returnsEmptyWhenNoUpstreamCommitHasSameBlob() {
		when(gitClient.getLatestCommitForPath(PATH)).thenReturn(localCommit);
		when(gitClient.getContentHashOfPathAtCommit(PATH, localCommit)).thenReturn(Optional.of("blobA"));
		when(gitClient.getAllCommitsForPathInAllBranches(PATH, REMOTE)).thenReturn(Arrays.asList(upstreamNewer));
		when(gitClient.getContentHashOfPathAtCommit(PATH, upstreamNewer)).thenReturn(Optional.of("blobB"));

		assertThat(determiner.findUpstreamMatch(PATH, REMOTE)).isEmpty();
	}

	@Test
	void returnsEmptyWhenPathHasNoLocalCommit() {
		when(gitClient.getLatestCommitForPath(PATH)).thenReturn(null);

		assertThat(determiner.findUpstreamMatch(PATH, REMOTE)).isEmpty();
	}

	private RevCommit commit(final String id) {
		return revWalk.lookupCommit(ObjectId.fromString(id));
	}
}
