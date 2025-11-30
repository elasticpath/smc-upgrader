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
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.UpgradeController;
import com.elasticpath.tools.smcupgrader.impl.GitClientImpl;

/**
 * Executes upgrade plan steps.
 */
public class AiPlanExecutor {
	private static final Logger LOGGER = LoggerFactory.getLogger(AiPlanExecutor.class);
	private static final String PLAN_FILE_NAME = "smc-upgrader-plan.md";
	private static final String DEFAULT_UPSTREAM_REPO_URL = "git@code.elasticpath.com:ep-commerce/ep-commerce.git";

	private final File workingDir;
	private final ClaudeCodeInvoker claudeInvoker;
	private final UpgradeController upgradeController;
	private final GitClient gitClient;
	private String testChoice; // For testing only - bypasses interactive prompt

	/**
	 * Constructor.
	 *
	 * @param workingDir the working directory
	 */
	public AiPlanExecutor(final File workingDir) {
		this.workingDir = workingDir;
		this.claudeInvoker = new ClaudeCodeInvoker(workingDir);
		this.upgradeController = new UpgradeController(workingDir, DEFAULT_UPSTREAM_REPO_URL);
		this.gitClient = createGitClient(workingDir);
	}

	/**
	 * Package-private constructor for testing.
	 *
	 * @param workingDir     the working directory
	 * @param gitClient      the git client (for testing)
	 * @param claudeInvoker  the claude invoker (for testing)
	 */
	AiPlanExecutor(final File workingDir, final GitClient gitClient, final ClaudeCodeInvoker claudeInvoker) {
		this.workingDir = workingDir;
		this.claudeInvoker = claudeInvoker;
		this.upgradeController = new UpgradeController(workingDir, DEFAULT_UPSTREAM_REPO_URL);
		this.gitClient = gitClient;
	}

	/**
	 * Execute the next step in the plan.
	 *
	 * @return true if a step was executed, false if all steps are complete
	 * @throws IOException if an error occurs
	 */
	public boolean executeNextStep() throws IOException {
		File planFile = new File(workingDir, PLAN_FILE_NAME);

		if (!planFile.exists()) {
			LOGGER.error("No upgrade plan found at: {}", planFile.getAbsolutePath());
			LOGGER.error("Run 'smc-upgrader --ai:start <version>' to generate a plan first.");
			return false;
		}

		LOGGER.info("Reading plan from: {}", planFile.getAbsolutePath());
		LOGGER.info("");

		// Parse the plan
		PlanDocument plan = MarkdownParser.parsePlanFile(planFile);

		// Find next incomplete step
		AiPlanStep nextStep = plan.findNextIncompleteStep();

		if (nextStep == null) {
			LOGGER.info("All steps complete! Upgrade finished.");
			LOGGER.info("");
			LOGGER.info("Completed {} of {} steps.", plan.countCompletedSteps(), plan.getTotalSteps());
			return false;
		}

		// Show next step info
		LOGGER.info("Next step: {}", nextStep.getTitle());
		LOGGER.info("  Task: {}", nextStep.getTask());
		LOGGER.info("  Tool: {}", nextStep.getTool());
		if (nextStep.hasValidationCommand()) {
			LOGGER.info("  Validation command: {}", nextStep.getValidationCommand());
		}
		LOGGER.info("  Status: {}", nextStep.getStatus());
		LOGGER.info("");

		// Show menu and get user choice
		String choice = promptUserChoice();

		// Handle user choice
		boolean stepCompleted = false;

		switch (choice.toUpperCase()) {
			case "E":
				// Execute the step
				if (nextStep.isSmcUpgraderStep()) {
					stepCompleted = executeSmcUpgraderStep(nextStep);
				} else if (nextStep.isClaudeStep()) {
					stepCompleted = executeClaudeStep(nextStep);
				} else {
					LOGGER.error("Unknown tool: {}", nextStep.getTool());
					return false;
				}
				break;

			case "C":
				// Check validation
				stepCompleted = checkStepValidation(nextStep);
				break;

			case "M":
				// Mark complete
				stepCompleted = true;
				break;

			case "X":
				// Exit
				LOGGER.info("Exiting. Run 'smc-upgrader --ai:continue' to return to this step.");
				return false;

			default:
				LOGGER.error("Invalid choice: {}", choice);
				return false;
		}

		// Save updated plan if step was marked complete
		if (stepCompleted) {
			// Commit all changes if configured to do so
			if (nextStep.isCommitAllChangesOnCompletion()) {
				commitAllChanges(nextStep.getTitle());
			}

			nextStep.setStatus("complete");
			savePlan(planFile, plan, nextStep);
			LOGGER.info("");
			LOGGER.info("Step marked as complete.");
		}

		// Exit after action
		LOGGER.info("");
		LOGGER.info("Exiting. Run 'smc-upgrader --ai:continue' to continue with the next step.");

		return true;
	}

