package com.elasticpath.tools.smcupgrader;

/**
 * The rules the conflict pass and the diff pass apply, with the codes used in the upgrade journal.
 */
public enum ResolutionRule {
	/** Conflict pass: the target release deleted the file. */
	R_DEL("R-DEL",
			"The target release deleted this file.",
			"Keep the local file. Rejected because the target release no longer has it."),

	/** Conflict pass: both sides added the file with identical contents. */
	R_SAME("R-SAME",
			"Both sides added this file with identical contents.",
			"Take the upstream file. Rejected because the contents are identical."),

	/** Conflict pass: the latest local version matches an upstream version. */
	R_SAFE("R-SAFE",
			"The latest local version matches an upstream version, so the file has no customer changes.",
			"Keep the local file. Rejected because it has no customer changes."),

	/** Conflict pass: no rule applies, so the conflict is left for manual resolution. */
	R_MANUAL("R-MANUAL",
			"The latest local version matches no upstream version, so the file can contain customer changes.",
			"Take the upstream file. Rejected because it can discard customer changes."),

	/** Diff pass: the file is replaced with the target release version. */
	D_REPLACE("D-REPLACE",
			"The file differs from the target release, and the latest local version matches an upstream version, "
					+ "so it has no customer changes.",
			"Keep the local file. Rejected because it has no customer changes."),

	/** Diff pass: the target release does not have the file, so it is deleted. */
	D_DELETE("D-DELETE",
			"The target release does not have this file, and the latest local version matches an upstream version, "
					+ "so it has no customer changes.",
			"Keep the local file. Rejected because it has no customer changes."),

	/** Diff pass: the target release has the file and the local tree does not, so it is restored. */
	D_READD("D-READD",
			"The target release has this file, and the local tree does not.",
			"Leave the file missing. Rejected because the latest local version matches an upstream version.");

	private final String code;
	private final String reason;
	private final String rejectedAlternatives;

	ResolutionRule(final String code, final String reason, final String rejectedAlternatives) {
		this.code = code;
		this.reason = reason;
		this.rejectedAlternatives = rejectedAlternatives;
	}

	public String getCode() {
		return code;
	}

	public String getReason() {
		return reason;
	}

	public String getRejectedAlternatives() {
		return rejectedAlternatives;
	}
}
