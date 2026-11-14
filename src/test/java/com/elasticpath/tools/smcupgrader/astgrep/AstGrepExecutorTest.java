package com.elasticpath.tools.smcupgrader.astgrep;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

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
	void isSupportedVersion_falseBelowMinimum() {
		assertThat(AstGrepExecutor.isSupportedVersion("ast-grep 0.45.3")).isFalse();
		assertThat(AstGrepExecutor.isSupportedVersion("ast-grep 0.49.9")).isFalse();
	}

	@Test
	void isSupportedVersion_trueAtOrAboveMinimum() {
		assertThat(AstGrepExecutor.isSupportedVersion("ast-grep 0.50.0")).isTrue();
		assertThat(AstGrepExecutor.isSupportedVersion("ast-grep 0.51.0")).isTrue();
		assertThat(AstGrepExecutor.isSupportedVersion("ast-grep 1.0.0")).isTrue();
	}

	@Test
	void isSupportedVersion_trueWhenUnparseable() {
		assertThat(AstGrepExecutor.isSupportedVersion("garbage")).isTrue();
		assertThat(AstGrepExecutor.isSupportedVersion("")).isTrue();
		assertThat(AstGrepExecutor.isSupportedVersion(null)).isTrue();
	}

	@Test
	void buildRecipeCommand_allowsCustomLanguagesWithConfig() {
		List<String> cmd = executor().buildRecipeCommand(Paths.get("recipe.yml"), "sgconfig.yml");

		assertThat(cmd).containsSequence("--config", "sgconfig.yml", "--custom-languages", "allow");
	}

	@Test
	void buildRecipeCommand_omitsConfigAndCustomLanguagesWithoutConfig() {
		List<String> cmd = executor().buildRecipeCommand(Paths.get("recipe.yml"), null);

		assertThat(cmd).doesNotContain("--config", "--custom-languages");
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

	private AstGrepExecutor executor() {
		return new AstGrepExecutor(tempDir, VERSIONS);
	}

	private void writeRecipe(final Path recipesDir, final String bucket, final String filename) throws IOException {
		Path bucketDir = recipesDir.resolve(bucket);
		Files.createDirectories(bucketDir);
		Files.write(bucketDir.resolve(filename), "id: test\n".getBytes());
	}
}
