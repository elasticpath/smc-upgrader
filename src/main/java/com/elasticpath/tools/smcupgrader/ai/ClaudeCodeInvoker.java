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
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles invocation of Claude Code CLI.
 */
public class ClaudeCodeInvoker {
	private static final Logger LOGGER = LoggerFactory.getLogger(ClaudeCodeInvoker.class);
	private static final String CLAUDE_PROMPT_FILE = ".claude-prompt.txt";

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
	 * @return true if invocation was successful
	 * @throws IOException if an error occurs
	 */
	public boolean invokeClaudeCode(final String prompt) throws IOException {
		// Write prompt to temporary file
		File promptFile = new File(workingDir, CLAUDE_PROMPT_FILE);
		Files.write(promptFile.toPath(), prompt.getBytes(StandardCharsets.UTF_8));

		LOGGER.info("Prompt file created at: {}", promptFile.getAbsolutePath());
		LOGGER.info("");
		LOGGER.info("To execute this step, run:");
		LOGGER.info("  claude --prompt-file {}", CLAUDE_PROMPT_FILE);
		LOGGER.info("");
		LOGGER.info("After Claude completes the task, run:");
		LOGGER.info("  smc-upgrader --ai:continue");

		return true;
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

	/**
	 * Clean up the prompt file.
	 *
	 * @throws IOException if an error occurs
	 */
	public void cleanupPromptFile() throws IOException {
		File promptFile = new File(workingDir, CLAUDE_PROMPT_FILE);
		if (promptFile.exists()) {
			Files.delete(promptFile.toPath());
			LOGGER.debug("Cleaned up prompt file: {}", promptFile.getAbsolutePath());
		}
	}
}
