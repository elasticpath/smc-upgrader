package com.elasticpath.tools.smcupgrader.astgrep;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and executes ast-grep recipes against a working tree.
 */
public class AstGrepExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger(AstGrepExecutor.class);
	private static final String RECIPES_DIR = "upgrade/recipes";

	private final File workingDir;

	/**
	 * Constructor.
	 *
	 * @param workingDir the repository working directory to run recipes against
	 */
	public AstGrepExecutor(final File workingDir) {
		this.workingDir = workingDir;
	}

	/**
	 * Run all applicable recipes for the given version.
	 *
	 * @param version the target version bucket
	 * @return true if all recipes passed (or none found), false if any failed
	 * @throws IOException if an I/O error occurs
	 */
	public boolean run(final String version) throws IOException {
		if (!isSgAvailable()) {
			LOGGER.warn("ast-grep (sg) is not available on PATH.");
			LOGGER.warn("Automated upgrade recipes for XML/Java will be skipped.");
			LOGGER.warn("");
			LOGGER.warn("To install ast-grep, see: https://ast-grep.github.io/guide/quick-start.html");
			LOGGER.warn("");
			LOGGER.warn("After installation, re-run 'smc-upgrader --ai:continue' to apply recipes.");
			LOGGER.warn("To skip this step anyway, choose [Y/M] below.");
			return false;
		}

		VersionBucket targetBucket = VersionBucket.parse(version);

		File recipesDir = new File(workingDir, RECIPES_DIR);
		if (!recipesDir.isDirectory()) {
			LOGGER.info("No upgrade recipes directory found. Skipping.");
			return true;
		}

		List<Path> recipeFiles = discoverRecipeFiles(recipesDir.toPath(), targetBucket);
		if (recipeFiles.isEmpty()) {
			LOGGER.info("No applicable recipes for bucket {}. Skipping.", version);
			return true;
		}

		LOGGER.info("Found {} applicable recipe(s) for {}.", recipeFiles.size(), version);

		String sgConfig = resolveSgConfig();

		try {
			int failCount = 0;
			for (Path recipeFile : recipeFiles) {
				LOGGER.info("Applying recipe: {}", recipeFile.getFileName());
				int exitCode = runRecipe(recipeFile, sgConfig);
				if (exitCode != 0) {
					LOGGER.warn("Recipe {} failed (exit code {}).", recipeFile.getFileName(), exitCode);
					failCount++;
				}
			}

			if (failCount == 0) {
				LOGGER.info("All {} recipe(s) applied successfully.", recipeFiles.size());
				return true;
			} else {
				LOGGER.warn("{} of {} recipe(s) failed.", failCount, recipeFiles.size());
				return false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("ast-grep execution interrupted", e);
		}
	}

	/**
	 * Check whether the {@code sg} binary is available on PATH.
	 *
	 * @return true if sg is available
	 */
	protected boolean isSgAvailable() {
		try {
			int exitCode = new ProcessBuilder("sg", "--version")
					.redirectErrorStream(true)
					.start()
					.waitFor();
			return exitCode == 0;
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Discover recipe files for the given target bucket and any later version buckets.
	 * Scans all version subdirectories {@code >= targetBucket} for {@code *.yml} files,
	 * supporting backported recipes that live in later version folders.
	 *
	 * @param recipesDir   the recipes directory
	 * @param targetBucket the target version bucket
	 * @return sorted list of recipe file paths (by bucket then filename)
	 * @throws IOException if an I/O error occurs
	 */
	List<Path> discoverRecipeFiles(final Path recipesDir, final VersionBucket targetBucket) throws IOException {
		List<Path> bucketDirs = new ArrayList<>();
		try (DirectoryStream<Path> dirs = Files.newDirectoryStream(recipesDir, Files::isDirectory)) {
			for (Path dir : dirs) {
				VersionBucket bucket = VersionBucket.tryParse(dir.getFileName().toString());
				if (bucket != null && bucket.compareTo(targetBucket) >= 0) {
					bucketDirs.add(dir);
				}
			}
		}
		bucketDirs.sort(Comparator.comparing(d -> VersionBucket.parse(d.getFileName().toString())));

		List<Path> recipeFiles = new ArrayList<>();
		for (Path bucketDir : bucketDirs) {
			List<Path> bucketRecipes = new ArrayList<>();
			try (DirectoryStream<Path> recipes = Files.newDirectoryStream(bucketDir, "*.yml")) {
				for (Path recipe : recipes) {
					if (Files.isRegularFile(recipe)) {
						bucketRecipes.add(recipe.toAbsolutePath().normalize());
					}
				}
			}
			bucketRecipes.sort(Comparator.comparing(a -> a.getFileName().toString()));
			recipeFiles.addAll(bucketRecipes);
		}

		return recipeFiles;
	}

	/**
	 * Resolve the platform-specific sgconfig.yml shipped alongside the installation JAR
	 * in {@code native/<platform>/sgconfig.yml}.
	 *
	 * @return the resolved config path, or null if none available
	 */
	private String resolveSgConfig() {
		Platform platform = Platform.detect();
		if (platform == null) {
			LOGGER.warn("Unsupported platform: {}. XML recipes may not work.", System.getProperty("os.name"));
			return null;
		}

		File installDir = resolveInstallDir();
		if (installDir == null) {
			LOGGER.warn("Could not determine installation directory. XML recipes may not work.");
			return null;
		}

		File sgConfig = new File(installDir, "native/" + platform.nativeDir() + "/sgconfig.yml");
		if (!sgConfig.isFile()) {
			LOGGER.warn("sg config not found at: {}", sgConfig.getAbsolutePath());
			return null;
		}

		if (platform == Platform.MAC) {
			clearQuarantineFlag(new File(sgConfig.getParentFile(), platform.getLibraryFile()));
		}

		LOGGER.info("Tree-sitter XML support ready.");
		return sgConfig.getAbsolutePath();
	}

	enum Platform {
		MAC("mac", "tree-sitter-xml.dylib"),
		WINDOWS("windows", "tree-sitter-xml.dll"),
		LINUX_X86_64("linux", "tree-sitter-xml.so"),
		LINUX_AARCH64("linux", "tree-sitter-xml.so");

		private final String platformName;
		private final String libraryFile;

		Platform(final String platformName, final String libraryFile) {
			this.platformName = platformName;
			this.libraryFile = libraryFile;
		}

		String nativeDir() {
			return name().toLowerCase();
		}

		String getLibraryFile() {
			return libraryFile;
		}

		static Platform detect() {
			String osName = System.getProperty("os.name", "").toLowerCase();
			String arch = System.getProperty("os.arch", "").toLowerCase();

			if (osName.contains(MAC.platformName)) {
				return MAC;
			} else if (osName.contains(WINDOWS.platformName)) {
				return WINDOWS;
			} else if (osName.contains(LINUX_X86_64.platformName)) {
				return (arch.contains("aarch64") || arch.contains("arm64")) ? LINUX_AARCH64 : LINUX_X86_64;
			}
			return null;
		}
	}

	/**
	 * Resolve the installation directory (the directory containing the JAR or classes).
	 *
	 * @return the installation directory, or null if it cannot be determined
	 */
	protected File resolveInstallDir() {
		try {
			File codeLocation = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
			// If running from JAR, go up to its parent directory
			return codeLocation.isFile() ? codeLocation.getParentFile() : codeLocation;
		} catch (Exception e) {
			// getCodeSource() or getLocation() may return null in some environments
			return null;
		}
	}

	private void clearQuarantineFlag(final File file) {
		try {
			new ProcessBuilder("xattr", "-d", "com.apple.quarantine", file.getAbsolutePath())
					.redirectErrorStream(true)
					.start()
					.waitFor();
		} catch (IOException e) {
			LOGGER.debug("Could not clear quarantine flag: {}", e.getMessage());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Run {@code sg scan} for a single recipe file.
	 *
	 * @param recipeFile the recipe file
	 * @param sgConfig path to sgconfig.yml, or null if not available
	 * @return the process exit code
	 * @throws IOException          if an I/O error occurs
	 * @throws InterruptedException if the process is interrupted
	 */
	int runRecipe(final Path recipeFile, final String sgConfig) throws IOException, InterruptedException {
		List<String> cmd = new ArrayList<>(Arrays.asList("sg", "scan"));

		if (sgConfig != null) {
			cmd.add("--config");
			cmd.add(sgConfig);
		}

		cmd.addAll(Arrays.asList(
				"--rule", recipeFile.toString(),
				"--update-all", workingDir.getAbsolutePath()));

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.inheritIO();
		return pb.start().waitFor();
	}
}
