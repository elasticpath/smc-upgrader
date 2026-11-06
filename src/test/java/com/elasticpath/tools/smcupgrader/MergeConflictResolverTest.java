package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MergeConflictResolverTest {
	private static final String UPSTREAM_REMOTE_NAME = "smc-upstream";
	private static final String PATH_1 = "bill-of-materials/pom.xml";
	private static final String PATH_2 = "commerce-engine/batch/ep-batch-processing/pom.xml";
	private static final String PATH_3 = "extensions/maven/individual-settings.xml";
	private static final String CONTENT_HASH_1 = "contentHash1";
	private static final String CONTENT_HASH_2 = "contentHash2";
	private static final String CONTENT_HASH_3 = "contentHash3";

	@Mock
	private GitClient gitClient;

	@Mock
	private RevCommit localCommit1;

	@Mock
	private RevCommit remoteCommit1;

	@Mock
	private RevCommit localCommit2;

	@Mock
	private RevCommit remoteCommit2;

	private AtomicInteger resolvedWithOurs;
	private AtomicInteger resolvedWithTheirs;

	private MergeConflictResolver mergeConflictResolver;

	@BeforeEach
	void setUp() {
		mergeConflictResolver = new MergeConflictResolver(gitClient) {
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
	void resolveMergeConflicts() {
		final Map<String, IndexDiff.StageState> conflicts = new HashMap<>();
		conflicts.put(PATH_1, IndexDiff.StageState.BOTH_ADDED);
		conflicts.put(PATH_2, IndexDiff.StageState.BOTH_MODIFIED);
		conflicts.put(PATH_3, IndexDiff.StageState.BOTH_MODIFIED);
		when(gitClient.getConflicts()).thenReturn(conflicts);

		final Set<IndexEntry> indexStatusEntries = new HashSet<>();
		indexStatusEntries.add(new IndexEntry(PATH_1, IndexEntry.Stage.THEIRS.ordinal(), UUID.randomUUID().toString()));
		indexStatusEntries.add(new IndexEntry(PATH_2, IndexEntry.Stage.THEIRS.ordinal(), UUID.randomUUID().toString()));
		indexStatusEntries.add(new IndexEntry(PATH_3, IndexEntry.Stage.THEIRS.ordinal(), UUID.randomUUID().toString()));
		when(gitClient.getStatusIndexEntries()).thenReturn(indexStatusEntries);

		when(gitClient.getLatestCommitForPath(PATH_2)).thenReturn(localCommit1);
		when(gitClient.getLatestCommitForPath(PATH_3)).thenReturn(localCommit2);

		when(gitClient.getContentHashOfPathAtCommit(eq(PATH_2), same(localCommit1))).thenReturn(Optional.of(CONTENT_HASH_1));
		when(gitClient.getContentHashOfPathAtCommit(eq(PATH_3), same(localCommit2))).thenReturn(Optional.of(CONTENT_HASH_2));

		final List<RevCommit> authoritativeCommitsForPath2 = new ArrayList<>();
		authoritativeCommitsForPath2.add(remoteCommit1);
		when(gitClient.getAllCommitsForPathInAllBranches(PATH_2, UPSTREAM_REMOTE_NAME)).thenReturn(authoritativeCommitsForPath2);

		final List<RevCommit> authoritativeCommitsForPath3 = new ArrayList<>();
		authoritativeCommitsForPath3.add(remoteCommit2);
		when(gitClient.getAllCommitsForPathInAllBranches(PATH_3, UPSTREAM_REMOTE_NAME)).thenReturn(authoritativeCommitsForPath3);

		when(gitClient.getContentHashOfPathAtCommit(eq(PATH_2), same(remoteCommit1))).thenReturn(Optional.of(CONTENT_HASH_1)); // Match localCommit1
		when(gitClient.getContentHashOfPathAtCommit(eq(PATH_3), same(remoteCommit2))).thenReturn(Optional.of(CONTENT_HASH_3)); // Don't match localCommit2

		final PassResult result = mergeConflictResolver.resolveMergeConflicts(UPSTREAM_REMOTE_NAME);

		assertThat(resolvedWithOurs.get())
				.as("Conflicts resolved with ours doesn't match expectation")
				.isEqualTo(1);
		assertThat(resolvedWithTheirs.get())
				.as("Conflicts resolved with theirs doesn't match expectation")
				.isEqualTo(1);

		final List<JournalEntry> entries = result.getEntries();
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.FILE)).containsExactly(PATH_1, PATH_2, PATH_3);
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.PASS)).containsOnly("conflict");
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.RULE)).containsExactly("R-SAME", "R-SAFE", "R-MANUAL");
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.GIT_STAGE)).containsExactly("AA", "UU", "UU");
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.ACTION))
				.containsExactly("kept ours", "took upstream", "left for manual resolution");
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.NEEDS_REVIEW)).containsExactly(false, false, true);
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.EXTENSION_OR_CORE)).containsExactly("core", "core", "extension");
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.MATCHED_UPSTREAM_BLOB)).containsExactly(null, CONTENT_HASH_1, null);
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.REASON)).doesNotContainNull();
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.EVIDENCE)).doesNotContainNull();
		assertThat(entries).extracting(entry -> entry.get(JournalEntry.REJECTED_ALTERNATIVES)).doesNotContainNull();
		assertThat(result.getUnchangedCount()).isZero();
	}

	@Test
	void gitStageUsesTwoLetterStatusCodes() {
		assertThat(MergeConflictResolver.gitStage(IndexDiff.StageState.BOTH_DELETED)).isEqualTo("DD");
		assertThat(MergeConflictResolver.gitStage(IndexDiff.StageState.ADDED_BY_US)).isEqualTo("AU");
		assertThat(MergeConflictResolver.gitStage(IndexDiff.StageState.DELETED_BY_THEM)).isEqualTo("UD");
		assertThat(MergeConflictResolver.gitStage(IndexDiff.StageState.ADDED_BY_THEM)).isEqualTo("UA");
		assertThat(MergeConflictResolver.gitStage(IndexDiff.StageState.DELETED_BY_US)).isEqualTo("DU");
		assertThat(MergeConflictResolver.gitStage(IndexDiff.StageState.BOTH_ADDED)).isEqualTo("AA");
		assertThat(MergeConflictResolver.gitStage(IndexDiff.StageState.BOTH_MODIFIED)).isEqualTo("UU");
	}
}