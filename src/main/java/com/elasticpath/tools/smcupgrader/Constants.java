package com.elasticpath.tools.smcupgrader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Application-wide constants.
 */
public final class Constants {

	/**
	 * The supported upstream repository URLs for fetching SMC release code.
	 */
	public static final List<String> UPSTREAM_REPO_URLS = Collections.unmodifiableList(Arrays.asList(
			"git@code.elasticpath.com:ep-commerce/ep-commerce.git",
			"https://code.elasticpath.com/ep-commerce/ep-commerce.git"
	));

	/**
	 * The file name for the upgrade plan.
	 */
	public static final String PLAN_FILE_NAME = "smc-upgrader-plan.md";

	/**
	 * The file name for the upgrade journal, which LLM steps use to record their reasoning.
	 */
	public static final String JOURNAL_FILE_NAME = "smc-upgrader-journal.md";

	/**
	 * Determines whether a path is a file that smc-upgrader writes in the codebase being upgraded.
	 *
	 * @param path the repository-relative path
	 * @return true if the path is the plan file or the journal file
	 */
	public static boolean isUpgraderFile(final String path) {
		return PLAN_FILE_NAME.equals(path) || JOURNAL_FILE_NAME.equals(path);
	}

	private Constants() {
		// Prevent instantiation
	}
}