	/**
	 * Set the test choice (for testing only).
	 *
	 * @param choice the choice to use instead of prompting
	 */
	void setTestChoice(final String choice) {
		this.testChoice = choice;
	}

	/**
	 * Prompt the user for their choice.
	 *
	 * @return the user's choice (E, C, M, or X)
	 */
	private String promptUserChoice() {
		// If test choice is set, use it instead of prompting
		if (testChoice != null) {
			String choice = testChoice;
			testChoice = null; // Clear after use
			return choice;
		}

		LOGGER.info("What would you like to do?");
		LOGGER.info("  [E] Execute this step");
		LOGGER.info("  [C] Check if this step is complete");
		LOGGER.info("  [M] Mark this step as complete");
		LOGGER.info("  [X] Exit");
		LOGGER.info("");

		Console console = System.console();
		String response;

		if (console != null) {
			response = console.readLine("Your choice: ");
		} else {
			// Fallback for environments without console (like IDEs)
			Scanner scanner = new Scanner(System.in);
			System.out.print("Your choice: ");
			response = scanner.nextLine();
		}

		return response != null ? response.trim() : "X";
	}

	/**
	 * Check step validation by running the validation command.
	 *
	 * @param step the step to validate
	 * @return true if validation passed and step should be marked complete
	 * @throws IOException if an error occurs
	 */
	private boolean checkStepValidation(final AiPlanStep step) throws IOException {
		if (!step.hasValidationCommand()) {
			LOGGER.warn("No validation command defined for this step.");
			LOGGER.warn("Use [M] to manually mark complete or [E] to execute the step.");
			return false;
		}

		LOGGER.info("Running validation command: {}", step.getValidationCommand());
		LOGGER.info("");

		boolean validationPassed = runValidationCommand(step.getValidationCommand());

		LOGGER.info("");
		if (validationPassed) {
			LOGGER.info("Validation passed!");
			return true;
		} else {
			LOGGER.warn("Validation failed. Step remains incomplete.");
			LOGGER.warn("Review the output above and run 'smc-upgrader --ai:continue' to try again.");
			return false;
		}
	}

	/**
	 * Execute an smc-upgrader step.
	 *
	 * @param step the step to execute
	 * @return true if the step should be auto-marked complete
	 * @throws IOException if an error occurs
	 */
	private boolean executeSmcUpgraderStep(final AiPlanStep step) throws IOException {
		LOGGER.info("Executing smc-upgrader step: {}", step.getTask());

		// Get target version from step
		String targetVersion = step.getVersion();

		if (targetVersion != null && !targetVersion.trim().isEmpty()) {
			LOGGER.info("Performing upgrade to version: {}", targetVersion);

			try {
				// Execute the upgrade with standard options
				upgradeController.performUpgrade(
						targetVersion,
						false,  // doCleanWorkingDirectoryCheck
						true,  // doRevertPatches
						true,  // doMerge
						true,  // doConflictResolution
						true   // doDiffResolution
				);

				LOGGER.info("Upgrade completed successfully.");

				// Run validation if specified
				if (step.hasValidationCommand()) {
					LOGGER.info("Running validation command: {}", step.getValidationCommand());
					boolean validationPassed = runValidationCommand(step.getValidationCommand());

					if (validationPassed) {
						LOGGER.info("Validation passed.");
						return true;
					} else {
						LOGGER.warn("Validation failed. Step not marked as complete.");
						LOGGER.warn("Please resolve the issue and run 'smc-upgrader --ai:continue' again.");
						return false;
					}
				} else {
					// No validation command - auto-complete after successful upgrade
					return true;
				}
			} catch (RuntimeException e) {
				LOGGER.error("Upgrade failed", e);
				LOGGER.warn("Step not marked as complete.");
				LOGGER.warn("Please resolve the issue and run 'smc-upgrader --ai:continue' again.");
				return false;
			}
		} else {
			LOGGER.warn("No version specified for this step.");
			LOGGER.warn("Step cannot be executed automatically.");

			// Run validation if specified
			if (step.hasValidationCommand()) {
				LOGGER.info("Running validation command: {}", step.getValidationCommand());
				boolean validationPassed = runValidationCommand(step.getValidationCommand());

				if (validationPassed) {
					LOGGER.info("Validation passed.");
					return true;
				} else {
					LOGGER.warn("Validation failed. Step not marked as complete.");
					LOGGER.warn("Please resolve the issue and run 'smc-upgrader --ai:continue' again.");
					return false;
				}
			} else {
				// No validation command and can't execute - don't auto-complete
				LOGGER.warn("Please complete this step manually and mark it as complete in the plan file.");
				return false;
			}
		}
	}

