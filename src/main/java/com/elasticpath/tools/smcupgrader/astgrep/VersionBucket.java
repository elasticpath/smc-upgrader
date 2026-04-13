package com.elasticpath.tools.smcupgrader.astgrep;

/**
 * Represents a version bucket directory name like {@code "8.7.x"}.
 * Parses the first two numeric segments for numeric comparison.
 */
public final class VersionBucket implements Comparable<VersionBucket> {

	private static final int PARTS = 3;

	private final int major;
	private final int minor;
	private final String raw;

	private VersionBucket(final int major, final int minor, final String raw) {
		this.major = major;
		this.minor = minor;
		this.raw = raw;
	}

	/**
	 * Parse a version bucket string such as {@code "8.7.x"}.
	 *
	 * @param value the version string (e.g. "8.7.x")
	 * @return the parsed VersionBucket
	 * @throws IllegalArgumentException if the string cannot be parsed
	 */
	public static VersionBucket parse(final String value) {
		String[] parts = value.split("\\.");
		if (parts.length != PARTS) {
			throw new IllegalArgumentException("Version bucket must be like 8.7.x (got: " + value + ")");
		}
		try {
			return new VersionBucket(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Version bucket must be like 8.7.x (got: " + value + ")");
		}
	}

	/**
	 * Try to parse a version bucket string, returning {@code null} on invalid input.
	 *
	 * @param value the version string
	 * @return the parsed VersionBucket, or {@code null} if parsing fails
	 */
	public static VersionBucket tryParse(final String value) {
		try {
			return parse(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	public int compareTo(final VersionBucket other) {
		int cmp = Integer.compare(this.major, other.major);
		return cmp != 0 ? cmp : Integer.compare(this.minor, other.minor);
	}

	@Override
	public String toString() {
		return raw;
	}
}
