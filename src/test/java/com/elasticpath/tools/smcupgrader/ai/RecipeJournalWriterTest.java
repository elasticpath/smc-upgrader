package com.elasticpath.tools.smcupgrader.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.Constants;
import com.elasticpath.tools.smcupgrader.astgrep.RecipeReport;
import com.elasticpath.tools.smcupgrader.astgrep.RecipeReport.RecipeResult;

/**
 * Tests for {@link RecipeJournalWriter}.
 */
class RecipeJournalWriterTest {
	private static final String TITLE = "Run automated upgrade recipes for 8.7.x";

	@TempDir
	File tempDir;

	private RecipeJournalWriter writer;

	@BeforeEach
	void setUp() {
		writer = new RecipeJournalWriter(tempDir, Clock.fixed(Instant.parse("2026-11-06T14:32:00Z"), ZoneOffset.UTC));
	}

	@Test
	void writesOneSubsectionForEachRecipeThatChangedFiles() throws IOException {
		final RecipeReport report = new RecipeReport("8.7.x");
		report.setSuccess(true);
		report.addResult(new RecipeResult("8.7.x/rename-cache-bean.yml", 0, Arrays.asList("b/pom.xml", "a/Foo.java")));
		report.addResult(new RecipeResult("8.7.x/unused.yml", 0, Collections.emptyList()));

		writer.write(TITLE, report, null);

		assertThat(readJournal()).isEqualTo("## Run automated upgrade recipes for 8.7.x\n"
				+ "\n"
				+ "### Goal\n"
				+ "\n"
				+ "Apply the ast-grep upgrade recipes for 8.7.x and later versions.\n"
				+ "\n"
				+ "### Evidence\n"
				+ "\n"
				+ "Target version 8.7.x. 2 applicable recipe(s) ran. The recipes changed 2 file(s).\n"
				+ "\n"
				+ "### Decisions and file changes\n"
				+ "\n"
				+ "#### 8.7.x/rename-cache-bean.yml\n"
				+ "\n"
				+ "- `a/Foo.java`: rewritten by this recipe.\n"
				+ "- `b/pom.xml`: rewritten by this recipe.\n");
	}

	@Test
	void listsFileUnderEachRecipeThatChangedIt() throws IOException {
		final RecipeReport report = new RecipeReport("8.7.x");
		report.setSuccess(true);
		report.addResult(new RecipeResult("8.7.x/a.yml", 0, Collections.singletonList("pom.xml")));
		report.addResult(new RecipeResult("8.8.x/b.yml", 0, Collections.singletonList("pom.xml")));

		writer.write(TITLE, report, null);

		assertThat(readJournal())
				.contains("The recipes changed 1 file(s).")
				.endsWith("#### 8.7.x/a.yml\n\n- `pom.xml`: rewritten by this recipe.\n"
						+ "\n#### 8.8.x/b.yml\n\n- `pom.xml`: rewritten by this recipe.\n");
	}

	@Test
	void namesFailedRecipeWithExitCode() throws IOException {
		final RecipeReport report = new RecipeReport("8.7.x");
		report.addResult(new RecipeResult("8.7.x/a.yml", 2, Collections.singletonList("pom.xml")));

		writer.write(TITLE, report, null);

		assertThat(readJournal())
				.contains("Target version 8.7.x. 1 applicable recipe(s) ran. Recipe `8.7.x/a.yml` failed with exit code 2. "
						+ "The recipes changed 1 file(s).")
				.endsWith("#### 8.7.x/a.yml\n\n- `pom.xml`: rewritten by this recipe.\n");
	}

	@Test
	void writesSkipReasonWhenNoRecipesRan() throws IOException {
		final RecipeReport report = new RecipeReport("8.7.x");
		report.setSkipReason("ast-grep is not available on the PATH, so no recipes ran.");

		writer.write(TITLE, report, null);

		assertThat(readJournal())
				.contains("### Evidence\n\nTarget version 8.7.x. ast-grep is not available on the PATH, so no recipes ran.\n")
				.endsWith("### Decisions and file changes\n\nNo files changed.\n");
	}

	@Test
	void failureIsRecordedInEvidence() throws IOException {
		writer.write(TITLE, new RecipeReport("8.7.x"), "ast-grep execution interrupted");

		assertThat(readJournal())
				.contains("### Evidence\n\nTarget version 8.7.x. The recipe step failed: ast-grep execution interrupted\n")
				.endsWith("### Decisions and file changes\n\nNo files changed.\n");
	}

	@Test
	void rerunAddsRunSubsectionWithRecipeSubsectionsOneLevelLower() throws IOException {
		final RecipeReport report = new RecipeReport("8.7.x");
		report.addResult(new RecipeResult("8.7.x/a.yml", 0, Collections.singletonList("pom.xml")));
		writer.write(TITLE, report, null);
		final String before = readJournal();

		writer.write(TITLE, report, null);

		assertThat(readJournal().substring(before.length()))
				.startsWith("\n### Run 2026-11-06 14:32\n")
				.endsWith("#### Decisions and file changes\n\n##### 8.7.x/a.yml\n\n- `pom.xml`: rewritten by this recipe.\n");
	}

	private String readJournal() throws IOException {
		return new String(Files.readAllBytes(tempDir.toPath().resolve(Constants.JOURNAL_FILE_NAME)), StandardCharsets.UTF_8);
	}
}
