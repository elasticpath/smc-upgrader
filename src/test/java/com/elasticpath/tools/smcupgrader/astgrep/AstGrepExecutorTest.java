package com.elasticpath.tools.smcupgrader.astgrep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.JournalEntry;
import com.elasticpath.tools.smcupgrader.UpgradeJournal;

/**
 * Tests for {@link AstGrepExecutor}.
 */
class AstGrepExecutorTest {

	private static final List<String> VERSIONS = Arrays.asList("8.5.x", "8.6.x", "8.7.x");

	@TempDir
	File tempDir;

	@Test
	@EnabledIfEnvironmentVariable(named = "SMC_UPGRADER_TEST_AST_GREP", matches = "npm|pip")
	void isAstGrepAvailable_trueWhenInstalled() {
		// CI sets the variable after installing ast-grep with npm or pip, covering both install layouts on Windows.
		assertThat(executor().isAstGrepAvailable()).isTrue();
	}

	@Test
	void discoverRecipeFiles_filtersOutOlderBuckets() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.5.x", "a.yml");
		writeRecipe(recipesDir, "8.6.x", "b.yml");
		writeRecipe(recipesDir, "8.7.x", "c.yml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, "8.6.x");

		assertThat(recipes).extracting(p -> p.getFileName().toString())
				.containsExactly("b.yml", "c.yml");
	}

	@Test
	void discoverRecipeFiles_sortsByBucketThenFilename() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.7.x", "z.yml");
		writeRecipe(recipesDir, "8.7.x", "a.yml");
		writeRecipe(recipesDir, "8.6.x", "m.yml");
		writeRecipe(recipesDir, "8.6.x", "b.yml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, "8.6.x");

		assertThat(recipes).extracting(p -> p.getFileName().toString())
				.containsExactly("b.yml", "m.yml", "a.yml", "z.yml");
	}

	@Test
	void discoverRecipeFiles_ignoresNonVersionDirs() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.7.x", "valid.yml");
		writeRecipe(recipesDir, "README", "ignored.yml");
		writeRecipe(recipesDir, "shared", "ignored.yml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, "8.6.x");

		assertThat(recipes).extracting(p -> p.getFileName().toString())
				.containsExactly("valid.yml");
	}

	@Test
	void discoverRecipeFiles_ignoresNonYmlFiles() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.7.x", "recipe.yml");
		writeRecipe(recipesDir, "8.7.x", "notes.txt");
		writeRecipe(recipesDir, "8.7.x", "data.yaml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, "8.7.x");

		assertThat(recipes).extracting(p -> p.getFileName().toString())
				.containsExactly("recipe.yml");
	}

	@Test
	void discoverRecipeFiles_returnsEmptyWhenNoMatchingBuckets() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.5.x", "old.yml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, "8.7.x");

		assertThat(recipes).isEmpty();
	}

	@Test
	void discoverRecipeFiles_returnsEmptyWhenVersionNotInSupportedList() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.7.x", "recipe.yml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, "9.0.x");

		assertThat(recipes).isEmpty();
	}

	@Test
	void createJournalEntries_usesRecipeIdsAndMessages() throws IOException {
		Path recipe = tempDir.toPath().resolve("upgrade/recipes/8.7.x/jakarta.yml");
		Files.createDirectories(recipe.getParent());
		Files.write(recipe, ("id: javax-to-jakarta\nmessage: Replace javax imports with jakarta.\nrule:\n  id: nested-not-a-rule\n"
				+ "---\nid: 'servlet-api'\nmessage: \"Update servlet API.\"\n").getBytes(StandardCharsets.UTF_8));

		List<JournalEntry> entries = executor().createJournalEntries(recipe, new HashSet<>(Arrays.asList("extensions/a/A.java")));

		assertThat(entries).hasSize(1);
		JournalEntry entry = entries.get(0);
		assertThat(entry.get(JournalEntry.FILE)).isEqualTo("extensions/a/A.java");
		assertThat(entry.get(JournalEntry.PASS)).isEqualTo("ast-grep");
		assertThat(entry.get(JournalEntry.RULE)).isEqualTo("upgrade/recipes/8.7.x/jakarta.yml: javax-to-jakarta, servlet-api");
		assertThat(entry.get(JournalEntry.REASON)).isEqualTo("Replace javax imports with jakarta. Update servlet API.");
		assertThat(entry.get(JournalEntry.EXTENSION_OR_CORE)).isEqualTo("extension");
		assertThat(entry.get(JournalEntry.REGION)).isEqualTo(JournalEntry.WHOLE_FILE);
	}

	@Test
	void run_recordsFilesEachRecipeChanged() throws IOException {
		Path recipesDir = tempDir.toPath().resolve("upgrade/recipes");
		writeRecipe(recipesDir, "8.7.x", "a.yml");
		writeRecipe(recipesDir, "8.7.x", "b.yml");
		GitClient gitClient = mock(GitClient.class);
		when(gitClient.getWorkingDir()).thenReturn(tempDir);
		when(gitClient.getUncommittedPaths()).thenReturn(new HashSet<>(Arrays.asList("A.java", "B.java")));
		UpgradeJournal journal = new UpgradeJournal(tempDir);

		AstGrepExecutor executor = new AstGrepExecutor(tempDir, VERSIONS, gitClient, journal, "Apply recipes") {
			@Override
			protected boolean isAstGrepAvailable() {
				return true;
			}

			@Override
			int runRecipe(final Path recipeFile, final String astGrepConfig) throws IOException {
				// Recipe a.yml changes A.java, recipe b.yml changes B.java
				String changed = recipeFile.getFileName().toString().equals("a.yml") ? "A.java" : "B.java";
				Files.write(tempDir.toPath().resolve(changed), "rewritten".getBytes(StandardCharsets.UTF_8));
				return 0;
			}
		};

		assertThat(executor.run("8.7.x")).isTrue();

		String content = journal.read();
		assertThat(content).startsWith("## Apply recipes\n");
		assertThat(content).contains("file: \"A.java\"\nstep: \"Apply recipes\"\npass: \"ast-grep\"\nrule: \"upgrade/recipes/8.7.x/a.yml: test\"");
		assertThat(content).contains("file: \"B.java\"\nstep: \"Apply recipes\"\npass: \"ast-grep\"\nrule: \"upgrade/recipes/8.7.x/b.yml: test\"");
		assertThat(UpgradeJournal.countFileFields(content)).containsEntry("A.java", 1).containsEntry("B.java", 1);
	}

	@Test
	void run_withoutJournalWritesNothing() throws IOException {
		writeRecipe(tempDir.toPath().resolve("upgrade/recipes"), "8.7.x", "a.yml");
		AstGrepExecutor executor = new AstGrepExecutor(tempDir, VERSIONS) {
			@Override
			protected boolean isAstGrepAvailable() {
				return true;
			}

			@Override
			int runRecipe(final Path recipeFile, final String astGrepConfig) {
				return 0;
			}
		};

		assertThat(executor.run("8.7.x")).isTrue();
		assertThat(new File(tempDir, "smc-upgrader-journal.md")).doesNotExist();
	}

	private AstGrepExecutor executor() {
		return new AstGrepExecutor(tempDir, VERSIONS);
	}

	private void writeRecipe(final Path recipesDir, final String bucket, final String filename) throws IOException {
		Path bucketDir = recipesDir.resolve(bucket);
		Files.createDirectories(bucketDir);
		Files.write(bucketDir.resolve(filename), "id: test\n".getBytes());
	}
}
