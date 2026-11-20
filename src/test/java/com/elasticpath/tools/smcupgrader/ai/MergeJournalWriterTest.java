package com.elasticpath.tools.smcupgrader.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.Constants;
import com.elasticpath.tools.smcupgrader.UpgradeReport;

/**
 * Tests for {@link MergeJournalWriter}.
 */
class MergeJournalWriterTest {
	private static final String TITLE = "Git merge from 8.5.x to 8.6.x";

	@TempDir
	File tempDir;

	private MergeJournalWriter writer;

	@BeforeEach
	void setUp() {
		writer = new MergeJournalWriter(tempDir, Clock.fixed(Instant.parse("2026-11-06T14:32:00Z"), ZoneOffset.UTC));
	}

	@Test
	void writesSectionWithPhaseSubsectionsInOrder() throws IOException {
		writer.write(TITLE, report(), null);

		assertThat(readJournal()).isEqualTo("## Git merge from 8.5.x to 8.6.x\n"
				+ "\n"
				+ "### Goal\n"
				+ "\n"
				+ "Merge the smc-upgrades/release/8.6.x branch and resolve the conflicts and differences that are safe to resolve "
				+ "automatically.\n"
				+ "\n"
				+ "### Evidence\n"
				+ "\n"
				+ "Current version 8.5.x. Target branch smc-upgrades/release/8.6.x at commit abc123. Git merged 3 file(s) without "
				+ "a conflict and found 2 conflict(s). Deterministic conflict resolution changed 1 file(s). Outdated platform files "
				+ "changed 1 file(s).\n"
				+ "\n"
				+ "### Decisions and file changes\n"
				+ "\n"
				+ "#### Git merge\n"
				+ "\n"
				+ "- `a.txt`: replaced with theirs because it had no local changes.\n"
				+ "- `b.txt`: conflict markers added because local and upstream changes overlap.\n"
				+ "\n"
				+ "#### Deterministic conflict resolution\n"
				+ "\n"
				+ "- `b.txt`: replaced with theirs because it matches an upstream commit.\n"
				+ "\n"
				+ "#### Outdated platform files\n"
				+ "\n"
				+ "- `c.txt`: replaced with theirs because it was out of date.\n");
	}

	@Test
	void omitsPhaseWithoutChanges() throws IOException {
		final UpgradeReport report = new UpgradeReport();
		report.setCurrentVersion("8.5.x");
		report.addOutdatedPlatformFileChanges(Collections.singletonList(UpgradeReport.fileChange("c.txt", "out of date.")));

		writer.write(TITLE, report, null);

		assertThat(readJournal())
				.endsWith("### Decisions and file changes\n\n#### Outdated platform files\n\n- `c.txt`: out of date.\n")
				.doesNotContain("#### Git merge")
				.doesNotContain("#### Deterministic conflict resolution");
	}

	@Test
	void writesNoFilesChangedWhenNoPhaseChangedFiles() throws IOException {
		final UpgradeReport report = new UpgradeReport();
		report.setCurrentVersion("8.5.x");

		writer.write(TITLE, report, null);

		assertThat(readJournal()).endsWith("### Decisions and file changes\n\nNo files changed.\n");
	}

	@Test
	void rerunWritesPhaseSubsectionsUnderRun() throws IOException {
		writer.write(TITLE, report(), null);
		final String before = readJournal();

		writer.write(TITLE, report(), null);

		assertThat(readJournal().substring(before.length()))
				.startsWith("\n### Run 2026-11-06 14:32\n\n#### Goal\n")
				.contains("#### Decisions and file changes\n\n##### Git merge\n")
				.contains("\n##### Deterministic conflict resolution\n")
				.contains("\n##### Outdated platform files\n");
	}

	@Test
	void failureIsRecordedInEvidence() throws IOException {
		writer.write(TITLE, null, "No release branch for version 8.6.x found in remote repository.");

		assertThat(readJournal())
				.contains("Merge the target release branch")
				.contains("### Evidence\n\nThe merge step failed: No release branch for version 8.6.x found in remote repository.\n")
				.endsWith("### Decisions and file changes\n\nNo files changed.\n");
	}

	private static UpgradeReport report() {
		final UpgradeReport report = new UpgradeReport();
		report.setCurrentVersion("8.5.x");
		report.setTargetBranch("smc-upgrades/release/8.6.x");
		report.setTargetCommitId("abc123");
		report.setCleanMergeCount(3);
		report.setConflictCount(2);
		report.setConflictPassChangeCount(1);
		report.setDiffPassChangeCount(1);
		report.addMergeFileChanges(Arrays.asList(
				UpgradeReport.fileChange("a.txt", "replaced with theirs because it had no local changes."),
				UpgradeReport.fileChange("b.txt", "conflict markers added because local and upstream changes overlap.")));
		report.addConflictResolutionFileChanges(Collections.singletonList(
				UpgradeReport.fileChange("b.txt", "replaced with theirs because it matches an upstream commit.")));
		report.addOutdatedPlatformFileChanges(Collections.singletonList(
				UpgradeReport.fileChange("c.txt", "replaced with theirs because it was out of date.")));
		return report;
	}

	private Path journalPath() {
		return tempDir.toPath().resolve(Constants.JOURNAL_FILE_NAME);
	}

	private String readJournal() throws IOException {
		return new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8);
	}
}
