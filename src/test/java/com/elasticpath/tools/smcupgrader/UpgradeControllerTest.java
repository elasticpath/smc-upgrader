package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test class for {@link UpgradeController}.
 */
@ExtendWith(MockitoExtension.class)
class UpgradeControllerTest {

	@Mock
	private GitClient gitClient;

	@TempDir
	File repoDir;

	private UpgradeController upgradeController;

	@BeforeEach
	public void setUp() {
		lenient().when(gitClient.getWorkingDir()).thenReturn(repoDir);
		upgradeController = new UpgradeController(gitClient, "git@example.com/upstream.git");
	}

	@Test
	public void verifyVersionReadFromPomInWorkingDir() throws Exception {
		writePom(repoDir, "8.7.0");

		assertThat(upgradeController.determineCurrentVersion()).isEqualTo("8.7.0");
	}

	@Test
	public void verifyVersionReadFromReadmeWhenPomAbsent() throws Exception {
		writeReadme(repoDir, "Elastic Path Commerce 8.7.0 (build 8.7.0.20250730203654-29b4ea)");

		assertThat(upgradeController.determineCurrentVersion()).isEqualTo("8.7.0");
	}

	@Test
	public void verifyVersionReadFromReadmeWhenPomLacksVersionProperty() throws Exception {
		Files.write(repoDir.toPath().resolve("pom.xml"),
				"<project><properties></properties></project>".getBytes(StandardCharsets.UTF_8));
		writeReadme(repoDir, "Elastic Path Commerce 8.6.2 (build x)");

		assertThat(upgradeController.determineCurrentVersion()).isEqualTo("8.6.2");
	}

	@Test
	public void verifyExceptionThrownWhenNeitherFilePresent() {
		assertThatThrownBy(() -> upgradeController.determineCurrentVersion())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Could not determine current version");
	}

	@Test
	public void verifyConvertVersionToReleaseFormat() {
		assertThat(upgradeController.convertVersionToReleaseFormat("8.7.0")).isEqualTo("8.7.x");
		assertThat(upgradeController.convertVersionToReleaseFormat("8.6.2")).isEqualTo("8.6.x");
	}

	private static void writePom(final File dir, final String releaseVersion) throws Exception {
		final String pom = "<project>"
				+ "<properties>"
				+ "<ep.release.version>" + releaseVersion + "</ep.release.version>"
				+ "</properties>"
				+ "</project>";
		Files.write(dir.toPath().resolve("pom.xml"), pom.getBytes(StandardCharsets.UTF_8));
	}

	private static void writeReadme(final File dir, final String firstLine) throws Exception {
		final Path readme = dir.toPath().resolve("README.txt");
		Files.write(readme, (firstLine + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
	}
}
