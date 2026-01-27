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

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.UpgradeController;
import com.elasticpath.tools.smcupgrader.impl.GitClientImpl;

/**
 * Generates AI-assisted upgrade plans.
 */
public class AiPlanGenerator {
	private static final Logger LOGGER = LoggerFactory.getLogger(AiPlanGenerator.class);
	private static final String PLAN_FILE_NAME = "smc-upgrader-plan.md";

	private final UpgradePath upgradePath;
	private final UpgradeController upgradeController;

	/**
	 * Constructor.
	 *
	 * @param upgradePath        the upgrade path configuration
	 * @param upgradeController  the upgrade controller for version detection
	 */
	public AiPlanGenerator(final UpgradePath upgradePath, final UpgradeController upgradeController) {
		this.upgradePath = upgradePath;
		this.upgradeController = upgradeController;
	}

	/**
	 * Generate an upgrade plan.
	 *
	 * @param targetVersion the target version to upgrade to
	 * @param workingDir    the working directory
	 * @return true if plan was generated, false if user cancelled
	 * @throws IOException if an error occurs
	 */
	public boolean generatePlan(final String targetVersion, final File workingDir) throws IOException {
		File planFile = new File(workingDir, PLAN_FILE_NAME);

		// Determine current version first
		String currentVersion = upgradeController.convertVersionToReleaseFormat(
				upgradeController.determineCurrentVersion());
		LOGGER.info("Detected current version: {}", currentVersion);

		// Check if plan already exists
		if (planFile.exists()) {
			if (!promptForOverwrite(planFile)) {
				LOGGER.info("Keeping existing plan. To continue with the existing upgrade, run:");
				LOGGER.info("  smc-upgrader --ai:continue");
				LOGGER.info("");
				LOGGER.info("To use a different plan file name, move or rename the existing plan first.");
				return false;
			}
		}

		// Validate target version
		if (!upgradePath.isValidVersion(targetVersion)) {
			throw new IllegalArgumentException("Invalid target version: " + targetVersion
					+ ". Valid versions are: " + upgradePath.getVersions());
		}

		// Validate upgrade path
		if (!upgradePath.validateVersionPath(currentVersion, targetVersion)) {
			throw new IllegalArgumentException(
					"Cannot upgrade from " + currentVersion + " to " + targetVersion
							+ ". Current version must not be later than target version.");
		}

		// Calculate version sequence
		List<String> versionSequence = calculateVersionSequence(currentVersion, targetVersion);
		LOGGER.info("Upgrade path: {}", String.join(" -> ", versionSequence));

		// Expand steps for all version transitions
		List<AiPlanStep> allSteps = expandStepsForVersions(versionSequence);

		// Generate markdown
		String markdown = MarkdownWriter.generateMarkdown(allSteps, currentVersion, targetVersion);

		// Write to file
		Files.write(planFile.toPath(), markdown.getBytes(StandardCharsets.UTF_8));

		// Commit to git
		GitClient gitClient = createGitClient(workingDir);
		if (gitClient != null) {
			try {
				String commitMessage;
				if (currentVersion.equals(targetVersion)) {
					commitMessage = "Generated upgrade plan for latest patches";
				} else {
					commitMessage = "Generated upgrade plan from " + currentVersion + " to " + targetVersion;
				}
				commitPlanFile(gitClient, commitMessage);
				LOGGER.info("Committed plan file to git");
			} catch (RuntimeException e) {
				// Don't fail if git commit fails (e.g., signing service unavailable)
				LOGGER.warn("Could not commit plan file to git: {}", e.getMessage(), e);
			}
		}

		LOGGER.info("Generated upgrade plan: {}", planFile.getAbsolutePath());
		LOGGER.info("");
		LOGGER.info("You can review and customize the plan if needed.");
		LOGGER.info("");
		LOGGER.info("IMPORTANT: This upgrade process requires Claude Code CLI.");
		LOGGER.info("Please ensure Claude Code is installed and you have a Claude Pro account.");
		LOGGER.info("For installation instructions, visit: https://www.claude.com/product/claude-code");
		LOGGER.info("");
		LOGGER.info("To continue with the upgrade, run:");
		LOGGER.info("  smc-upgrader --ai:continue");

		return true;
	}

