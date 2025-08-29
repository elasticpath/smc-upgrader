package com.elasticpath.tools.smcupgrader.impl;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
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
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
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

	@Override
	public File getWorkingDir() {
		return repository.getWorkTree();
	}

	@Override
	public Set<RemoteRepository> getRemoteRepositories() {
		final Config storedConfig = repository.getConfig();
		final Set<String> remotes = storedConfig.getSubsections("remote");

		return remotes.stream().map(remoteName -> new RemoteRepository(remoteName, storedConfig.getString("remote", remoteName, "url")))
				.collect(Collectors.toSet());
	}

	@Override
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

	@Override
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

	@Override
	public boolean workingDirectoryHasChanges() {
		try (Git git = new Git(repository)) {
			final Status gitStatus = git.status().call();
			return !gitStatus.isClean();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
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

	@Override
	public void revert(final AnyObjectId toRevert) {
		try (Git git = new Git(repository)) {
			final RevertCommand revertCommand = git.revert()
					.include(toRevert);
			revertCommand.call();
			if (revertCommand.getFailingResult() != null) {
				try {
					cleanupUnmergedFiles();
				} catch (IOException | GitAPIException ex) {
					// Ignore
				}
				throw new RuntimeException(revertCommand.getFailingResult().toString());
			}
		} catch (final GitAPIException e) {
			try {
				cleanupUnmergedFiles();
			} catch (IOException | GitAPIException ex) {
				// Ignore
			}
			throw new RuntimeException(e);
		}
	}

	@Override
	public Map<String, IndexDiff.StageState> getConflicts() {
		try (Git git = new Git(repository)) {
			final Status status = git.status().call();
			return status.getConflictingStageState();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
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

	@Override
	public Iterable<RevCommit> getAllCommits() {
		try (Git git = new Git(repository)) {
			return git.log()
					.call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Iterable<RevCommit> getAllCommitsForBranch(final Ref branch) {
		try (Git git = new Git(repository)) {
			return git.log()
					.add(branch.getObjectId())
					.call();
		} catch (final GitAPIException | IncorrectObjectTypeException | MissingObjectException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
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

	@Override
	public RevCommit getLatestCommitForPath(final String path) {
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

	@Override
	public List<DiffEntry> getDiff() {
		try (Git git = new Git(repository)) {
			return git.diff().call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public List<DiffEntry> getDiff(final Ref otherBranch) {
		try (Git git = new Git(repository)) {

			final AbstractTreeIterator newTreeParser = prepareTreeParser(otherBranch);

			return git.diff().setNewTree(newTreeParser).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void stage(final String path) {
		try (Git git = new Git(repository)) {
			git.add().addFilepattern(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void unstage(final String path) {
		try (Git git = new Git(repository)) {
			git.reset().addPath(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void delete(final String path) {
		try (Git git = new Git(repository)) {
			git.rm().addFilepattern(path).call();
		} catch (final GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void writeBlobContents(final String hexSha, final OutputStream outputStream) {
		final ObjectId objectId = ObjectId.fromString(hexSha);
		try {
			final ObjectLoader loader = repository.open(objectId);
			loader.copyTo(outputStream);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
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
				return Optional.of(objectId.name());
			}
		} catch (final IOException ex) {
			return Optional.empty();
		}
	}

	@Override
	public String getContentHash(final RevCommit commit) {
		try (Git git = new Git(repository)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			// For the initial commit or if something goes wrong with getting parent, fall back to hashing the entire tree
			if (commit.getParentCount() == 0) {
				try (TreeWalk treeWalk = new TreeWalk(repository)) {
					treeWalk.addTree(commit.getTree());
					treeWalk.setRecursive(true);

					while (treeWalk.next()) {
						String path = treeWalk.getPathString();
						ObjectId objectId = treeWalk.getObjectId(0);

						digest.update(path.getBytes(StandardCharsets.UTF_8));
						digest.update(objectId.name().getBytes(StandardCharsets.UTF_8));
					}
				}
			} else {
				// For normal commits, hash the diff between this commit and its parent
				RevCommit parent = commit.getParent(0);
				List<DiffEntry> diffs = git.diff()
						.setOldTree(prepareTreeParser(parent))
						.setNewTree(prepareTreeParser(commit))
						.call();

				for (DiffEntry diff : diffs) {
					String changeType = diff.getChangeType().name();
					String oldPath = diff.getOldPath();
					String newPath = diff.getNewPath();

					digest.update(changeType.getBytes(StandardCharsets.UTF_8));
					if (oldPath != null) {
						digest.update(oldPath.getBytes(StandardCharsets.UTF_8));
					}
					if (newPath != null) {
						digest.update(newPath.getBytes(StandardCharsets.UTF_8));
					}

					// Include the content of the files in the diff
					try (ObjectReader reader = repository.newObjectReader()) {
						// For modified/added files, include the new content
						AbbreviatedObjectId newIdAbbr = diff.getNewId();
						if (newIdAbbr != null && !newIdAbbr.equals(ObjectId.zeroId())) {
							try {
								ObjectId newId = repository.resolve(newIdAbbr.name());
								if (newId != null) {
									ObjectLoader newLoader = reader.open(newId);
									digest.update(newLoader.getBytes());
								}
							} catch (MissingObjectException | LargeObjectException e) {
								// Skip if the object is missing or too large
							}
						}
						// For modified/deleted files, include the old content
						AbbreviatedObjectId oldIdAbbr = diff.getOldId();
						if (oldIdAbbr != null && !oldIdAbbr.equals(ObjectId.zeroId())) {
							try {
								ObjectId oldId = repository.resolve(oldIdAbbr.name());
								if (oldId != null) {
									ObjectLoader oldLoader = reader.open(oldId);
									digest.update(oldLoader.getBytes());
								}
							} catch (MissingObjectException | LargeObjectException e) {
								// Skip if the object is missing or too large
							}
						}
					} catch (IOException e) {
						throw new RuntimeException("Failed to read file content for hashing", e);
					}
				}
			}

			byte[] hashBytes = digest.digest();
			StringBuilder hexString = new StringBuilder();
			for (byte b : hashBytes) {
				hexString.append(String.format("%02x", b));
			}

			return hexString.toString();
		} catch (final IOException | NoSuchAlgorithmException | GitAPIException ex) {
			throw new RuntimeException(ex);
		}
	}

	private AbstractTreeIterator prepareTreeParser(final Ref ref) {
		try (RevWalk walk = new RevWalk(repository)) {
			final RevCommit commit = walk.parseCommit(ref.getObjectId());
			return prepareTreeParser(commit);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private AbstractTreeIterator prepareTreeParser(final RevCommit commit) {
		try (RevWalk walk = new RevWalk(repository)) {
			final RevTree tree = walk.parseTree(commit.getTree().getId());

			final CanonicalTreeParser treeParser = new CanonicalTreeParser();
			try (ObjectReader reader = repository.newObjectReader()) {
				treeParser.reset(reader, tree.getId());
			}

			return treeParser;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
}