	/**
	 * Execute a Claude step.
	 *
	 * @param step the step to execute
	 * @return false (Claude steps are never auto-completed, even on success)
	 * @throws IOException if an error occurs
	 */
	private boolean executeClaudeStep(final AiPlanStep step) throws IOException {
		LOGGER.info("This step requires Claude Code assistance.");
		LOGGER.info("");

		// Invoke Claude with the prompt
		if (step.getPrompt() != null && !step.getPrompt().trim().isEmpty()) {
			// Build the full prompt including validation command if present
			String fullPrompt = step.getPrompt();
			if (step.hasValidationCommand()) {
				fullPrompt += "\n\nValidation command: " + step.getValidationCommand();
			}

			boolean claudeSuccess = claudeInvoker.invokeClaudeCode(fullPrompt);

			LOGGER.info("");
			if (claudeSuccess) {
				LOGGER.info("Claude Code completed successfully.");
			} else {
				LOGGER.warn("Claude Code did not complete successfully.");
			}
		} else {
			LOGGER.warn("No prompt defined for this Claude step.");
			LOGGER.info("Please manually complete the task: {}", step.getTask());
		}

		// Claude steps are never auto-completed - user must verify and mark complete manually
		LOGGER.info("");
		LOGGER.info("Please verify the work, then run 'smc-upgrader --ai:continue' to resume or proceed to the next step.");

		return false;
	}

	/**
	 * Run a validation command.
	 *
	 * @param command the command to run
	 * @return true if the command exited with code 0
	 * @throws IOException if an error occurs
	 */
	boolean runValidationCommand(final String command) throws IOException {
		try {
			Process process = new ProcessBuilder("/bin/sh", "-c", command)
					.directory(workingDir)
					.redirectErrorStream(true)
					.start();

			// Read output
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					LOGGER.debug("Validation output: {}", line);
				}
			}

			int exitCode = process.waitFor();
			return exitCode == 0;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Validation command interrupted", e);
		}
	}

	/**
	 * Save the plan back to the file.
	 *
	 * @param planFile      the plan file
	 * @param plan          the plan document
	 * @param completedStep the step that was just completed
	 * @throws IOException if an error occurs
	 */
	private void savePlan(final File planFile, final PlanDocument plan, final AiPlanStep completedStep) throws IOException {
		String markdown = MarkdownWriter.generateMarkdown(plan.getSteps(), plan.getFromVersion(), plan.getToVersion());
		Files.write(planFile.toPath(), markdown.getBytes(StandardCharsets.UTF_8));

		// Commit to git if configured to do so
		if (completedStep.isCommitPlanOnCompletion()) {
			commitPlanFile("Mark step complete: " + completedStep.getTitle());
		}
	}

	/**
	 * Create a GitClient for the working directory.
	 *
	 * @param workingDir the working directory
	 * @return a GitClient, or null if not in a git repository
	 */
	private GitClient createGitClient(final File workingDir) {
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
	 * @param message the commit message
	 */
	private void commitPlanFile(final String message) {
		// Stage the plan file
		gitClient.stage(PLAN_FILE_NAME);

		// Commit the plan file
		gitClient.commit(message);

		LOGGER.debug("Committed plan file: {}", message);
	}

	/**
	 * Commit all changes in the working directory.
	 *
	 * @param message the commit message
	 */
	private void commitAllChanges(final String message) {
		// Stage all changes (modified, new, and deleted files)
		gitClient.stageAll();

		// Unstage the plan file - it should be committed separately
		gitClient.unstage(PLAN_FILE_NAME);

		// Commit all changes
		gitClient.commit(message);

		LOGGER.info("Committed all changes: {}", message);
	}
}
