package com.elasticpath.tools.smcupgrader;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Determines if the local and incoming file changes contain the same content.
 */
public class ChangeContentsEquivalence {

	private final GitClient gitClient;

	/**
	 * Constructor.
	 *
	 * @param gitClient the git client
	 */
	public ChangeContentsEquivalence(final GitClient gitClient) {
		this.gitClient = gitClient;
	}

	/**
	 * Determines if the local and incoming file changes contain the same content.
	 *
	 * @param change the change
	 * @return true if the content of the local and incoming file changes are identical
	 */
	public boolean oursTheirsChangeContentsAreEqual(final Change change) {
		final ByteArrayOutputStream oursOutputStream = new ByteArrayOutputStream();
		final ByteArrayOutputStream theirsOutputStream = new ByteArrayOutputStream();

		gitClient.writeBlobContents(change.getOurVersionSHA(), oursOutputStream);
		gitClient.writeBlobContents(change.getTheirVersionSHA(), theirsOutputStream);

		final byte[] oursByteArray = oursOutputStream.toByteArray();
		final byte[] theirsByteArray = theirsOutputStream.toByteArray();

		return Arrays.equals(oursByteArray, theirsByteArray);
	}
}
