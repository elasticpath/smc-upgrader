package com.elasticpath.tools.smcupgrader.astgrep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.diff.DiffEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.Constants;
import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.astgrep.RecipeReport.RecipeResult;

/**
 * Tests for {@link AstGrepExecutor}.
 */
class AstGrepExecutorTest {

	private static final List<String> VERSIONS = Arrays.asList("8.5.x", "8.6.x", "8.7.x");

	@TempDir
	File tempDir;

	private GitClient gitClient;
	/** Paths that differ between the working tree and the index, as the mock Git client reports them. */
	private final Set<String> modifiedPaths = new LinkedHashSet<>();

	@BeforeEach
	void setUp() {
		gitClient = mock(GitClient.class);
		when(gitClient.getDiff()).thenAnswer(invocation -> modifiedPaths.stream()
				.map(AstGrepExecutorTest::modifiedDiff)
				.collect(Collectors.toList()));
	}

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
	void run_skipsRecipesWhenAstGrepTooOld() throws IOException {
		final RecipeReport report = executorWithAstGrepVersion("ast-grep 0.45.3").run("8.6.x");

		assertThat(report.isSuccess()).isFalse();
		assertThat(report.getSkipReason()).contains("0.50.0 or later is required");
	}

	@Test
	void run_reportsAstGrepNotAvailable() throws IOException {
		final RecipeReport report = new AstGrepExecutor(tempDir, VERSIONS, gitClient) {
			@Override
			protected boolean isAstGrepAvailable() {
				return false;
			}
		}.run("8.6.x");

		assertThat(report.isSuccess()).isFalse();
		assertThat(report.getSkipReason()).isEqualTo("ast-grep is not available on the PATH, so no recipes ran.");
		assertThat(report.getResults()).isEmpty();
	}

	@Test
	void run_reportsNoApplicableRecipes() throws IOException {
		writeRecipe(recipesDir(), "8.5.x", "old.yml");

		final RecipeReport report = executorWithAstGrepVersion("ast-grep 0.51.0").run("8.6.x");

		assertThat(report.isSuccess()).isTrue();
		assertThat(report.getSkipReason()).isEqualTo("No recipes apply to 8.6.x, so no recipes ran.");
	}

	@Test
	void run_recordsFilesThatEachRecipeChanges() throws IOException {
		writeRecipe(recipesDir(), "8.6.x", "a.yml");
		writeRecipe(recipesDir(), "8.7.x", "b.yml");
		writeRecipe(recipesDir(), "8.7.x", "c.yml");
		// Changes left by an earlier run, which no recipe in this run touches.
		editFile("earlier.txt", "earlier");
		final Map<String, String[]> edits = new HashMap<>();
		edits.put("a.yml", new String[]{"x.txt", "y.txt", Constants.JOURNAL_FILE_NAME});
		edits.put("b.yml", new String[]{});
		edits.put("c.yml", new String[]{"y.txt"});

		final RecipeReport report = executorWithRecipeEdits(edits, new HashMap<>()).run("8.6.x");

		assertThat(report.isSuccess()).isTrue();
		assertThat(report.getSkipReason()).isNull();
		assertThat(report.getResults()).extracting(RecipeResult::getRecipePath)
				.containsExactly("8.6.x/a.yml", "8.7.x/b.yml", "8.7.x/c.yml");
		assertThat(report.getResults()).extracting(RecipeResult::getChangedPaths)
				.containsExactly(Arrays.asList("x.txt", "y.txt"), new ArrayList<>(), Arrays.asList("y.txt"));
	}

	@Test
	void run_recordsDeletedFile() throws IOException {
		writeRecipe(recipesDir(), "8.6.x", "a.yml");

		final RecipeReport report = new AstGrepExecutor(tempDir, VERSIONS, gitClient) {
			@Override
			protected boolean isAstGrepAvailable() {
				return true;
			}

			@Override
			int runRecipe(final Path recipeFile, final String astGrepConfig) {
				// A deleted tracked file is in the diff, but no longer on disk.
				modifiedPaths.add("gone.txt");
				return 0;
			}
		}.run("8.6.x");

		assertThat(report.getResults().get(0).getChangedPaths()).containsExactly("gone.txt");
	}

