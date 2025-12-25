/*	Copyright 2025 Elastic Path Software Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.elasticpath.tools.smcupgrader.ai;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles invocation of Claude Code CLI.
 */
public class ClaudeCodeInvoker {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClaudeCodeInvoker.class);
	private static final int PROMPT_MAX_DISPLAY_LENGTH = 50;
	private static final int COMMAND_MAX_DISPLAY_LENGTH = 100;
	public static final String CLAUDE_CLI_PARAMETERS = "--dangerously-skip-permissions --model sonnet";

	private final File workingDir;

	/**
	 * Constructor.
	 *
	 * @param workingDir the working directory
	 */
	public ClaudeCodeInvoker(final File workingDir) {
		this.workingDir = workingDir;
	}

	/**
	 * Invoke Claude Code with a prompt.
	 *
	 * @param prompt the prompt to send to Claude
	 * @return true if Claude completed successfully
	 * @throws IOException if an error occurs
	 */
	public boolean invokeClaudeCode(final String prompt) throws IOException {
		// Check if Claude Code is available
		if (!isClaudeCodeAvailable()) {
			LOGGER.error("Claude Code CLI is not available.");
			LOGGER.error("Please install Claude Code from: https://claude.com/claude-code");
			return false;
		}

		// Check if we have console access
		boolean hasConsole = System.console() != null;
		LOGGER.debug("System console available: {}", hasConsole);
		LOGGER.debug("Executing command: claude {}", prompt.substring(0, Math.min(PROMPT_MAX_DISPLAY_LENGTH, prompt.length())) + "...");

		LOGGER.info("Executing Claude Code with prompt...");
		LOGGER.info("");

		try {
			// Execute Claude Code through shell to ensure proper terminal allocation
			// Use single quotes for safer shell escaping (only need to escape single quotes themselves)
			String escapedPrompt = prompt.replace("'", "'\\''");
			String command = "claude " + CLAUDE_CLI_PARAMETERS + " '" + escapedPrompt + "'";

			LOGGER.debug("Shell command: {}", command.substring(0, Math.min(COMMAND_MAX_DISPLAY_LENGTH, command.length())) + "...");

			Process process = new ProcessBuilder("/bin/sh", "-c", command)
					.directory(workingDir)
					.redirectInput(ProcessBuilder.Redirect.INHERIT)
					.redirectOutput(ProcessBuilder.Redirect.INHERIT)
					.redirectError(ProcessBuilder.Redirect.INHERIT)
					.start();

			// Wait for Claude to complete
			int exitCode = process.waitFor();

			LOGGER.info("");
			if (exitCode == 0) {
				return true;
			} else {
				LOGGER.warn("Claude Code exited with code: {}", exitCode);
				return false;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.error("Claude Code execution was interrupted", e);
			return false;
		}
	}

	/**
	 * Check if Claude Code CLI is available.
	 *
	 * @return true if claude command is available
	 */
	public boolean isClaudeCodeAvailable() {
		try {
			Process process = new ProcessBuilder("which", "claude")
					.redirectErrorStream(true)
					.start();

			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (IOException | InterruptedException e) {
			LOGGER.debug("Error checking for Claude Code availability", e);
			return false;
		}
	}

	/**
	 * Get the version of Claude Code CLI.
	 *
	 * @return the version string, or null if unavailable
	 */
	public String getClaudeCodeVersion() {
		try {
			Process process = new ProcessBuilder("claude", "--version")
					.redirectErrorStream(true)
					.start();

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String version = reader.readLine();
				process.waitFor();
				return version;
			}
		} catch (IOException | InterruptedException e) {
			LOGGER.debug("Error getting Claude Code version", e);
			return null;
		}
	}

}
