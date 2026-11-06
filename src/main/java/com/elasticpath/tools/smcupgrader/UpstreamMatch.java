package com.elasticpath.tools.smcupgrader;

/**
 * The upstream commit and blob whose contents match the latest local version of a file.
 */
public final class UpstreamMatch {
	private final String commitId;
	private final String blobId;

	/**
	 * Constructor.
	 *
	 * @param commitId the ID of the upstream commit
	 * @param blobId   the ID of the file's blob in that commit
	 */
	public UpstreamMatch(final String commitId, final String blobId) {
		this.commitId = commitId;
		this.blobId = blobId;
	}

	public String getCommitId() {
		return commitId;
	}

	public String getBlobId() {
		return blobId;
	}
}
