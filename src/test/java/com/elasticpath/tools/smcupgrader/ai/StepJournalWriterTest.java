package com.elasticpath.tools.smcupgrader.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.Constants;

/**
 * Tests for {@link StepJournalWriter}.
 */
class StepJournalWriterTest {
	private static final String TITLE = "Git merge from 8.5.x to 8.6.x";

	@TempDir
	File tempDir;

	private StepJournalWriter writer;

	@BeforeEach
	void setUp() {
		writer = new StepJournalWriter(tempDir, Clock.fixed(Instant.parse("2026-11-06T14:32:00Z"), ZoneOffset.UTC));
	}

	@Test
	void writesGroupsInOrderAndOmitsEmptyGroups() throws IOException {
		writer.write(TITLE, "The goal.", "The evidence.", groups());

		assertThat(readJournal()).isEqualTo("## Git merge from 8.5.x to 8.6.x\n"
				+ "\n"
				+ "### Goal\n"
				+ "\n"
				+ "The goal.\n"
				+ "\n"
				+ "### Evidence\n"
				+ "\n"
				+ "The evidence.\n"
				+ "\n"
				+ "### Decisions and file changes\n"
				+ "\n"
				+ "#### Second\n"
				+ "\n"
				+ "- b\n"
				+ "- c\n"
				+ "\n"
				+ "#### First\n"
				+ "\n"
				+ "- a\n");
	}

	@Test
	void writesNoFilesChangedWhenAllGroupsAreEmpty() throws IOException {
		final Map<String, List<String>> groups = new LinkedHashMap<>();
		groups.put("Empty", Collections.emptyList());

		writer.write(TITLE, "The goal.", "The evidence.", groups);

		assertThat(readJournal()).endsWith("### Decisions and file changes\n\nNo files changed.\n").doesNotContain("Empty");
	}

	@Test
	void writesNoFilesChangedWhenThereAreNoGroups() throws IOException {
		writer.write(TITLE, "The goal.", "The evidence.", Collections.emptyMap());

		assertThat(readJournal()).endsWith("### Decisions and file changes\n\nNo files changed.\n");
	}

	@Test
	void rerunAppendsRunSubsectionWithLowerHeadingsAndKeepsEarlierContent() throws IOException {
		writer.write(TITLE, "The goal.", "The evidence.", groups());
		final String before = readJournal();

		writer.write(TITLE, "The goal.", "The evidence.", groups());

		final String after = readJournal();
		assertThat(after).startsWith(before);
		assertThat(after.substring(before.length())).isEqualTo("\n### Run 2026-11-06 14:32\n"
				+ "\n"
				+ "#### Goal\n"
				+ "\n"
				+ "The goal.\n"
				+ "\n"
				+ "#### Evidence\n"
				+ "\n"
				+ "The evidence.\n"
				+ "\n"
				+ "#### Decisions and file changes\n"
				+ "\n"
				+ "##### Second\n"
				+ "\n"
				+ "- b\n"
				+ "- c\n"
				+ "\n"
				+ "##### First\n"
				+ "\n"
				+ "- a\n");
		assertThat(after).containsOnlyOnce("## " + TITLE + "\n");
	}

	@Test
	void rerunAfterAnotherStepRepeatsStepHeading() throws IOException {
		writer.write(TITLE, "The goal.", "The evidence.", groups());
		Files.write(journalPath(), "\n## Resolve conflicts\n\nLLM notes.\n".getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);
		final String before = readJournal();

		writer.write(TITLE, "The goal.", "The evidence.", groups());

		assertThat(readJournal().substring(before.length())).startsWith("\n## " + TITLE + "\n\n### Run 2026-11-06 14:32\n");
	}

	@Test
	void appendsBlankLineAfterJournalWithoutTrailingNewline() throws IOException {
		Files.write(journalPath(), "## Other step".getBytes(StandardCharsets.UTF_8));

		writer.write(TITLE, "The goal.", "The evidence.", groups());

		assertThat(readJournal()).startsWith("## Other step\n\n## " + TITLE + "\n");
	}

	private static Map<String, List<String>> groups() {
		final Map<String, List<String>> groups = new LinkedHashMap<>();
		groups.put("Empty", Collections.emptyList());
		groups.put("Second", Arrays.asList("b", "c"));
		groups.put("First", Collections.singletonList("a"));
		return groups;
	}

	private Path journalPath() {
		return tempDir.toPath().resolve(Constants.JOURNAL_FILE_NAME);
	}

	private String readJournal() throws IOException {
		return new String(Files.readAllBytes(journalPath()), StandardCharsets.UTF_8);
	}
}
