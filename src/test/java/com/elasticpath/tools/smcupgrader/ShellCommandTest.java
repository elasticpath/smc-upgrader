package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ShellCommand}.
 */
class ShellCommandTest {
	private static final String WINDOWS = "Windows 11";

	@TempDir
	Path tempDir;

	@Test
	void testResolveShell_nonWindowsUsesBinSh() throws IOException {
		ShellCommand shellCommand = new ShellCommand("Mac OS X", null, Optional::empty);

		assertThat(shellCommand.resolveShell()).isEqualTo("/bin/sh");
	}

	@Test
	void testResolveShell_windowsFindsShFromGitOnPath() throws IOException {
		Path gitInstall = createGitInstall();
		ShellCommand shellCommand = new ShellCommand(WINDOWS, pathOf(gitInstall.resolve("cmd")), Optional::empty);

		assertThat(shellCommand.resolveShell()).isEqualTo(gitInstall.resolve("usr/bin/sh.exe").toString());
	}

	@Test
	void testResolveShell_windowsPrefersGitInstallOverShOnPath() throws IOException {
		Path gitInstall = createGitInstall();
		Path otherShDir = createFile(tempDir.resolve("msys64/usr/bin/sh.exe")).getParent();
		ShellCommand shellCommand = new ShellCommand(WINDOWS, pathOf(otherShDir, gitInstall.resolve("cmd")), Optional::empty);

		assertThat(shellCommand.resolveShell()).isEqualTo(gitInstall.resolve("usr/bin/sh.exe").toString());
	}

	@Test
	void testResolveShell_windowsFindsShFromGitExecPathForShimmedGit() throws IOException {
		Path gitInstall = createGitInstall();
		Path shimDir = createFile(tempDir.resolve("scoop/shims/git.exe")).getParent();
		Path execPath = Files.createDirectories(gitInstall.resolve("mingw64/libexec/git-core"));
		ShellCommand shellCommand = new ShellCommand(WINDOWS, pathOf(shimDir), () -> Optional.of(execPath));

		assertThat(shellCommand.resolveShell()).isEqualTo(gitInstall.resolve("usr/bin/sh.exe").toString());
	}

	@Test
	void testResolveShell_windowsFallsBackToShOnPath() throws IOException {
		Path shDir = createFile(tempDir.resolve("msys64/usr/bin/sh.exe")).getParent();
		ShellCommand shellCommand = new ShellCommand(WINDOWS, pathOf(shDir), Optional::empty);

		assertThat(shellCommand.resolveShell()).isEqualTo(shDir.resolve("sh.exe").toString());
	}

	@Test
	void testResolveShell_windowsWithoutShellThrows() {
		ShellCommand shellCommand = new ShellCommand(WINDOWS, pathOf(tempDir), Optional::empty);

		assertThatThrownBy(shellCommand::resolveShell)
				.isInstanceOf(IOException.class)
				.hasMessage(ShellCommand.SHELL_NOT_FOUND_MESSAGE);
	}

	@Test
	void testQuote_escapesSingleQuotes() {
		assertThat(ShellCommand.quote("it's")).isEqualTo("'it'\\''s'");
	}

	@Test
	void testRun_returnsExitCodeAndDeletesScript() throws IOException, InterruptedException {
		// Uses the real platform shell, so on Windows this exercises Git for Windows' sh.exe.
		File workingDir = tempDir.toFile();
		Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"));
		long scriptsBefore = countScripts(tempRoot);

		int exitCode = new ShellCommand().run("echo first > out.txt\necho second >> out.txt\nexit 3",
				processBuilder -> processBuilder.directory(workingDir));

		assertThat(exitCode).isEqualTo(3);
		assertThat(Files.readAllLines(tempDir.resolve("out.txt"), StandardCharsets.UTF_8)).containsExactly("first", "second");
		assertThat(countScripts(tempRoot)).isEqualTo(scriptsBefore);
	}

	private static long countScripts(final Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(file -> {
				String name = file.getFileName().toString();
				return name.startsWith("smc-upgrader-") && name.endsWith(".sh");
			}).count();
		}
	}

	private Path createGitInstall() throws IOException {
		Path gitInstall = tempDir.resolve("Git");
		createFile(gitInstall.resolve("cmd/git.exe"));
		createFile(gitInstall.resolve("bin/sh.exe"));
		createFile(gitInstall.resolve("usr/bin/sh.exe"));
		return gitInstall;
	}

	private static Path createFile(final Path file) throws IOException {
		Files.createDirectories(file.getParent());
		return Files.createFile(file);
	}

	private static String pathOf(final Path... dirs) {
		return String.join(";", Stream.of(dirs).map(Path::toString).toArray(String[]::new));
	}
}
