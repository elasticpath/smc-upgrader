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

	private Constants() {
		// Prevent instantiation
	}
}
