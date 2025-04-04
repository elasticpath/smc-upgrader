package com.elasticpath.tools.smcupgrader;

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

/**
 * Performs git operations.
 */
public class GitClient {

	private final Repository repository;

	private static final String NO_COMMON_ANCESTOR = "Git merge failed. Usually this means that Git could not find a "
			+ "common ancestor commit between your branch and the Self Managed Commerce release branch.";

	/**
	 * Constructor.
	 *
	 * @param repository the repository
	 */
	public GitClient(final Repository repository) {
		this.repository = repository;
	}

	public File getWorkingDir() {
		return repository.getWorkTree();
	}

	/**
	 * Returns a set of remote repositories configured on the git repository.
	 *
	 * @return a set of remote repositories configured on the git repository
	 */
	public Set<RemoteRepository> getRemoteRepositories() {
		final Config storedConfig = repository.getConfig();
		final Set<String> remotes = storedConfig.getSubsections("remote");

		return remotes.stream().map(remoteName -> new RemoteRepository(remoteName, storedConfig.getString("remote", remoteName, "url")))
				.collect(Collectors.toSet());
	}

	/**
	 * Adds a remote repository to the git config.
	 *
	 * @param name the name of the remote
	 * @param url  the URL of the remote
	 */
	public void addUpstreamRemote(final String name, final String url) {
		try (Git git = new Git(repository)) {
			final RemoteAddCommand remoteAddCommand = git.remoteAdd();
			remoteAddCommand.setName(name);
			remoteAddCommand.setUri(new URIish(url));
			remoteAddCommand.call();
		} catch (final URISyntaxException | GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the branch containing the release code corresponding to the given version.
	 *
	 * @param upstreamRemoteName the name of the upstream remote
	 * @param version            the version
	 * @return a {@link Ref} representing the release branch
	 */
	public Ref getReleaseBranch(final String upstreamRemoteName, final String version) {
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

	/**
	 * Determines whether the working directory is clean or contains uncommitted changes.
	 *
	 * @return true if the working directory contains uncommitted changes
	 */
	public boolean workingDirectoryHasChanges() {
		try (Git git = new Git(repository)) {
			final Status gitStatus = git.status().call();
			return !gitStatus.isClean();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Performs a merge operation for the given ref.
	 *
	 * @param toMerge the {@link Ref} to merge into the current working branch.
	 */
	public void merge(final Ref toMerge) {
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

	/**
	 * Returns merge conflicts that exist in the local working directory.
	 *
	 * @return a map of conflicting file names to their {@link IndexDiff.StageState StageState}.
	 */
	public Map<String, IndexDiff.StageState> getConflicts() {
		try (Git git = new Git(repository)) {
			final Status status = git.status().call();
			return status.getConflictingStageState();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the set of {@link IndexEntry} instances representing every index entry in the git database.
	 *
	 * @return the set of {@link IndexEntry} instances representing every index entry in the git database
	 */
	public Set<IndexEntry> getStatusIndexEntries() {
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

	/**
	 * Returns an iterable of commits containing the specified path in any branch with a name starting with upstreamRemoteName.
	 *
	 * @param path a path that the commit must contain
	 * @param upstreamRemoteName the branch name prefix
	 * @return an iterable of commits
	 */
	public Iterable<RevCommit> getAllCommitsForPathInAllBranches(final String path, final String upstreamRemoteName) {
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

	/**
	 * Returns an iterable of commits in the current branch that contain the specified path.
	 *
	 * @param path a path that the commit must contain
	 * @return an iterable of commits
	 */
	public Iterable<RevCommit> getAllCommitsForPath(final String path) {
		try (Git git = new Git(repository)) {
			return git.log()
					.addPath(path)
					.call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the latest commit in the current branch that contains the specified path.
	 *
	 * @param path a path that the commit must contain
	 * @return a commit
	 */
	public RevCommit getLatestCommitForPath(final String path) {
		Iterator<RevCommit> iterator = getAllCommitsForPath(path).iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		} else {
			return null;
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

	/**
	 * Returns the results of a diff of all unstaged changes in the working directory.
	 *
	 * @return a list of {@link DiffEntry} instances
	 */
	public List<DiffEntry> getDiff() {
		try (Git git = new Git(repository)) {
			return git.diff().call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns the results of a diff between the local working directory and the given {@link Ref}.
	 *
	 * @param otherBranch the branch to diff
	 * @return a list of {@link DiffEntry} instances
	 */
	public List<DiffEntry> getDiff(final Ref otherBranch) {
		try (Git git = new Git(repository)) {

			final AbstractTreeIterator newTreeParser = prepareTreeParser(otherBranch);

			return git.diff().setNewTree(newTreeParser).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Adds a file to the git index.
	 *
	 * @param path the path of the file to add to the git index
	 */
	public void stage(final String path) {
		try (Git git = new Git(repository)) {
			git.add().addFilepattern(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Removes a file from the git index.
	 *
	 * @param path the path of the file to remove from the git index
	 */
	public void unstage(final String path) {
		try (Git git = new Git(repository)) {
			git.reset().addPath(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Deletes a file from the local file system and git repository.
	 *
	 * @param path the path of the file to delete
	 */
	public void delete(final String path) {
		try (Git git = new Git(repository)) {
			git.rm().addFilepattern(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Writes the contents of the object at the given SHA to the given output stream.
	 *
	 * @param hexSha       the SHA referencing an object in the git database
	 * @param outputStream the output stream to which the contents should be written
	 */
	public void writeBlobContents(final String hexSha, final OutputStream outputStream) {
		final ObjectId objectId = ObjectId.fromString(hexSha);
		try {
			final ObjectLoader loader = repository.open(objectId);
			loader.copyTo(outputStream);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns a SHA-256 hash of the contents of the path at the specified commit.
	 *
	 * @param path the path of a file
	 * @param commit a commit
	 * @return an optional hash of the file contents, or Optional.empty if we were unable to generate the content hash
	 */
	public Optional<String> getContentHashOfPathAtCommit(final String path, final RevCommit commit) {
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
