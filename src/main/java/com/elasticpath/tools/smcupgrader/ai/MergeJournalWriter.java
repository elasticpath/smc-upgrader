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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

import com.elasticpath.tools.smcupgrader.Constants;
import com.elasticpath.tools.smcupgrader.UpgradeReport;

/**
 * Writes the Git merge step section of the upgrade journal.
 */
public class MergeJournalWriter {
	private static final DateTimeFormatter RUN_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final File workingDir;
	private final Clock clock;

	/**
	 * Constructor.
	 *
	 * @param workingDir the directory that contains the journal
	 * @param clock      the clock used for run timestamps
	 */
	public MergeJournalWriter(final File workingDir, final Clock clock) {
		this.workingDir = workingDir;
		this.clock = clock;
	}

	/**
	 * Appends a section for the step. If the journal already has a section with the step title, appends a run subsection under it.
	 *
	 * @param stepTitle      the step title
	 * @param report         the report of what the step did, or null if the step failed
	 * @param failureMessage the error message if the step failed, or null
	 * @throws IOException if the journal cannot be read or written
	 */
	public void write(final String stepTitle, final UpgradeReport report, final String failureMessage) throws IOException {
		final Path journal = workingDir.toPath().resolve(Constants.JOURNAL_FILE_NAME);
		final String existing = Files.exists(journal) ? new String(Files.readAllBytes(journal), StandardCharsets.UTF_8) : "";
		final boolean rerun = hasStepHeading(existing, stepTitle);

		final StringBuilder section = new StringBuilder();
		if (!existing.isEmpty()) {
			section.append(existing.endsWith("\n") ? "\n" : "\n\n");
		}
		final String subheading;
		if (rerun) {
			// A run subsection belongs under the step heading, so repeat the heading if another step's section came after it.
			if (!lastStepHeadingIs(existing, stepTitle)) {
				section.append("## ").append(stepTitle).append("\n\n");
			}
			section.append("### Run ").append(LocalDateTime.now(clock).format(RUN_TIMESTAMP)).append("\n\n");
			subheading = "#### ";
		} else {
			section.append("## ").append(stepTitle).append("\n\n");
			subheading = "### ";
		}

		section.append(subheading).append("Goal\n\n").append(goal(report)).append("\n\n");
		section.append(subheading).append("Evidence\n\n").append(evidence(report, failureMessage)).append("\n\n");
		section.append(subheading).append("Decisions and file changes\n\n");
		final List<String> fileChanges = report == null ? Arrays.asList() : report.getFileChanges();
		if (fileChanges.isEmpty()) {
			section.append("No files changed.\n");
		} else {
			fileChanges.forEach(change -> section.append("- ").append(change).append('\n'));
		}

		Files.write(journal, section.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	private static boolean hasStepHeading(final String journal, final String stepTitle) {
		final String heading = "## " + stepTitle;
		return Arrays.stream(journal.split("\\r?\\n")).anyMatch(line -> line.trim().equals(heading));
	}

	private static boolean lastStepHeadingIs(final String journal, final String stepTitle) {
		String lastHeading = null;
		for (String line : journal.split("\\r?\\n")) {
			if (line.startsWith("## ")) {
				lastHeading = line.trim();
			}
		}
		return ("## " + stepTitle).equals(lastHeading);
	}

	private static String goal(final UpgradeReport report) {
		final String branch = report == null || report.getTargetBranch() == null ? "target release" : report.getTargetBranch();
		return "Merge the " + branch + " branch and resolve the conflicts and differences that are safe to resolve automatically.";
	}

	private static String evidence(final UpgradeReport report, final String failureMessage) {
		if (failureMessage != null) {
			return "The merge step failed: " + failureMessage;
		}
		final StringBuilder evidence = new StringBuilder();
		evidence.append("Current version ").append(report.getCurrentVersion()).append(". ");
		if (report.getTargetBranch() != null) {
			evidence.append("Target branch ").append(report.getTargetBranch());
			if (report.getTargetCommitId() != null) {
				evidence.append(" at commit ").append(report.getTargetCommitId());
			}
			evidence.append(". ");
		}
		evidence.append(String.format("Git merged %d file(s) without a conflict and found %d conflict(s). ",
				report.getCleanMergeCount(), report.getConflictCount()));
		evidence.append(String.format("The conflict pass changed %d file(s). The diff pass changed %d file(s).",
				report.getConflictPassChangeCount(), report.getDiffPassChangeCount()));
		return evidence.toString();
	}
}
