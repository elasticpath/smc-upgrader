package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiffConflictResolverTest {
	private static final String UPSTREAM_REMOTE_NAME = "smc-upstream";
	private static final String VERSION = "8.6.x";
	private static final String PATH_1 = "bill-of-materials/pom.xml";
	private static final String CONTENT_HASH_1 = "contentHash1";
	private static final String SHA_1 = "a952b2e04ebc61a64eb7c7f542ee9b2de9888370";
	private static final String SHA_2 = "7418a139da6dc2fc7d50db7a341518c15f330c75";

	@Mock
	private GitClient gitClient;

	@Mock
	private RevCommit localCommit1;

	@Mock
	private RevCommit remoteCommit1;

	private AtomicInteger resolvedWithOurs;
	private AtomicInteger resolvedWithTheirs;

	private DiffConflictResolver diffConflictResolver;

	@BeforeEach
	void setUp() {
		diffConflictResolver = new DiffConflictResolver(gitClient) {
			@Override
			protected void resolveConflictOurs(final Change change) {
				resolvedWithOurs.getAndIncrement();
			}

			@Override
			protected void resolveConflictTheirs(final Change change) {
				resolvedWithTheirs.getAndIncrement();
			}
		};
		resolvedWithOurs = new AtomicInteger();
		resolvedWithTheirs = new AtomicInteger();
	}

	@Test
	void resolveDiffConflicts() {
		final Ref releaseBranch = mock(Ref.class);
		when(gitClient.getReleaseBranch(UPSTREAM_REMOTE_NAME, VERSION)).thenReturn(releaseBranch);

		DiffEntry diff = mock(DiffEntry.class);
		when(diff.getNewId()).thenReturn(AbbreviatedObjectId.fromString(SHA_1));
		when(diff.getOldId()).thenReturn(AbbreviatedObjectId.fromString(SHA_2));
		when(diff.getChangeType()).thenReturn(DiffEntry.ChangeType.MODIFY);
		when(diff.getOldPath()).thenReturn(PATH_1);
		when(gitClient.getDiff(releaseBranch)).thenReturn(Collections.singletonList(diff));

		when(gitClient.getLatestCommitForPath(PATH_1)).thenReturn(localCommit1);

		when(gitClient.getContentHashOfPathAtCommit(eq(PATH_1), same(localCommit1))).thenReturn(Optional.of(CONTENT_HASH_1));

		final List<RevCommit> authoritativeCommitsForPath2 = new ArrayList<>();
		authoritativeCommitsForPath2.add(remoteCommit1);
		when(gitClient.getAllCommitsForPathInAllBranches(PATH_1, UPSTREAM_REMOTE_NAME)).thenReturn(authoritativeCommitsForPath2);

		when(gitClient.getContentHashOfPathAtCommit(eq(PATH_1), same(remoteCommit1))).thenReturn(Optional.of(CONTENT_HASH_1)); // Match localCommit1

		diffConflictResolver.resolveDiffConflicts(UPSTREAM_REMOTE_NAME, VERSION);

		assertThat(resolvedWithOurs.get())
				.as("Conflicts resolved with ours doesn't match expectation")
				.isEqualTo(0);
		assertThat(resolvedWithTheirs.get())
				.as("Conflicts resolved with theirs doesn't match expectation")
				.isEqualTo(1);
	}
}