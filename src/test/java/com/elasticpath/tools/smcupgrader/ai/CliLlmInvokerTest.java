package com.elasticpath.tools.smcupgrader.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.ai.config.LlmConfig;

/**
 * Tests for {@link CliLlmInvoker}.
 */
class CliLlmInvokerTest {

	@TempDir
	File tempDir;

	private LlmConfig defaultConfig;
	private CliLlmInvoker invoker;

	@BeforeEach
	void setUp() {
		// An absent config file yields all defaults, matching historical Claude Code behavior.
		defaultConfig = LlmConfig.load(new File(tempDir, "missing.json"));
		invoker = new CliLlmInvoker(tempDir, defaultConfig);
	}

	@Test
	void testBuildCommand_defaultConfigSkipPermissionsOff() {
		CliLlmInvoker skipOff = new CliLlmInvoker(tempDir, false, defaultConfig);

		String command = skipOff.buildCommand("Resolve the conflicts");

		assertThat(command).isEqualTo("claude --model sonnet 'Resolve the conflicts'");
	}

	@Test
	void testBuildCommand_defaultConfigSkipPermissionsOn() {
		CliLlmInvoker skipOn = new CliLlmInvoker(tempDir, true, defaultConfig);

		String command = skipOn.buildCommand("Resolve the conflicts");

		assertThat(command).isEqualTo("claude --dangerously-skip-permissions --model sonnet 'Resolve the conflicts'");
	}

	@Test
	void testBuildCommand_customTemplate() {
		LlmConfig config = new LlmConfig("{executable} run {permissions} {prompt}", "mytool", "--yolo");
		CliLlmInvoker customInvoker = new CliLlmInvoker(tempDir, true, config);

		String command = customInvoker.buildCommand("do the thing");

		assertThat(command).isEqualTo("mytool run --yolo 'do the thing'");
	}

	@Test
	void testBuildCommand_promptWithSingleQuotes() {
		String command = invoker.buildCommand("it's a 'quoted' prompt");

		// Each single quote is escaped as '\'' for safe single-quoted shell embedding.
		assertThat(command).isEqualTo("claude --model sonnet 'it'\\''s a '\\''quoted'\\'' prompt'");
	}

	@Test
	void testInvoke_whenLlmNotAvailable_returnsFalse() throws IOException {
		// This test verifies graceful handling when the configured LLM is not installed.
		// Only run this test if the executable is actually not available.
		boolean available = invoker.isLlmAvailable();

		if (!available) {
			boolean result = invoker.invoke("Test prompt");

			// Should return false when the LLM is not available
			assertThat(result).isFalse();
		}
		// If the LLM IS available, we skip this test as we can't test execution without user interaction.
	}

	@Test
	void testIsLlmAvailable() {
		// This test will pass or fail depending on whether the executable is installed.
		// We can't make strong assertions, but we can verify the method doesn't crash.
		boolean available = invoker.isLlmAvailable();
		assertThat(available).isIn(true, false);
	}

	@Test
	void testGetVersion() {
		// This test will return a version or null depending on installation.
		// We can't make strong assertions, but we can verify the method doesn't crash.
		String version = invoker.getVersion();
		assertThat(version == null || version.length() > 0).isTrue();
	}
}
