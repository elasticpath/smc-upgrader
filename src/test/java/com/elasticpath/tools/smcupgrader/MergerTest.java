package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.impl.GitClientImpl;

/**
 * Tests for {@link Merger}, against a real repository.
 */
class MergerTest {
	private static final String REMOTE = "smc-upgrades";
	private static final String VERSION = "8.6.x";
	private static final String BASE_LINES = "1\n2\n3\n4\n5\n6\n7\n8\n";

	@TempDir
	File repoDir;

	private Git git;
	private Merger merger;

	@BeforeEach
	void setUp() throws GitAPIException {
		git = Git.init().setDirectory(repoDir).call();
		merger = new Merger(new GitClientImpl(git.getRepository()));
	}

	@Test
	void mergeRecordsBulletForEachChangedFile() throws Exception {
		writeFile("theirs.txt", "base");
		writeFile("combined.txt", BASE_LINES);
		writeFile("conflict.txt", "base");
		writeFile("deleted.txt", "base");
		writeFile("local-only.txt", "base");
		writeFile(Constants.JOURNAL_FILE_NAME, "base");
		commitAll("base");
		final String mainBranch = git.getRepository().getBranch();

		git.checkout().setCreateBranch(true).setName("upstream").call();
		writeFile("theirs.txt", "upstream");
		writeFile("combined.txt", BASE_LINES.replace("8\n", "8 upstream\n"));
		writeFile("conflict.txt", "upstream");
		writeFile("added.txt", "upstream");
		writeFile(Constants.JOURNAL_FILE_NAME, "upstream");
		Files.delete(new File(repoDir, "deleted.txt").toPath());
		git.rm().addFilepattern("deleted.txt").call();
		final RevCommit upstream = commitAll("upstream");
		createRemoteBranch("release/" + VERSION, upstream);

		git.checkout().setName(mainBranch).call();
		writeFile("combined.txt", BASE_LINES.replace("1\n", "1 local\n"));
		writeFile("conflict.txt", "local");
		writeFile("local-only.txt", "local");
		writeFile(Constants.JOURNAL_FILE_NAME, "local");
		commitAll("local");

		final UpgradeReport report = new UpgradeReport();
		merger.merge(false, REMOTE, VERSION, report);

		assertThat(report.getTargetBranch()).isEqualTo(REMOTE + "/release/" + VERSION);
		assertThat(report.getTargetCommitId()).isEqualTo(upstream.getName());
		assertThat(report.getCleanMergeCount()).isEqualTo(4);
		assertThat(report.getConflictCount()).isEqualTo(1);
		assertThat(report.getFileChanges()).containsExactly(
				"`added.txt`: added from theirs because the target release added it.",
				"`combined.txt`: Git combined local and upstream changes without a conflict.",
				"`conflict.txt`: conflict markers added because local and upstream changes overlap.",
				"`deleted.txt`: deleted because the target release deleted it and it had no local changes.",
				"`theirs.txt`: replaced with theirs because it had no local changes.");
	}

	@Test
	void explainConflictCoversDeleteConflicts() {
		assertThat(Merger.explainConflict(IndexDiff.StageState.DELETED_BY_THEM))
				.isEqualTo("left in conflict because one side deleted it and the other side changed it.");
		assertThat(Merger.explainConflict(IndexDiff.StageState.ADDED_BY_US)).isEqualTo("left in conflict (added by us).");
		assertThat(Merger.explainCleanMerge(DiffEntry.ChangeType.MODIFY, false))
				.isEqualTo("replaced with theirs because it had no local changes.");
	}

	private void createRemoteBranch(final String name, final RevCommit commit) throws IOException {
		final RefUpdate update = git.getRepository().updateRef("refs/remotes/" + REMOTE + "/" + name);
		update.setNewObjectId(commit);
		update.forceUpdate();
	}

	private RevCommit commitAll(final String message) throws GitAPIException {
		git.add().addFilepattern(".").call();
		return git.commit().setMessage(message).setSign(false).setAuthor("Test", "test@example.com")
				.setCommitter("Test", "test@example.com").call();
	}

	private void writeFile(final String path, final String content) throws IOException {
		Files.write(new File(repoDir, path).toPath(), content.getBytes(StandardCharsets.UTF_8));
	}
}
