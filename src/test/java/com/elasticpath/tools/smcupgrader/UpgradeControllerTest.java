package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for {@link UpgradeController}.
 */
@ExtendWith(MockitoExtension.class)
class UpgradeControllerTest {

	@Mock
	private GitClient gitClient;

	@TempDir
	File repoDir;

	private UpgradeController upgradeController;

	@BeforeEach
	public void setUp() {
		lenient().when(gitClient.getWorkingDir()).thenReturn(repoDir);
		upgradeController = new UpgradeController(gitClient);
	}

	@Test
	public void verifyVersionReadFromPomInWorkingDir() throws Exception {
		writePom(repoDir, "8.7.0");

		assertThat(upgradeController.determineCurrentVersion()).isEqualTo("8.7.0");
	}

	@Test
	public void verifyVersionReadFromReadmeWhenPomAbsent() throws Exception {
		writeReadme(repoDir, "Elastic Path Commerce 8.7.0 (build 8.7.0.20250730203654-29b4ea)");

		assertThat(upgradeController.determineCurrentVersion()).isEqualTo("8.7.0");
	}

	@Test
	public void verifyVersionReadFromReadmeWhenPomLacksVersionProperty() throws Exception {
		Files.write(repoDir.toPath().resolve("pom.xml"),
				"<project><properties></properties></project>".getBytes(StandardCharsets.UTF_8));
		writeReadme(repoDir, "Elastic Path Commerce 8.6.2 (build x)");

		assertThat(upgradeController.determineCurrentVersion()).isEqualTo("8.6.2");
	}

	@Test
	public void verifyExceptionThrownWhenNeitherFilePresent() {
		assertThatThrownBy(() -> upgradeController.determineCurrentVersion())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Could not determine current version");
	}

	@Test
	public void verifyConvertVersionToReleaseFormat() {
		assertThat(upgradeController.convertVersionToReleaseFormat("8.7.0")).isEqualTo("8.7.x");
		assertThat(upgradeController.convertVersionToReleaseFormat("8.6.2")).isEqualTo("8.6.x");
	}

	@Test
	public void verifyJournalRecordsCleanMergesAndBothPasses() throws Exception {
		writePom(repoDir, "8.6.0");
		final String remote = "smc-upgrades";
		final String conflicted = "extensions/core/Conflicted.java";
		final String diffPath = "commerce-engine/core/pom.xml";
		final String localBlob = "a952b2e04ebc61a64eb7c7f542ee9b2de9888370";
		final String upstreamBlob = "7418a139da6dc2fc7d50db7a341518c15f330c75";
		final RevWalk revWalk = new RevWalk((ObjectReader) null);
		final RevCommit localCommit = revWalk.lookupCommit(ObjectId.fromString("1111111111111111111111111111111111111111"));
		final RevCommit upstreamCommit = revWalk.lookupCommit(ObjectId.fromString("2222222222222222222222222222222222222222"));

		when(gitClient.getRemoteRepositories()).thenReturn(Collections.singleton(new RemoteRepository(remote, Constants.UPSTREAM_REPO_URLS.get(0))));
		final Ref releaseBranch = mock(Ref.class);
		when(gitClient.getReleaseBranch(remote, "8.7.x")).thenReturn(releaseBranch);
		when(gitClient.getHeadCommitId()).thenReturn("head");
		when(gitClient.getPathsChangedSince("head")).thenReturn(new HashSet<>(Arrays.asList(
				"a.java", "b.java", conflicted, Constants.JOURNAL_FILE_NAME)));
		when(gitClient.getConflicts()).thenReturn(Collections.singletonMap(conflicted, IndexDiff.StageState.BOTH_MODIFIED));
		when(gitClient.getStatusIndexEntries()).thenReturn(new HashSet<>(Arrays.asList(
				new IndexEntry(conflicted, IndexEntry.Stage.OURS.ordinal(), localBlob),
				new IndexEntry(conflicted, IndexEntry.Stage.THEIRS.ordinal(), upstreamBlob))));
		when(gitClient.getLatestCommitForPath(conflicted)).thenReturn(null);

		final DiffEntry diff = mock(DiffEntry.class);
		when(diff.getNewId()).thenReturn(AbbreviatedObjectId.fromString(upstreamBlob));
		when(diff.getOldId()).thenReturn(AbbreviatedObjectId.fromString(localBlob));
		when(diff.getChangeType()).thenReturn(DiffEntry.ChangeType.MODIFY);
		when(diff.getOldPath()).thenReturn(diffPath);
		when(gitClient.getDiff()).thenReturn(Collections.emptyList());
		when(gitClient.getDiff(releaseBranch)).thenReturn(Collections.singletonList(diff));
		when(gitClient.getLatestCommitForPath(diffPath)).thenReturn(localCommit);
		when(gitClient.getContentHashOfPathAtCommit(diffPath, localCommit)).thenReturn(Optional.of("blobX"));
		when(gitClient.getAllCommitsForPathInAllBranches(diffPath, remote)).thenReturn(Collections.singletonList(upstreamCommit));
		when(gitClient.getContentHashOfPathAtCommit(diffPath, upstreamCommit)).thenReturn(Optional.of("blobX"));
		when(gitClient.findUpstreamBranchContaining(upstreamCommit.getName(), remote)).thenReturn(Optional.of("release/8.6.x"));

		final UpgradeJournal journal = new UpgradeJournal(repoDir);
		journal.startRun("Git merge from 8.6.x to 8.7.x");
		upgradeController.performUpgrade("8.7.x", journal);

		final String content = journal.read();
		assertThat(content).contains("Git merged 2 file(s) from the 8.7.x release branch without a conflict.");
		assertThat(content).contains("The conflict pass handled 1 conflicted file(s).");
		assertThat(content).contains("file: \"" + conflicted + "\"\nstep: \"Git merge from 8.6.x to 8.7.x\"\npass: \"conflict\"\n"
				+ "git_stage: \"UU\"\nrule: \"R-MANUAL\"");
		assertThat(content).contains("The diff pass changed 1 file(s) that differed from the 8.7.x release branch. It left 0 file(s)");
		assertThat(content).contains("rule: \"D-REPLACE\"");
		assertThat(content).contains("matched_upstream_branch: \"release/8.6.x\"");
		assertThat(content).contains("raw_merge_blob: \"" + localBlob + "\"\nfinal_blob: \"" + upstreamBlob + "\"");
	}

	@Test
	public void verifyStandardModeWritesNoJournal() throws Exception {
		writePom(repoDir, "8.6.0");
		when(gitClient.getRemoteRepositories()).thenReturn(Collections.singleton(
				new RemoteRepository("smc-upgrades", Constants.UPSTREAM_REPO_URLS.get(0))));

		upgradeController.performUpgrade("8.7.x", false, false, false, false, true, false);

		verify(gitClient, never()).getHeadCommitId();
		verify(gitClient, never()).findUpstreamBranchContaining(anyString(), anyString());
		assertThat(new File(repoDir, Constants.JOURNAL_FILE_NAME)).doesNotExist();
	}

	private static void writePom(final File dir, final String releaseVersion) throws Exception {
		final String pom = "<project>"
				+ "<properties>"
				+ "<ep.release.version>" + releaseVersion + "</ep.release.version>"
				+ "</properties>"
				+ "</project>";
		Files.write(dir.toPath().resolve("pom.xml"), pom.getBytes(StandardCharsets.UTF_8));
	}

	private static void writeReadme(final File dir, final String firstLine) throws Exception {
		final Path readme = dir.toPath().resolve("README.txt");
		Files.write(readme, (firstLine + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
	}
}
