package com.elasticpath.tools.smcupgrader.impl;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.IndexEntry;
import com.elasticpath.tools.smcupgrader.LoggableException;
import com.elasticpath.tools.smcupgrader.MergeException;
import com.elasticpath.tools.smcupgrader.RemoteRepository;

/**
 * Performs git operations.
 */
public class GitClientImpl implements GitClient {

	private final Repository repository;

	private static final String NO_COMMON_ANCESTOR = "Git merge failed. Usually this means that Git could not find a "
			+ "common ancestor commit between your branch and the Self Managed Commerce release branch.";

	/**
	 * Constructor.
	 *
	 * @param repository the repository
	 */
	public GitClientImpl(final Repository repository) {
		this.repository = repository;
	}

	@Override public File getWorkingDir() {
		return repository.getWorkTree();
	}

	@Override public Set<RemoteRepository> getRemoteRepositories() {
		final Config storedConfig = repository.getConfig();
		final Set<String> remotes = storedConfig.getSubsections("remote");

		return remotes.stream().map(remoteName -> new RemoteRepository(remoteName, storedConfig.getString("remote", remoteName, "url")))
				.collect(Collectors.toSet());
	}

	@Override public void addUpstreamRemote(final String name, final String url) {
		try (Git git = new Git(repository)) {
			final RemoteAddCommand remoteAddCommand = git.remoteAdd();
			remoteAddCommand.setName(name);
			remoteAddCommand.setUri(new URIish(url));
			remoteAddCommand.call();
		} catch (final URISyntaxException | GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public Ref getReleaseBranch(final String upstreamRemoteName, final String version) {
		final String branchName = "release/" + version;

		try (Git git = new Git(repository)) {
			final Optional<Ref> releaseBranch = git.branchList()
					.setListMode(ListBranchCommand.ListMode.REMOTE)
					.call().stream()
					.filter(ref -> ref.getName().equals("refs/remotes/" + upstreamRemoteName + "/" + branchName))
					.findAny();

			if (releaseBranch.isPresent()) {
				return releaseBranch.get();
			} else {
				throw new LoggableException("No release branch for version " + version + " found in remote repository.");
			}
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}

	}

	@Override public boolean workingDirectoryHasChanges() {
		try (Git git = new Git(repository)) {
			final Status gitStatus = git.status().call();
			return !gitStatus.isClean();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public void merge(final Ref toMerge) {
		try (Git git = new Git(repository)) {
			MergeResult result = git.merge()
					.include(toMerge)
					.setFastForward(MergeCommand.FastForwardMode.NO_FF)
					.call();

			if (result.getBase() == null) {
				cleanupUnmergedFiles();
				throw new MergeException(NO_COMMON_ANCESTOR);
			}

		} catch (final GitAPIException | RuntimeException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public Map<String, IndexDiff.StageState> getConflicts() {
		try (Git git = new Git(repository)) {
			final Status status = git.status().call();
			return status.getConflictingStageState();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public Set<IndexEntry> getStatusIndexEntries() {
		final Set<IndexEntry> entries = new HashSet<>();
		final DirCache index;
		try {
			index = repository.lockDirCache();
			try {
				for (int i = 0; i < index.getEntryCount(); i++) {
					final DirCacheEntry entry = index.getEntry(i);

					entries.add(new IndexEntry(entry.getPathString(), entry.getStage(), entry.getObjectId().getName()));
				}
			} finally {
				index.unlock();
			}
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		return entries;
	}

	@Override public Iterable<RevCommit> getAllCommitsForPathInAllBranches(final String path, final String upstreamRemoteName) {
		try (Git git = new Git(repository)) {
			// Get all branches that start with "refs/remotes/" + upstreamRemoteName + "/"
			String remoteBranchPrefix = "refs/remotes/" + upstreamRemoteName + "/";
			List<AnyObjectId> upstreamRemoteBranchHeads = git.branchList()
					.setListMode(ListBranchCommand.ListMode.REMOTE)
					.call()
					.stream()
					.filter(ref -> ref.getName().startsWith(remoteBranchPrefix))
					.map(ref -> (AnyObjectId) ref.getObjectId())
					.collect(Collectors.toList());

			if (upstreamRemoteBranchHeads.isEmpty()) {
				return Collections.emptyList(); // No matching branches
			}

			LogCommand logCommand = git.log()
					.addPath(path);
			for (AnyObjectId branchHead : upstreamRemoteBranchHeads) {
				logCommand.add(branchHead);
			}
			return logCommand.call();
		} catch (final GitAPIException | IncorrectObjectTypeException | MissingObjectException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public RevCommit getLatestCommitForPath(final String path) {
		try (Git git = new Git(repository)) {
			Iterable<RevCommit> commits = git.log()
					.addPath(path)
					.setMaxCount(1) // Only fetch the latest one
					.call();

			Iterator<RevCommit> iterator = commits.iterator();
			return iterator.hasNext() ? iterator.next() : null;
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Cleanup: git reset --hard.
	 */
	private void cleanupUnmergedFiles() throws IOException, GitAPIException {
			repository.writeMergeCommitMsg(null);
			repository.writeMergeHeads(null);
			Git.wrap(repository).reset().setMode(ResetCommand.ResetType.HARD).call();
	}

	@Override public List<DiffEntry> getDiff() {
		try (Git git = new Git(repository)) {
			return git.diff().call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public List<DiffEntry> getDiff(final Ref otherBranch) {
		try (Git git = new Git(repository)) {

			final AbstractTreeIterator newTreeParser = prepareTreeParser(otherBranch);

			return git.diff().setNewTree(newTreeParser).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public void stage(final String path) {
		try (Git git = new Git(repository)) {
			git.add().addFilepattern(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public void unstage(final String path) {
		try (Git git = new Git(repository)) {
			git.reset().addPath(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public void delete(final String path) {
		try (Git git = new Git(repository)) {
			git.rm().addFilepattern(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public void writeBlobContents(final String hexSha, final OutputStream outputStream) {
		final ObjectId objectId = ObjectId.fromString(hexSha);
		try {
			final ObjectLoader loader = repository.open(objectId);
			loader.copyTo(outputStream);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override public Optional<String> getContentHashOfPathAtCommit(final String path, final RevCommit commit) {
		try {
			RevTree tree = commit.getTree();

			try (TreeWalk treeWalk = new TreeWalk(repository)) {
				treeWalk.addTree(tree);
				treeWalk.setRecursive(true);
				treeWalk.setFilter(PathFilter.create(path));

				if (!treeWalk.next()) {
					throw new IOException("Path not found in commit: " + path);
				}

				ObjectId objectId = treeWalk.getObjectId(0);
				ObjectLoader loader = repository.open(objectId);

				MessageDigest digest = MessageDigest.getInstance("SHA-256");
				byte[] hashBytes = digest.digest(loader.getBytes());

				StringBuilder hexString = new StringBuilder();
				for (byte b : hashBytes) {
					hexString.append(String.format("%02x", b));
				}

				return Optional.of(hexString.toString());
			}
		} catch (final IOException | NoSuchAlgorithmException ex) {
			return Optional.empty();
		}
	}

	private AbstractTreeIterator prepareTreeParser(final Ref ref) {
		try (RevWalk walk = new RevWalk(repository)) {
			final RevCommit commit = walk.parseCommit(ref.getObjectId());
			final RevTree tree = walk.parseTree(commit.getTree().getId());

			final CanonicalTreeParser treeParser = new CanonicalTreeParser();
			try (ObjectReader reader = repository.newObjectReader()) {
				treeParser.reset(reader, tree.getId());
			}

			walk.dispose();

			return treeParser;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
}
