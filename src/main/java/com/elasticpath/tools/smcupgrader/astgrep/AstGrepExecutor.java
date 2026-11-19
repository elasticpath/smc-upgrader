package com.elasticpath.tools.smcupgrader.astgrep;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.diff.DiffEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elasticpath.tools.smcupgrader.Constants;
import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.ShellCommand;

/**
 * Discovers and executes ast-grep recipes against a working tree.
 */
public class AstGrepExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger(AstGrepExecutor.class);
	private static final String RECIPES_DIR = "upgrade/recipes";
	private static final int MIN_MAJOR_VERSION = 0;
	private static final int MIN_MINOR_VERSION = 50;
	private static final String MIN_VERSION = MIN_MAJOR_VERSION + "." + MIN_MINOR_VERSION + ".0";
	private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
	private static final String DELETED = "deleted";

	private final File workingDir;
	private final List<String> versions;
	private final GitClient gitClient;
	private final ShellCommand shellCommand = new ShellCommand();
	private String astGrepVersion;

	/**
	 * Constructor.
	 *
	 * @param workingDir        the repository working directory to run recipes against
	 * @param versions ordered list of supported versions from configuration
	 * @param gitClient         the Git client, used to find the files that each recipe changes
	 */
	public AstGrepExecutor(final File workingDir, final List<String> versions, final GitClient gitClient) {
		this.workingDir = workingDir;
		this.versions = versions;
		this.gitClient = gitClient;
	}

	/**
	 * Run all applicable recipes for the given version.
	 *
	 * @param version the target version
	 * @return the report; it is successful if all recipes passed (or none found), and not successful if any failed
	 * @throws IOException if an I/O error occurs
	 */
	public RecipeReport run(final String version) throws IOException {
		final RecipeReport report = new RecipeReport(version);
		if (!isAstGrepAvailable()) {
			LOGGER.warn("ast-grep is not available on PATH.");
			LOGGER.warn("Automated upgrade recipes for XML/Java will be skipped.");
			LOGGER.warn("");
			LOGGER.warn("To install ast-grep, see: https://ast-grep.github.io/guide/quick-start.html");
			LOGGER.warn("");
			LOGGER.warn("After installation, re-run 'smc-upgrader --ai:continue' to apply recipes.");
			LOGGER.warn("To skip this step anyway, choose [Y/M] below.");
			report.setSkipReason("ast-grep is not available on the PATH, so no recipes ran.");
			return report;
		}

		String installedVersion = getAstGrepVersion();
		if (!isSupportedVersion(installedVersion)) {
			LOGGER.warn("Detected {}, but ast-grep {} or later is required.", installedVersion, MIN_VERSION);
			LOGGER.warn("Automated upgrade recipes for XML/Java will be skipped.");
			LOGGER.warn("");
			LOGGER.warn("To upgrade ast-grep, see: https://ast-grep.github.io/guide/quick-start.html");
			LOGGER.warn("");
			LOGGER.warn("After upgrading, re-run 'smc-upgrader --ai:continue' to apply recipes.");
			LOGGER.warn("To skip this step anyway, choose [Y/M] below.");
			report.setSkipReason("Detected " + installedVersion + ", but ast-grep " + MIN_VERSION
					+ " or later is required, so no recipes ran.");
			return report;
		}

		File recipesDir = new File(workingDir, RECIPES_DIR);
		if (!recipesDir.isDirectory()) {
			LOGGER.info("No upgrade recipes directory found. Skipping.");
			report.setSkipReason("The " + RECIPES_DIR + " directory does not exist, so no recipes ran.");
			report.setSuccess(true);
			return report;
		}

		List<Path> recipeFiles = discoverRecipeFiles(recipesDir.toPath(), version);
		if (recipeFiles.isEmpty()) {
			LOGGER.info("No applicable recipes for {}. Skipping.", version);
			report.setSkipReason("No recipes apply to " + version + ", so no recipes ran.");
			report.setSuccess(true);
			return report;
		}

		LOGGER.info("Found {} applicable recipe(s) for {}.", recipeFiles.size(), version);

		String astGrepConfig = resolveAstGrepConfig();

		try {
			int failCount = 0;
			for (Path recipeFile : recipeFiles) {
				LOGGER.info("Applying recipe: {}", recipeFile.getFileName());
				final Map<String, String> before = snapshotModifiedFiles();
				int exitCode = runRecipe(recipeFile, astGrepConfig);
				final Map<String, String> after = snapshotModifiedFiles();
				report.addResult(new RecipeReport.RecipeResult(relativeRecipePath(recipesDir.toPath(), recipeFile), exitCode,
						changedPaths(before, after)));
				if (exitCode != 0) {
					LOGGER.warn("Recipe {} failed (exit code {}).", recipeFile.getFileName(), exitCode);
					failCount++;
				}
			}

			if (failCount == 0) {
				LOGGER.info("All {} recipe(s) applied successfully.", recipeFiles.size());
				report.setSuccess(true);
			} else {
				LOGGER.warn("{} of {} recipe(s) failed.", failCount, recipeFiles.size());
			}
			return report;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("ast-grep execution interrupted", e);
		}
	}

	private static String relativeRecipePath(final Path recipesDir, final Path recipeFile) {
		return recipesDir.toAbsolutePath().normalize().relativize(recipeFile).toString().replace(File.separatorChar, '/');
	}

	/**
	 * Find the paths whose working tree state differs between two snapshots.
	 *
	 * @param before the snapshot taken before a recipe ran
	 * @param after  the snapshot taken after the recipe ran
	 * @return the changed paths
	 */
	private static List<String> changedPaths(final Map<String, String> before, final Map<String, String> after) {
		final Set<String> paths = new HashSet<>(before.keySet());
		paths.addAll(after.keySet());
		return paths.stream()
				.filter(path -> !Objects.equals(before.get(path), after.get(path)))
				.collect(Collectors.toList());
	}

	/**
	 * Snapshot the files that differ between the working tree and the index.
	 *
	 * @return map from repository-relative path to the SHA-256 hash of its content, or a deleted marker
	 * @throws IOException if a file cannot be read
	 */
	private Map<String, String> snapshotModifiedFiles() throws IOException {
		final Map<String, String> snapshot = new HashMap<>();
		for (DiffEntry entry : gitClient.getDiff()) {
			final String path = entry.getChangeType() == DiffEntry.ChangeType.ADD ? entry.getNewPath() : entry.getOldPath();
			if (!Constants.isUpgraderFile(path)) {
				snapshot.put(path, hash(new File(workingDir, path).toPath()));
			}
		}
		return snapshot;
	}

	private static String hash(final Path file) throws IOException {
		if (!Files.isRegularFile(file)) {
			return DELETED;
		}
		try {
			final byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
			final StringBuilder hex = new StringBuilder();
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Check whether the {@code ast-grep} binary is available on PATH, and record its version output.
	 *
	 * @return true if ast-grep is available
	 */
	protected boolean isAstGrepAvailable() {
		try {
			Optional<String> output = shellCommand.runForOutput("ast-grep --version");
			astGrepVersion = output.orElse(null);
			return output.isPresent();
		} catch (IOException e) {
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Get the {@code ast-grep --version} output recorded by {@link #isAstGrepAvailable()}.
	 *
	 * @return the version output, or null if not available
	 */
	protected String getAstGrepVersion() {
		return astGrepVersion;
	}

	/**
	 * Check whether the {@code ast-grep --version} output reports MIN_VERSION or later.
	 * Output that does not contain a version number is treated as supported.
	 *
	 * @param versionOutput the output of {@code ast-grep --version}, for example {@code ast-grep 0.50.0}
	 * @return true if the version is supported or cannot be parsed
	 */
	static boolean isSupportedVersion(final String versionOutput) {
		if (versionOutput == null) {
			return true;
		}
		Matcher matcher = VERSION_PATTERN.matcher(versionOutput);
		if (!matcher.find()) {
			return true;
		}
		int major = Integer.parseInt(matcher.group(1));
		int minor = Integer.parseInt(matcher.group(2));
		return major > MIN_MAJOR_VERSION || (major == MIN_MAJOR_VERSION && minor >= MIN_MINOR_VERSION);
	}

	/**
	 * Discover recipe files for the given target version and any later versions.
	 * Uses the configured version ordering to filter directories {@code >= targetVersion}
	 * and sort them in version order, then by filename within each version.
	 *
	 * @param recipesDir    the recipes directory
	 * @param targetVersion the target version string (e.g. "8.6.x")
	 * @return sorted list of recipe file paths (by version order then filename)
	 * @throws IOException if an I/O error occurs
	 */
	List<Path> discoverRecipeFiles(final Path recipesDir, final String targetVersion) throws IOException {
		int targetIndex = versions.indexOf(targetVersion);
		if (targetIndex == -1) {
			LOGGER.warn("Version {} not found in supported versions. No recipes will be applied.", targetVersion);
			return new ArrayList<>();
		}

		List<Path> bucketDirs = new ArrayList<>();
		try (DirectoryStream<Path> dirs = Files.newDirectoryStream(recipesDir, Files::isDirectory)) {
			for (Path dir : dirs) {
				String dirName = dir.getFileName().toString();
				int dirIndex = versions.indexOf(dirName);
				if (dirIndex >= 0 && dirIndex >= targetIndex) {
					bucketDirs.add(dir);
				}
			}
		}
		bucketDirs.sort(Comparator.comparingInt(d -> versions.indexOf(d.getFileName().toString())));

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
	private String resolveAstGrepConfig() {
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

		File astGrepConfig = new File(installDir, "native/" + platform.nativeDir() + "/sgconfig.yml");
		if (!astGrepConfig.isFile()) {
			LOGGER.warn("ast-grep config not found at: {}", astGrepConfig.getAbsolutePath());
			return null;
		}

		if (platform == Platform.MAC) {
			clearQuarantineFlag(new File(astGrepConfig.getParentFile(), platform.getLibraryFile()));
		}

		LOGGER.info("Tree-sitter XML support ready.");
		return astGrepConfig.getAbsolutePath();
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
	 * Run {@code ast-grep scan} for a single recipe file.
	 *
	 * @param recipeFile the recipe file
	 * @param astGrepConfig path to sgconfig.yml, or null if not available
	 * @return the process exit code
	 * @throws IOException          if an I/O error occurs
	 * @throws InterruptedException if the process is interrupted
	 */
	int runRecipe(final Path recipeFile, final String astGrepConfig) throws IOException, InterruptedException {
		String command = buildRecipeCommand(recipeFile, astGrepConfig).stream()
				.map(ShellCommand::quote)
				.collect(Collectors.joining(" "));
		return shellCommand.run(command, ProcessBuilder::inheritIO);
	}

	/**
	 * Build the {@code ast-grep scan} command arguments for a single recipe file.
	 *
	 * @param recipeFile the recipe file
	 * @param astGrepConfig path to sgconfig.yml, or null if not available
	 * @return the unquoted command arguments
	 */
	List<String> buildRecipeCommand(final Path recipeFile, final String astGrepConfig) {
		List<String> cmd = new ArrayList<>(Arrays.asList("ast-grep", "scan"));

		if (astGrepConfig != null) {
			cmd.add("--config");
			cmd.add(ShellCommand.toPosixPath(astGrepConfig));
			// ast-grep 0.50.0+ ignores the custom XML language library in sgconfig.yml without this opt-in.
			cmd.add("--custom-languages");
			cmd.add("allow");
		}

		cmd.addAll(Arrays.asList(
				"--rule", ShellCommand.toPosixPath(recipeFile.toString()),
				"--update-all", ShellCommand.toPosixPath(workingDir.getAbsolutePath())));

		return cmd;
	}
}