	/**
	 * Prompt the user to confirm overwriting an existing plan file.
	 *
	 * @param planFile the existing plan file
	 * @return true to proceed with overwrite, false to cancel
	 */
	boolean promptForOverwrite(final File planFile) throws IOException {
		LOGGER.warn("WARNING: An upgrade plan already exists at: {}", planFile.getAbsolutePath());
		LOGGER.warn("");
		LOGGER.warn("This plan may contain customizations or progress from a previous upgrade.");
		LOGGER.warn("");

		Console console = System.console();
		String response;

		if (console != null) {
			response = console.readLine("Do you want to overwrite it? [y/N]: ");
		} else {
			// Check if stdin is available (non-blocking check)
			if (System.in.available() == 0) {
				// In test/automated environments with no stdin, default to not overwriting
				LOGGER.debug("No stdin available, defaulting to not overwrite");
				return false;
			}

			// Fallback for environments without console (like IDEs)
			Scanner scanner = new Scanner(System.in);
			System.out.print("Do you want to overwrite it? [y/N]: ");
			System.out.flush();  // Ensure prompt is displayed
			response = scanner.nextLine();
		}

		return response != null && (response.equalsIgnoreCase("y") || response.equalsIgnoreCase("yes"));
	}

	/**
	 * Calculate the version sequence from current to target.
	 *
	 * @param fromVersion the starting version
	 * @param toVersion   the target version
	 * @return list of versions in sequence
	 */
	List<String> calculateVersionSequence(final String fromVersion, final String toVersion) {
		return upgradePath.getIntermediateVersions(fromVersion, toVersion);
	}

	/**
	 * Expand step templates for all version transitions.
	 *
	 * @param versionSequence the sequence of versions
	 * @return list of expanded steps
	 */
	List<AiPlanStep> expandStepsForVersions(final List<String> versionSequence) {
		List<AiPlanStep> allSteps = new ArrayList<>();

		// For each version transition
		for (int i = 0; i < versionSequence.size() - 1; i++) {
			String fromVersion = versionSequence.get(i);
			String toVersion = versionSequence.get(i + 1);

			// Determine which prompt prefix to use based on whether this is an upgrade or patch consumption
			boolean isPatchConsumption = fromVersion.equals(toVersion);
			String promptPrefixTemplate = isPatchConsumption
					? upgradePath.getPatchConsumptionPromptPrefix()
					: upgradePath.getUpgradePromptPrefix();
			String promptPrefix = (promptPrefixTemplate != null && !promptPrefixTemplate.isEmpty())
					? substituteVariables(promptPrefixTemplate, fromVersion, toVersion)
					: null;

			// Create steps for this transition (use the same steps for both upgrades and patch consumption)
			for (AiPlanStep template : upgradePath.getSteps()) {
				// Skip steps that have a version filter that doesn't match the target version
				if (template.getVersionFilter() != null && !template.getVersionFilter().equals(toVersion)) {
					continue;
				}

				AiPlanStep step = new AiPlanStep();
				step.setTitle(substituteVariables(template.getTitle(), fromVersion, toVersion));
				step.setTool(template.getTool());
				step.setStatus(template.getStatus() != null ? template.getStatus() : "not started");
				step.setCommitPlanOnCompletion(template.isCommitPlanOnCompletion());
				step.setCommitAllChangesOnCompletion(template.isCommitAllChangesOnCompletion());
				step.setVersion(toVersion);

				if (template.getValidationCommand() != null) {
					step.setValidationCommand(substituteVariables(template.getValidationCommand(), fromVersion, toVersion));
				}

				if (template.getPrompt() != null) {
					String prompt = substituteVariables(template.getPrompt(), fromVersion, toVersion);
					// Prepend the prefix if it's not null or empty
					if (promptPrefix != null && !promptPrefix.isEmpty()) {
						prompt = promptPrefix + prompt;
					}
					step.setPrompt(prompt);
				}

				allSteps.add(step);
			}
		}

		return allSteps;
	}

	/**
	 * Substitute version variables in a template string.
	 *
	 * @param template    the template string
	 * @param fromVersion the from version
	 * @param toVersion   the to version
	 * @return the string with variables substituted
	 */
	String substituteVariables(final String template, final String fromVersion, final String toVersion) {
		return template
				.replace("{FROM_VERSION}", fromVersion)
				.replace("{TO_VERSION}", toVersion);
	}

	/**
	 * Create a GitClient for the working directory.
	 *
	 * @param workingDir the working directory
	 * @return a GitClient, or null if not in a git repository
	 */
	protected GitClient createGitClient(final File workingDir) {
		try {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();
			Repository repository = builder
					.setWorkTree(workingDir)
					.readEnvironment()
					.findGitDir()
					.build();
			return new GitClientImpl(repository);
		} catch (IOException e) {
			// Not in a git repository
			LOGGER.debug("Could not create GitClient: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Commit the plan file to git.
	 *
	 * @param gitClient the git client
	 * @param message   the commit message
	 */
	private void commitPlanFile(final GitClient gitClient, final String message) {
		// Stage the plan file
		gitClient.stage(PLAN_FILE_NAME);

		// Commit the plan file
		gitClient.commit(message);

		LOGGER.debug("Committed plan file: {}", message);
	}
}
