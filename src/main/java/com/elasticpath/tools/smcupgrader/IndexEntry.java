package com.elasticpath.tools.smcupgrader;

import java.util.Objects;

public class IndexEntry {
	private final String path;

	private final String sha;

	private final Stage stage;

	/**
	 * Constructor.
	 *
	 * @param path        the path of the Git object
	 * @param sha         the ID of the contents of the Git object
	 * @param stageNumber the stage number
	 * @see <a href="https://git-scm.com/docs/gitrevisions#Documentation/gitrevisions.txt-emltngtltpathgtemegem0READMEememREADMEem">
	 * Git Revisions documentation</a>
	 */
	public IndexEntry(final String path, final int stageNumber, final String sha) {
		this.path = path;
		this.stage = Stage.fromStageNumber(stageNumber);
		this.sha = sha;
	}

	public String getPath() {
		return path;

	}

	public String getSha() {
		return sha;
	}

	public Stage getStage() {
		return stage;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final IndexEntry that = (IndexEntry) other;
		return Objects.equals(path, that.path) && Objects.equals(sha, that.sha) && stage == that.stage;
	}

	@Override
	public int hashCode() {
		return Objects.hash(path, sha, stage);
	}

	@Override
	public String toString() {
		return sha + " " + stage.stageNumber + " " + path;
	}

	/**
	 * Enumerates the valid stage values.
	 */
	public enum Stage {
		INDEX(0),
		BASE(1),
		OURS(2),
		THEIRS(3);

		private final int stageNumber;

		/**
		 * Constructor.
		 *
		 * @param stageNumber the Git stage number
		 * @see <a href="https://git-scm.com/docs/gitrevisions#Documentation/gitrevisions.txt-emltngtltpathgtemegem0READMEememREADMEem">
		 * Git Revisions documentation</a>
		 */
		Stage(final int stageNumber) {
			this.stageNumber = stageNumber;
		}

		/**
		 * Creates a Stage from the given stage number.
		 *
		 * @param stageNumber the stage number
		 * @return a Stage
		 * @see <a href="https://git-scm.com/docs/gitrevisions#Documentation/gitrevisions.txt-emltngtltpathgtemegem0READMEememREADMEem">
		 * Git Revisions documentation</a>
		 */
		public static Stage fromStageNumber(final int stageNumber) {
			for (final Stage value : values()) {
				if (value.stageNumber == stageNumber) {
					return value;
				}
			}

			throw new IllegalArgumentException("No Stage corresponds to stage number " + stageNumber);
		}
	}

}
