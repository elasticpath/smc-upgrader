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

package com.elasticpath.tools.smcupgrader.ai;

import java.io.File;
import java.io.IOException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.elasticpath.tools.smcupgrader.UpgradeReport;
import com.elasticpath.tools.smcupgrader.astgrep.RecipeReport;
import com.elasticpath.tools.smcupgrader.astgrep.RecipeReport.RecipeResult;

/**
 * Writes the ast-grep recipe step section of the upgrade journal, with one file change group for each recipe.
 */
public class RecipeJournalWriter {
	private final StepJournalWriter stepJournalWriter;

	/**
	 * Constructor.
	 *
	 * @param workingDir the directory that contains the journal
	 * @param clock      the clock used for run timestamps
	 */
	public RecipeJournalWriter(final File workingDir, final Clock clock) {
		this.stepJournalWriter = new StepJournalWriter(workingDir, clock);
	}

	/**
	 * Appends a section for the step. If the journal already has a section with the step title, appends a run subsection under it.
	 *
	 * @param stepTitle      the step title
	 * @param report         the report of what the step did, or null if the step failed
	 * @param failureMessage the error message if the step failed, or null
	 * @throws IOException if the journal cannot be read or written
	 */
	public void write(final String stepTitle, final RecipeReport report, final String failureMessage) throws IOException {
		final Map<String, List<String>> groups = new LinkedHashMap<>();
		if (report != null) {
			for (RecipeResult result : report.getResults()) {
				if (!result.getChangedPaths().isEmpty()) {
					groups.put(result.getRecipePath(), result.getChangedPaths().stream()
							.map(path -> UpgradeReport.fileChange(path, "rewritten by this recipe."))
							.collect(Collectors.toList()));
				}
			}
		}
		stepJournalWriter.write(stepTitle, goal(report), evidence(report, failureMessage), groups);
	}

	private static String goal(final RecipeReport report) {
		final String version = report == null || report.getTargetVersion() == null ? "the target version" : report.getTargetVersion();
		return "Apply the ast-grep upgrade recipes for " + version + " and later versions.";
	}

	private static String evidence(final RecipeReport report, final String failureMessage) {
		final StringBuilder evidence = new StringBuilder();
		if (report != null && report.getTargetVersion() != null) {
			evidence.append("Target version ").append(report.getTargetVersion()).append(". ");
		}
		if (failureMessage != null) {
			return evidence.append("The recipe step failed: ").append(failureMessage).toString();
		}
		if (report.getSkipReason() != null) {
			return evidence.append(report.getSkipReason()).toString();
		}
		evidence.append(String.format("%d applicable recipe(s) ran. ", report.getResults().size()));
		report.getResults().stream()
				.filter(result -> result.getExitCode() != 0)
				.forEach(result -> evidence.append(String.format("Recipe `%s` failed with exit code %d. ",
						result.getRecipePath(), result.getExitCode())));
		final long changedFileCount = report.getResults().stream()
				.flatMap(result -> result.getChangedPaths().stream())
				.distinct()
				.count();
		evidence.append(String.format("The recipes changed %d file(s).", changedFileCount));
		return evidence.toString();
	}
}
