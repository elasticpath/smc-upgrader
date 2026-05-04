package com.elasticpath.tools.smcupgrader;

/**
 * Application-wide constants.
 */
public final class Constants {

	/**
	 * The URL of the upstream repository containing SMC release code.
	 */
	public static final String UPSTREAM_REPO_URL = "git@code.elasticpath.com:ep-commerce/ep-commerce.git";

	/**
	 * The file name for the upgrade plan.
	 */
	public static final String PLAN_FILE_NAME = "smc-upgrader-plan.md";

	private Constants() {
		// Prevent instantiation
	}
}
