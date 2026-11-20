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

import com.elasticpath.tools.smcupgrader.UpgradeReport;

/**
 * Writes the Git merge step section of the upgrade journal, with one file change group for each merge phase.
 */
public class MergeJournalWriter {
	private final StepJournalWriter stepJournalWriter;

	/**
	 * Constructor.
	 *
	 * @param workingDir the directory that contains the journal
	 * @param clock      the clock used for run timestamps
	 */
	public MergeJournalWriter(final File workingDir, final Clock clock) {
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
	public void write(final String stepTitle, final UpgradeReport report, final String failureMessage) throws IOException {
		final Map<String, List<String>> groups = new LinkedHashMap<>();
		if (report != null) {
			groups.put("Git merge", report.getMergeFileChanges());
			groups.put("Deterministic conflict resolution", report.getConflictResolutionFileChanges());
			groups.put("Outdated platform files", report.getOutdatedPlatformFileChanges());
		}
		stepJournalWriter.write(stepTitle, goal(report), evidence(report, failureMessage), groups);
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
		evidence.append(String.format("Deterministic conflict resolution changed %d file(s). Outdated platform files changed %d file(s).",
				report.getConflictPassChangeCount(), report.getDiffPassChangeCount()));
		return evidence.toString();
	}
}
