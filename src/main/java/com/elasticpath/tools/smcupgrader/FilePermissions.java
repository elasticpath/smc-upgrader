package com.elasticpath.tools.smcupgrader;

import java.util.Objects;

/**
 * Represents POSIX-like mode permissions for a file.
 */
public class FilePermissions {
	private final boolean readable;

	private final boolean writable;

	private final boolean executable;

	/**
	 * Constructor.
	 *
	 * @param readable   file is readable
	 * @param writable   file is writable
	 * @param executable file is executable
	 */
	public FilePermissions(final boolean readable, final boolean writable, final boolean executable) {
		this.readable = readable;
		this.writable = writable;
		this.executable = executable;
	}

	public boolean isReadable() {
		return readable;
	}

	public boolean isWritable() {
		return writable;
	}

	public boolean isExecutable() {
		return executable;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final FilePermissions that = (FilePermissions) other;
		return readable == that.readable && writable == that.writable && executable == that.executable;
	}

	@Override
	public int hashCode() {
		return Objects.hash(readable, writable, executable);
	}

}
