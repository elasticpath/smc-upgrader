package com.elasticpath.tools.smcupgrader.astgrep;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link AstGrepExecutor}.
 */
class AstGrepExecutorTest {

	@TempDir
	File tempDir;

	@Test
	void discoverRecipeFiles_filtersOutOlderBuckets() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.5.x", "a.yml");
		writeRecipe(recipesDir, "8.6.x", "b.yml");
		writeRecipe(recipesDir, "8.7.x", "c.yml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, VersionBucket.parse("8.6.x"));

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

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, VersionBucket.parse("8.6.x"));

		assertThat(recipes).extracting(p -> p.getFileName().toString())
				.containsExactly("b.yml", "m.yml", "a.yml", "z.yml");
	}

	@Test
	void discoverRecipeFiles_ignoresNonVersionDirs() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.7.x", "valid.yml");
		writeRecipe(recipesDir, "README", "ignored.yml");
		writeRecipe(recipesDir, "shared", "ignored.yml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, VersionBucket.parse("8.6.x"));

		assertThat(recipes).extracting(p -> p.getFileName().toString())
				.containsExactly("valid.yml");
	}

	@Test
	void discoverRecipeFiles_ignoresNonYmlFiles() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.7.x", "recipe.yml");
		writeRecipe(recipesDir, "8.7.x", "notes.txt");
		writeRecipe(recipesDir, "8.7.x", "data.yaml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, VersionBucket.parse("8.7.x"));

		assertThat(recipes).extracting(p -> p.getFileName().toString())
				.containsExactly("recipe.yml");
	}

	@Test
	void discoverRecipeFiles_returnsEmptyWhenNoMatchingBuckets() throws IOException {
		Path recipesDir = tempDir.toPath();
		writeRecipe(recipesDir, "8.5.x", "old.yml");

		List<Path> recipes = executor().discoverRecipeFiles(recipesDir, VersionBucket.parse("8.7.x"));

		assertThat(recipes).isEmpty();
	}

	private AstGrepExecutor executor() {
		return new AstGrepExecutor(tempDir);
	}

	private void writeRecipe(final Path recipesDir, final String bucket, final String filename) throws IOException {
		Path bucketDir = recipesDir.resolve(bucket);
		Files.createDirectories(bucketDir);
		Files.write(bucketDir.resolve(filename), "id: test\n".getBytes());
	}
}
