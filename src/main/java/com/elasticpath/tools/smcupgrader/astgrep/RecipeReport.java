/*	Copyright 2026 Elastic Path Software Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.elasticpath.tools.smcupgrader.astgrep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records what the ast-grep recipe step did, so that AI Assist Mode can write it to the upgrade journal.
 */
public class RecipeReport {
	private final String targetVersion;
	private boolean success;
	private String skipReason;
	private final List<RecipeResult> results = new ArrayList<>();

	/**
	 * Constructor.
	 *
	 * @param targetVersion the target version of the step
	 */
	public RecipeReport(final String targetVersion) {
		this.targetVersion = targetVersion;
	}

	public String getTargetVersion() {
		return targetVersion;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(final boolean success) {
		this.success = success;
	}

	/**
	 * Gets the reason that no recipes ran.
	 *
	 * @return the reason, or null if recipes ran
	 */
	public String getSkipReason() {
		return skipReason;
	}

	public void setSkipReason(final String skipReason) {
		this.skipReason = skipReason;
	}

	/**
	 * Adds the result of a recipe. Add results in the order that the recipes ran.
	 *
	 * @param result the recipe result
	 */
	public void addResult(final RecipeResult result) {
		results.add(result);
	}

	public List<RecipeResult> getResults() {
		return Collections.unmodifiableList(results);
	}

	/**
	 * The result of one recipe.
	 */
	public static class RecipeResult {
		private final String recipePath;
		private final int exitCode;
		private final List<String> changedPaths;

		/**
		 * Constructor.
		 *
		 * @param recipePath   the recipe path relative to the recipes directory, with forward slashes
		 * @param exitCode     the ast-grep exit code
		 * @param changedPaths the repository-relative paths that the recipe changed
		 */
		public RecipeResult(final String recipePath, final int exitCode, final List<String> changedPaths) {
			this.recipePath = recipePath;
			this.exitCode = exitCode;
			final List<String> sorted = new ArrayList<>(changedPaths);
			Collections.sort(sorted);
			this.changedPaths = Collections.unmodifiableList(sorted);
		}

		public String getRecipePath() {
			return recipePath;
		}

		public int getExitCode() {
			return exitCode;
		}

		public List<String> getChangedPaths() {
			return changedPaths;
		}
	}
}
