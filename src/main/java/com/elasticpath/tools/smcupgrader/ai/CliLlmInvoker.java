package com.elasticpath.tools.smcupgrader.ai;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elasticpath.tools.smcupgrader.ai.config.LlmConfig;

/**
 * Handles invocation of the configured CLI-based LLM (Claude Code by default).
 *
 * <p>The invocation command, executable, and skip-permissions argument are supplied via {@link LlmConfig},
 * which is loaded from {@code ~/.smc-upgrader.json}. With no configuration file, this reproduces the historical
 * Claude Code behavior exactly.</p>
 */
public class CliLlmInvoker {
	private static final Logger LOGGER = LoggerFactory.getLogger(CliLlmInvoker.class);
	private static final int PROMPT_MAX_DISPLAY_LENGTH = 50;
	private static final int COMMAND_MAX_DISPLAY_LENGTH = 100;

	private final File workingDir;
	private final boolean skipPermissions;
	private final LlmConfig config;

	/**
	 * Constructor.
	 *
	 * @param workingDir the working directory
	 * @param config     the LLM configuration
	 */
	public CliLlmInvoker(final File workingDir, final LlmConfig config) {
		this(workingDir, false, config);
	}

	/**
	 * Constructor.
	 *
	 * @param workingDir      the working directory
	 * @param skipPermissions whether to skip permission prompts
	 * @param config          the LLM configuration
	 */
	public CliLlmInvoker(final File workingDir, final boolean skipPermissions, final LlmConfig config) {
		this.workingDir = workingDir;
		this.skipPermissions = skipPermissions;
		this.config = config;
	}

	/**
	 * Invoke the configured CLI LLM with a prompt.
	 *
	 * @param prompt the prompt to send to the LLM
	 * @return true if the LLM completed successfully
	 * @throws IOException if an error occurs
	 */
	public boolean invoke(final String prompt) throws IOException {
		String executable = config.resolveAvailabilityTarget();

		// Check if the configured LLM CLI is available
		if (!isLlmAvailable()) {
			LOGGER.error("The configured CLI LLM executable '{}' is not available on the PATH.", executable);
			LOGGER.error("Install it, or configure a different command in ~/.smc-upgrader.json.");
			LOGGER.error("The default executable is Claude Code: https://claude.com/claude-code");
			return false;
		}

		// Check if we have console access
		boolean hasConsole = System.console() != null;
		LOGGER.debug("System console available: {}", hasConsole);
		LOGGER.debug("Executing LLM with prompt: {}", prompt.substring(0, Math.min(PROMPT_MAX_DISPLAY_LENGTH, prompt.length())) + "...");

		LOGGER.info("Executing the configured CLI LLM with prompt...");
		LOGGER.info("");

		try {
			String command = buildCommand(prompt);

			LOGGER.debug("Shell command: {}", command.substring(0, Math.min(COMMAND_MAX_DISPLAY_LENGTH, command.length())) + "...");

			// Execute through a shell to ensure proper terminal allocation
			Process process = new ProcessBuilder("/bin/sh", "-c", command)
					.directory(workingDir)
					.redirectInput(ProcessBuilder.Redirect.INHERIT)
					.redirectOutput(ProcessBuilder.Redirect.INHERIT)
					.redirectError(ProcessBuilder.Redirect.INHERIT)
					.start();

			// Wait for the LLM to complete
			int exitCode = process.waitFor();

			LOGGER.info("");
			if (exitCode == 0) {
				return true;
			} else {
				LOGGER.warn("The CLI LLM exited with code: {}", exitCode);
				return false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.error("CLI LLM execution was interrupted", e);
			return false;
		}
	}

	/**
	 * Build the final shell command for the given prompt by substituting the configured template placeholders.
	 *
	 * <p>{@code {executable}} is replaced with the executable value, {@code {permissions}} with the
	 * skip-permissions argument (or removed when not active), and {@code {prompt}} with the prompt, single-quoted
	 * and shell-escaped (single quotes escaped as {@code '\''}).</p>
	 *
	 * @param prompt the prompt to send to the LLM
	 * @return the final shell command string
	 */
	String buildCommand(final String prompt) {
		// Use single quotes for safer shell escaping (only need to escape single quotes themselves)
		String escapedPrompt = prompt.replace("'", "'\\''");
		String permissions = skipPermissions ? config.getSkipPermissionsArg() : "";

		String command = config.getCommand().replace(LlmConfig.EXECUTABLE_PLACEHOLDER, config.getExecutable());
		if (permissions.isEmpty()) {
			// Remove the placeholder and any single trailing space so the command is unchanged from a template
			// that omits the permissions argument entirely.
			command = command.replace("{permissions} ", "").replace("{permissions}", "");
		} else {
			command = command.replace("{permissions}", permissions);
		}
		command = command.replace("{prompt}", "'" + escapedPrompt + "'");

		return command;
	}

	/**
	 * Check if the configured CLI LLM executable is available on the PATH.
	 *
	 * @return true if the executable is available
	 */
	public boolean isLlmAvailable() {
		try {
			Process process = new ProcessBuilder("which", config.resolveAvailabilityTarget())
					.redirectErrorStream(true)
					.start();

			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (IOException | InterruptedException e) {
			LOGGER.debug("Error checking for CLI LLM availability", e);
			return false;
		}
	}
}
