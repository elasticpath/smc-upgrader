package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link UpgradeJournal} and {@link JournalEntry}.
 */
class UpgradeJournalTest {

	@TempDir
	File tempDir;

	private UpgradeJournal journal;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-11-05T14:32:00Z"), ZoneOffset.UTC);
		journal = new UpgradeJournal(tempDir, clock);
	}

	@Test
	void firstRunCreatesStepSectionAndRunSubsection() {
		journal.startRun("Git merge from 8.5.x to 8.6.x");
		journal.appendParagraph("Narrative.");
		journal.appendEntries(Collections.singletonList(JournalEntry.forFile("pom.xml", "diff")));

		assertThat(journal.read()).isEqualTo("## Git merge from 8.5.x to 8.6.x\n"
				+ "\n"
				+ "### Run 2026-11-05 14:32\n"
				+ "\n"
				+ "Narrative.\n"
				+ "\n"
				+ "```yaml\n"
				+ "file: \"pom.xml\"\n"
				+ "step: \"Git merge from 8.5.x to 8.6.x\"\n"
				+ "pass: \"diff\"\n"
				+ "extension_or_core: \"core\"\n"
				+ "```\n");
	}

	@Test
	void rerunOfLastStepAddsOnlyRunSubsection() {
		journal.startRun("Step A");
		journal.startRun("Step A");

		assertThat(journal.read()).containsOnlyOnce("## Step A\n");
		assertThat(journal.read().split("### Run ", -1)).hasSize(3);
	}

	@Test
	void rerunOfEarlierStepAppendsNewSectionAndKeepsEarlierContent() {
		journal.startRun("Step A");
		journal.appendParagraph("A1");
		journal.startRun("Step B");
		journal.appendParagraph("B1");
		String before = journal.read();

		journal.startRun("Step A");
		journal.appendParagraph("A2");

		String after = journal.read();
		assertThat(after).startsWith(before);
		assertThat(after.substring(before.length())).isEqualTo("\n## Step A\n\n### Run 2026-11-05 14:32\n\nA2\n");
	}

	@Test
	void entryKeepsExplicitStepAndRendersFieldsInFixedOrder() {
		journal.startRun("Step A");
		JournalEntry entry = JournalEntry.forFile("extensions/a.xml", "llm")
				.with(JournalEntry.NEEDS_REVIEW, true)
				.with(JournalEntry.REASON, "why")
				.with(JournalEntry.STEP, "Other step");
		journal.appendEntries(Collections.singletonList(entry));

		assertThat(entry.render()).isEqualTo("```yaml\n"
				+ "file: \"extensions/a.xml\"\n"
				+ "step: \"Other step\"\n"
				+ "pass: \"llm\"\n"
				+ "extension_or_core: \"extension\"\n"
				+ "reason: \"why\"\n"
				+ "needs_review: true\n"
				+ "```\n");
	}

	@Test
	void renderQuotesValuesWithColonsQuotesAndNewlines() {
		JournalEntry entry = JournalEntry.forFile("a.java", "llm")
				.with(JournalEntry.REASON, "key: \"value\"\nback\\slash");

		assertThat(entry.render()).contains("reason: \"key: \\\"value\\\"\\nback\\\\slash\"\n");
	}

	@Test
	void extensionOrCoreUsesExtensionsPrefix() {
		assertThat(JournalEntry.extensionOrCore("extensions/core/pom.xml")).isEqualTo("extension");
		assertThat(JournalEntry.extensionOrCore("commerce-engine/core/pom.xml")).isEqualTo("core");
		assertThat(JournalEntry.extensionOrCore("my-extensions/pom.xml")).isEqualTo("core");
	}

	@Test
	void countFileFieldsNormalizesPathsAndToleratesMalformedBlocks() {
		String content = "```yaml\nfile: \"a/b.java\"\n```\n"
				+ "```yaml\n  file: ./a/b.java\nreason: unterminated \"quote\n```\n"
				+ "```yaml\nfile: c.xml\n";

		Map<String, Integer> counts = UpgradeJournal.countFileFields(content);

		assertThat(counts).containsEntry("a/b.java", 2).containsEntry("c.xml", 1).hasSize(2);
	}

	@Test
	void appendEntriesWithNoEntriesWritesNothing() {
		journal.appendEntries(Arrays.asList());

		assertThat(new File(tempDir, Constants.JOURNAL_FILE_NAME)).doesNotExist();
	}
}
