package com.elasticpath.tools.smcupgrader;

import java.util.Objects;

/**
 * Represents a remote Git repository.
 */
public class RemoteRepository {
	private final String name;

	private final String url;

	/**
	 * Constructor.
	 *
	 * @param name the repository name
	 * @param url  the repository URL
	 */
	public RemoteRepository(final String name, final String url) {
		this.name = name;
		this.url = url;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}

		final RemoteRepository that = (RemoteRepository) other;
		return Objects.equals(name, that.name) && Objects.equals(url, that.url);
	}

	public String getName() {
		return name;
	}

	public String getUrl() {
		return url;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, url);
	}

	@Override
	public String toString() {
		return name + " " + url;
	}
}
