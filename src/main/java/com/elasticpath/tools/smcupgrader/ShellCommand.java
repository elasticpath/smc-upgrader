package com.elasticpath.tools.smcupgrader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs commands through a POSIX shell on every platform.
 *
 * <p>The command is written to a temporary script file and run as {@code <sh> <script>}, so the command text
 * never crosses the process command line. This avoids Windows argument re-splitting of embedded quotes.
 * On Windows, the shell is the {@code sh.exe} shipped with Git for Windows.</p>
 */
public class ShellCommand {
	/**
	 * Message for the exception thrown when no POSIX shell can be found.
	 */
	public static final String SHELL_NOT_FOUND_MESSAGE = "A POSIX shell (sh.exe) was not found. On Windows, install Git for Windows.";

	private static final Logger LOGGER = LoggerFactory.getLogger(ShellCommand.class);

	private final String osName;
	private final String path;
	private final Supplier<Optional<Path>> gitExecPath;
	private String shell;

	/**
	 * Constructor using the current platform, PATH, and Git install.
	 */
	public ShellCommand() {
		this(System.getProperty("os.name", ""), System.getenv("PATH"), ShellCommand::queryGitExecPath);
	}

	/**
	 * Constructor.
	 *
	 * @param osName      the operating system name
	 * @param path        the PATH environment variable value (may be null)
	 * @param gitExecPath supplies the output of {@code git --exec-path}, if available
	 */
	public ShellCommand(final String osName, final String path, final Supplier<Optional<Path>> gitExecPath) {
		this.osName = osName;
		this.path = path;
		this.gitExecPath = gitExecPath;
	}

	/**
	 * Run a command through the shell and wait for it to finish.
	 *
	 * @param command    the shell command, in POSIX sh syntax
	 * @param configurer configures the process (working directory, redirects) before it starts
	 * @return the exit code
	 * @throws IOException          if no shell is found or the process cannot be started
	 * @throws InterruptedException if interrupted while waiting
	 */
	public int run(final String command, final Consumer<ProcessBuilder> configurer) throws IOException, InterruptedException {
		String sh = resolveShell();
		// Pass the command in a script file, not as "sh -c <command>". Windows passes a process one command-line
		// string, and ProcessBuilder does not escape embedded quotes when joining arguments into it, so prompts
		// containing " or \ would be re-split and corrupted. This also avoids the 32K Windows command-line limit.
		// The script is used on every platform so macOS and Linux tests exercise the same code path.
		Path script = Files.createTempFile("smc-upgrader-", ".sh");
		try {
			Files.write(script, command.getBytes(StandardCharsets.UTF_8));
			ProcessBuilder processBuilder = new ProcessBuilder(sh, toPosixPath(script.toAbsolutePath().toString()));
			configurer.accept(processBuilder);
			return processBuilder.start().waitFor();
		} finally {
			try {
				Files.deleteIfExists(script);
			} catch (IOException e) {
				LOGGER.debug("Could not delete temporary script {}", script, e);
			}
		}
	}

	/**
	 * Resolve the shell executable, caching the result.
	 *
	 * @return the shell executable path
	 * @throws IOException if no shell is found
	 */
	public String resolveShell() throws IOException {
		if (shell == null) {
			shell = findShell().orElseThrow(() -> new IOException(SHELL_NOT_FOUND_MESSAGE));
		}
		return shell;
	}

	private Optional<String> findShell() {
		if (!osName.startsWith("Windows")) {
			return Optional.of("/bin/sh");
		}
		return findOnPath("git.exe").flatMap(git -> findShInGitInstall(git.getParent()))
				.or(() -> gitExecPath.get().flatMap(ShellCommand::findShInGitInstall))
				.or(() -> findOnPath("sh.exe"))
				.map(Path::toString);
	}

	private Optional<Path> findOnPath(final String fileName) {
		if (path == null) {
			return Optional.empty();
		}
		for (String entry : path.split(";")) {
			String dir = entry.trim().replace("\"", "");
			if (dir.isEmpty()) {
				continue;
			}
			try {
				Path candidate = Paths.get(dir, fileName);
				if (Files.isRegularFile(candidate)) {
					return Optional.of(candidate);
				}
			} catch (InvalidPathException e) {
				LOGGER.debug("Ignoring invalid PATH entry {}", dir);
			}
		}
		return Optional.empty();
	}

	/**
	 * Walk up from a directory inside a Git for Windows install, looking for its {@code sh.exe}.
	 *
	 * @param start a directory inside the Git install, such as {@code Git\cmd} or {@code Git\mingw64\libexec\git-core}
	 * @return the shell, if found
	 */
	private static Optional<Path> findShInGitInstall(final Path start) {
		for (Path dir = start; dir != null; dir = dir.getParent()) {
			for (Path candidate : new Path[]{dir.resolve("usr/bin/sh.exe"), dir.resolve("bin/sh.exe")}) {
				if (Files.isRegularFile(candidate)) {
					return Optional.of(candidate);
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<Path> queryGitExecPath() {
		try {
			Process process = new ProcessBuilder("git", "--exec-path")
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			String output;
			try (InputStream stdout = process.getInputStream()) {
				output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8).trim();
			}
			if (process.waitFor() != 0 || output.isEmpty()) {
				return Optional.empty();
			}
			return Optional.of(Paths.get(output));
		} catch (IOException | InvalidPathException e) {
			LOGGER.debug("Could not run git --exec-path", e);
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
	}

	/**
	 * Single-quote a value for POSIX sh, escaping embedded single quotes as {@code '\''}.
	 *
	 * @param value the value to quote
	 * @return the quoted value
	 */
	public static String quote(final String value) {
		return "'" + value.replace("'", "'\\''") + "'";
	}

	/**
	 * Convert Windows path separators to forward slashes, which Git's {@code sh.exe} and native programs both accept.
	 *
	 * @param filePath the path
	 * @return the path with {@code \} replaced by {@code /}
	 */
	public static String toPosixPath(final String filePath) {
		return filePath.replace('\\', '/');
	}
}
