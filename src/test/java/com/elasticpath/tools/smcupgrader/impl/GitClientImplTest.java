package com.elasticpath.tools.smcupgrader.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the {@link GitClientImpl} methods that the upgrade journal uses, against a real repository.
 */
class GitClientImplTest {
	private static final String REMOTE = "smc-upgrades";

	@TempDir
	File repoDir;

	private Git git;
	private GitClientImpl gitClient;

	@BeforeEach
	void setUp() throws GitAPIException {
		git = Git.init().setDirectory(repoDir).call();
		gitClient = new GitClientImpl(git.getRepository());
	}

	@Test
	void getPathsChangedSinceComparesCommitTreeWithIndex() throws Exception {
		writeFile("a.txt", "1");
		writeFile("b.txt", "1");
		final RevCommit first = commitAll("first");
		writeFile("a.txt", "2");
		writeFile("c.txt", "new");
		Files.delete(new File(repoDir, "b.txt").toPath());
		git.add().addFilepattern(".").call();
		git.rm().addFilepattern("b.txt").call();

		assertThat(gitClient.getHeadCommitId()).isEqualTo(first.getName());
		assertThat(gitClient.getPathsChangedSince(first.getName())).containsExactlyInAnyOrder("a.txt", "b.txt", "c.txt");
	}

	@Test
	void getPathsChangedSinceWorksDuringMergeWithConflicts() throws Exception {
		writeFile("conflict.txt", "base");
		writeFile("clean.txt", "base");
		commitAll("base");
		final String mainBranch = git.getRepository().getBranch();

		git.checkout().setCreateBranch(true).setName("upstream").call();
		writeFile("conflict.txt", "upstream");
		writeFile("clean.txt", "upstream");
		commitAll("upstream change");

		git.checkout().setName(mainBranch).call();
		writeFile("conflict.txt", "local");
		final RevCommit local = commitAll("local change");

		gitClient.merge(git.getRepository().findRef("upstream"));

		assertThat(gitClient.getConflicts()).containsOnlyKeys("conflict.txt");
		assertThat(gitClient.getPathsChangedSince(local.getName())).contains("clean.txt", "conflict.txt");
	}

	@Test
	void getUncommittedPathsIncludesStagedUnstagedAndUntrackedFiles() throws Exception {
		writeFile("staged.txt", "1");
		writeFile("modified.txt", "1");
		commitAll("first");
		writeFile("staged.txt", "2");
		git.add().addFilepattern("staged.txt").call();
		writeFile("modified.txt", "2");
		writeFile("untracked.txt", "new");

		assertThat(gitClient.getUncommittedPaths()).containsExactlyInAnyOrder("staged.txt", "modified.txt", "untracked.txt");
	}

	@Test
	void findUpstreamBranchContainingPrefersReleaseBranches() throws Exception {
		writeFile("a.txt", "1");
		final RevCommit base = commitAll("base");
		writeFile("a.txt", "2");
		final RevCommit later = commitAll("later");

		createRemoteBranch("main", later);
		createRemoteBranch("release/8.6.x", base);
		createRemoteBranch("release/8.7.x", later);

		assertThat(gitClient.findUpstreamBranchContaining(base.getName(), REMOTE)).contains("release/8.6.x");
		assertThat(gitClient.findUpstreamBranchContaining(later.getName(), REMOTE)).contains("release/8.7.x");
		assertThat(gitClient.findUpstreamBranchContaining(later.getName(), "other-remote")).isEmpty();
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
