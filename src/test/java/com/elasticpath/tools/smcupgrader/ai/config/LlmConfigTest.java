package com.elasticpath.tools.smcupgrader.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link LlmConfig}.
 */
class LlmConfigTest {

	@TempDir
	File tempDir;

	@Test
	void testLoad_missingFile_returnsAllDefaults() {
		LlmConfig config = LlmConfig.load(new File(tempDir, "does-not-exist.json"));

		assertThat(config.getCommand()).isEqualTo(LlmConfig.DEFAULT_COMMAND);
		assertThat(config.getExecutable()).isEqualTo(LlmConfig.DEFAULT_EXECUTABLE);
		assertThat(config.getSkipPermissionsArg()).isEqualTo(LlmConfig.DEFAULT_SKIP_PERMISSIONS_ARG);
	}

	@Test
	void testLoad_partialFile_unspecifiedFieldsFallBackToDefaults() throws IOException {
		File configFile = writeConfig("{ \"llm\": { \"executable\": \"mytool\" } }");

		LlmConfig config = LlmConfig.load(configFile);

		// Specified field is honored
		assertThat(config.getExecutable()).isEqualTo("mytool");
		// Unspecified fields fall back to defaults
		assertThat(config.getCommand()).isEqualTo(LlmConfig.DEFAULT_COMMAND);
		assertThat(config.getSkipPermissionsArg()).isEqualTo(LlmConfig.DEFAULT_SKIP_PERMISSIONS_ARG);
	}

	@Test
	void testLoad_fullOverride() throws IOException {
		File configFile = writeConfig(
				"{ \"llm\": { \"command\": \"{executable} chat {permissions} {prompt}\", "
						+ "\"executable\": \"mytool\", \"skipPermissionsArg\": \"--yolo\" } }");

		LlmConfig config = LlmConfig.load(configFile);

		assertThat(config.getCommand()).isEqualTo("{executable} chat {permissions} {prompt}");
		assertThat(config.getExecutable()).isEqualTo("mytool");
		assertThat(config.getSkipPermissionsArg()).isEqualTo("--yolo");
	}

	@Test
	void testLoad_commandMissingPromptPlaceholder_throws() throws IOException {
		File configFile = writeConfig("{ \"llm\": { \"command\": \"{executable} {permissions} --model sonnet\" } }");

		assertThatThrownBy(() -> LlmConfig.load(configFile))
				.isInstanceOf(LlmConfigException.class)
				.hasMessageContaining("{prompt}");
	}

	@Test
	void testResolveAvailabilityTarget_usesExecutableWhenPlaceholderPresent() {
		LlmConfig config = new LlmConfig("{executable} --model sonnet {prompt}", "claude", "--skip");

		assertThat(config.resolveAvailabilityTarget()).isEqualTo("claude");
	}

	@Test
	void testResolveAvailabilityTarget_usesFirstTokenWhenPlaceholderOmitted() {
		// When the {executable} placeholder is omitted, the command begins with the literal binary name.
		LlmConfig config = new LlmConfig("mytool run {permissions} {prompt}", "claude", "--skip");

		assertThat(config.resolveAvailabilityTarget()).isEqualTo("mytool");
	}

	private File writeConfig(final String json) throws IOException {
		File configFile = new File(tempDir, "config.json");
		Files.write(configFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
		return configFile;
	}
}