	@Test
	void run_recordsFailedRecipeAndItsChanges() throws IOException {
		writeRecipe(recipesDir(), "8.6.x", "a.yml");
		final Map<String, String[]> edits = new HashMap<>();
		edits.put("a.yml", new String[]{"x.txt"});
		final Map<String, Integer> exitCodes = new HashMap<>();
		exitCodes.put("a.yml", 2);

		final RecipeReport report = executorWithRecipeEdits(edits, exitCodes).run("8.6.x");

		assertThat(report.isSuccess()).isFalse();
		assertThat(report.getResults()).hasSize(1);
		assertThat(report.getResults().get(0).getExitCode()).isEqualTo(2);
		assertThat(report.getResults().get(0).getChangedPaths()).containsExactly("x.txt");
	}

	@Test
	void run_continuesWhenAstGrepSupported() throws IOException {
		// No upgrade/recipes directory exists, so run() returns true once past the version check.
		final RecipeReport report = executorWithAstGrepVersion("ast-grep 0.51.0").run("8.6.x");

		assertThat(report.isSuccess()).isTrue();
		assertThat(report.getSkipReason()).isEqualTo("The upgrade/recipes directory does not exist, so no recipes ran.");
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
		return new AstGrepExecutor(tempDir, VERSIONS, gitClient);
	}

	private AstGrepExecutor executorWithAstGrepVersion(final String version) {
		return new AstGrepExecutor(tempDir, VERSIONS, gitClient) {
			@Override
			protected boolean isAstGrepAvailable() {
				return true;
			}

			@Override
			protected String getAstGrepVersion() {
				return version;
			}
		};
	}

	/**
	 * Creates an executor whose recipes edit files instead of running ast-grep.
	 *
	 * @param edits     map from recipe file name to the paths it edits
	 * @param exitCodes map from recipe file name to its exit code; a missing entry means 0
	 */
	private AstGrepExecutor executorWithRecipeEdits(final Map<String, String[]> edits, final Map<String, Integer> exitCodes) {
		return new AstGrepExecutor(tempDir, VERSIONS, gitClient) {
			@Override
			protected boolean isAstGrepAvailable() {
				return true;
			}

			@Override
			protected File resolveInstallDir() {
				return null;
			}

			@Override
			int runRecipe(final Path recipeFile, final String astGrepConfig) throws IOException {
				final String name = recipeFile.getFileName().toString();
				for (String path : edits.get(name)) {
					editFile(path, name);
				}
				return exitCodes.getOrDefault(name, 0);
			}
		};
	}

	/**
	 * Appends text to a file and records it as modified, as a recipe rewrite would.
	 */
	private void editFile(final String path, final String text) throws IOException {
		final Path file = tempDir.toPath().resolve(path);
		final String existing = Files.exists(file) ? new String(Files.readAllBytes(file), StandardCharsets.UTF_8) : "";
		Files.write(file, (existing + text + "\n").getBytes(StandardCharsets.UTF_8));
		modifiedPaths.add(path);
	}

	private Path recipesDir() {
		return tempDir.toPath().resolve("upgrade/recipes");
	}

	private static DiffEntry modifiedDiff(final String path) {
		final DiffEntry diff = mock(DiffEntry.class);
		when(diff.getChangeType()).thenReturn(DiffEntry.ChangeType.MODIFY);
		when(diff.getOldPath()).thenReturn(path);
		return diff;
	}

	private void writeRecipe(final Path recipesDir, final String bucket, final String filename) throws IOException {
		Path bucketDir = recipesDir.resolve(bucket);
		Files.createDirectories(bucketDir);
		Files.write(bucketDir.resolve(filename), "id: test\n".getBytes());
	}
}
