package com.elasticpath.tools.smcupgrader;

import java.util.Objects;

/**
 * Represents a change to a single file resulting from a Git merge operation.
 */
public class Change {
	private final String path;

	private final ChangeType theirChangeType;

	private final ChangeType ourChangeType;

	private final String theirVersionSHA;

	private final String ourVersionSHA;

	private final FilePermissions theirFilePermissions;

	/**
	 * Constructor.
	 *
	 * @param path            the path of the file being changed, relative to the git root, in the source branch,
	 *                        i.e. the branch into which the merge is being performed.
	 * @param theirChangeType the {@link ChangeType} representing the type of change that was performed in the branch being merged.
	 *                        Use {@code null} if no local change was performed.
	 * @param ourChangeType   the {@link ChangeType} representing the type of change that was performed in the source branch.
	 *                        Use {@code null} if no local change was performed.
	 * @param theirVersionSHA the SHA of the Git BLOB containing the version of the file in the target branch,
	 *                        i.e. the branch being merged. Will be {@code null} when the file was created in the local branch.
	 * @param ourVersionSHA   the SHA of the Git BLOB containing the version of the file in the source branch,
	 *                        i.e. the branch into which the merge is being performed. Will be {@code null} when the file was created
	 *                        in the target branch.
	 * @param theirFilePermissions   the file permissions of the version in the target branch,
	 *                        i.e. the branch being merged. Will be {@code null} when the file was created in the local branch.
	 */
	public Change(final String path,
			final ChangeType theirChangeType,
			final ChangeType ourChangeType,
			final String theirVersionSHA,
			final String ourVersionSHA,
			final FilePermissions theirFilePermissions) {
		this.path = path;
		this.ourChangeType = ourChangeType;
		this.theirVersionSHA = theirVersionSHA;
		this.ourVersionSHA = ourVersionSHA;
		this.theirFilePermissions = theirFilePermissions;

		/*
		Sometimes Git struggles to represent a file rename correctly. Instead of it being tracked as a single status
        change, it gets split into a creation and deletion. Unfortunately, when the file has also been modified along
        with the rename (almost always the case with Java files, which typically will at least update the package),
        this tracks as an UPDATED modification on the status line item for the previous filename.
        Luckily, there is a reliable way of identifying these cases: if no BLOB SHA exists that represents the file
        contents in the upstream branch, the file doesn't exist, i.e. it has been deleted or renamed. In both of these
        cases we can represent the change type for this specific line item as a deletion, because a status line item
        will also exist for the file with the new, renamed filename, likely as a CREATED change type.
		 */
		// TODO verify
		if (this.theirVersionSHA == null && theirChangeType == ChangeType.UPDATED) {
			this.theirChangeType = ChangeType.DELETED;
		} else {
			this.theirChangeType = theirChangeType;
		}
	}

	@Override
	public String toString() {
		String status = "";

		if (ourChangeType != null) {
			status = ourChangeType + " locally and ";
		}

		status += theirChangeType + " remotely: " + path;

		if (ourVersionSHA != null) {
			status += "\n\tSHA of our version: " + ourVersionSHA;
		}

		return status + " \n\tSHA of their version: " + theirVersionSHA;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final Change change = (Change) other;
		return Objects.equals(path, change.path)
				&& theirChangeType == change.theirChangeType
				&& ourChangeType == change.ourChangeType
				&& Objects.equals(theirVersionSHA, change.theirVersionSHA)
				&& Objects.equals(ourVersionSHA, change.ourVersionSHA)
				&& Objects.equals(theirFilePermissions, change.theirFilePermissions);
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, theirChangeType, ourChangeType, theirVersionSHA, ourVersionSHA, theirFilePermissions);
	}

	/**
	 * Indicates whether this change represents a conflict.
	 *
	 * @return true if the change represents a conflict
	 */
	public boolean isConflict() {
		return this.ourChangeType != null && this.theirChangeType != null;
	}

	public String getPath() {
		return path;
	}

	public ChangeType getTheirChangeType() {
		return theirChangeType;
	}

	public ChangeType getOurChangeType() {
		return ourChangeType;
	}

	public String getTheirVersionSHA() {
		return theirVersionSHA;
	}

	public String getOurVersionSHA() {
		return ourVersionSHA;
	}

	public FilePermissions getTheirFilePermissions() {
		return theirFilePermissions;
	}
}
