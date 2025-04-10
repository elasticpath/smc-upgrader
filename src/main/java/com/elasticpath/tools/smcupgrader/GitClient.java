package com.elasticpath.tools.smcupgrader;

import java.io.File;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

public interface GitClient {
	/**
	 * Returns the current Git working directory.
	 *
	 * @return the working directory
	 */
	File getWorkingDir();

	/**
	 * Returns a set of remote repositories configured on the git repository.
	 *
	 * @return a set of remote repositories configured on the git repository
	 */
	Set<RemoteRepository> getRemoteRepositories();

	/**
	 * Adds a remote repository to the git config.
	 *
	 * @param name the name of the remote
	 * @param url  the URL of the remote
	 */
	void addUpstreamRemote(String name, String url);

	/**
	 * Returns the branch containing the release code corresponding to the given version.
	 *
	 * @param upstreamRemoteName the name of the upstream remote
	 * @param version            the version
	 * @return a {@link Ref} representing the release branch
	 */
	Ref getReleaseBranch(String upstreamRemoteName, String version);

	/**
	 * Determines whether the working directory is clean or contains uncommitted changes.
	 *
	 * @return true if the working directory contains uncommitted changes
	 */
	boolean workingDirectoryHasChanges();

	/**
	 * Performs a merge operation for the given ref.
	 *
	 * @param toMerge the {@link Ref} to merge into the current working branch.
	 */
	void merge(Ref toMerge);

	/**
	 * Returns merge conflicts that exist in the local working directory.
	 *
	 * @return a map of conflicting file names to their {@link IndexDiff.StageState StageState}.
	 */
	Map<String, IndexDiff.StageState> getConflicts();

	/**
	 * Returns the set of {@link IndexEntry} instances representing every index entry in the git database.
	 *
	 * @return the set of {@link IndexEntry} instances representing every index entry in the git database
	 */
	Set<IndexEntry> getStatusIndexEntries();

	/**
	 * Returns an iterable of commits containing the specified path in any branch with a name starting with upstreamRemoteName.
	 *
	 * @param path               a path that the commit must contain
	 * @param upstreamRemoteName the branch name prefix
	 * @return an iterable of commits
	 */
	Iterable<RevCommit> getAllCommitsForPathInAllBranches(String path, String upstreamRemoteName);

	/**
	 * Returns the latest commit in the current branch that contains the specified path.
	 *
	 * @param path a path that the commit must contain
	 * @return a commit, or null if the path can't be found
	 */
	RevCommit getLatestCommitForPath(String path);

	/**
	 * Returns the results of a diff of all unstaged changes in the working directory.
	 *
	 * @return a list of {@link DiffEntry} instances
	 */
	List<DiffEntry> getDiff();

	/**
	 * Returns the results of a diff between the local working directory and the given {@link Ref}.
	 *
	 * @param otherBranch the branch to diff
	 * @return a list of {@link DiffEntry} instances
	 */
	List<DiffEntry> getDiff(Ref otherBranch);

	/**
	 * Adds a file to the git index.
	 *
	 * @param path the path of the file to add to the git index
	 */
	void stage(String path);

	/**
	 * Removes a file from the git index.
	 *
	 * @param path the path of the file to remove from the git index
	 */
	void unstage(String path);

	/**
	 * Deletes a file from the local file system and git repository.
	 *
	 * @param path the path of the file to delete
	 */
	void delete(String path);

	/**
	 * Writes the contents of the object at the given SHA to the given output stream.
	 *
	 * @param hexSha       the SHA referencing an object in the git database
	 * @param outputStream the output stream to which the contents should be written
	 */
	void writeBlobContents(String hexSha, OutputStream outputStream);

	/**
	 * Returns a SHA-256 hash of the contents of the path at the specified commit.
	 *
	 * @param path   the path of a file
	 * @param commit a commit
	 * @return an optional hash of the file contents, or Optional.empty if we were unable to generate the content hash
	 */
	Optional<String> getContentHashOfPathAtCommit(String path, RevCommit commit);
}
