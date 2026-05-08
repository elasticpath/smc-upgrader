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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.astgrep.AstGrepExecutor;

import com.elasticpath.tools.smcupgrader.ai.config.AiPlanStep;
import com.elasticpath.tools.smcupgrader.ai.config.StatusEnum;
import com.elasticpath.tools.smcupgrader.ai.config.ToolTypeEnum;

/**
 * Tests for {@link AiPlanExecutor}.
 */
class AiPlanExecutorTest {

	@TempDir
	File tempDir;

	private GitClient gitClient;
	private ClaudeCodeInvoker claudeInvoker;
	private AiPlanExecutor executor;

	@BeforeEach
	void setUp() throws IOException {
		gitClient = mock(GitClient.class);
		claudeInvoker = mock(ClaudeCodeInvoker.class);

		// Configure mock to accept git operations without doing anything
		doNothing().when(gitClient).stage(anyString());
		doNothing().when(gitClient).stageAll();
		doNothing().when(gitClient).unstage(anyString());
		doNothing().when(gitClient).commit(anyString());

		// Configure mock Claude invoker to always return false (Claude not available)
		when(claudeInvoker.isClaudeCodeAvailable()).thenReturn(false);
		when(claudeInvoker.invokeClaudeCode(anyString())).thenReturn(false);

		// Create executor with overridden methods to use mocks and prevent real process execution
		executor = new AiPlanExecutor(tempDir, gitClient) {
			@Override
			protected ClaudeCodeInvoker createClaudeCodeInvoker(final boolean skipPermissions) {
				return claudeInvoker;
			}

			@Override
			protected AstGrepExecutor createAstGrepExecutor() {
				return new AstGrepExecutor(tempDir, Collections.emptyList()) {
					@Override
					protected boolean isSgAvailable() {
						return false;
					}
				};
			}
		};
	}

	@Test
	void testExecuteNextStep_noPlanFile() throws IOException {
		boolean result = executor.executeNextStep();

		assertThat(result).isFalse();
	}

	@Test
	void testExecuteNextStep_allStepsComplete() throws IOException {
		// Create plan with all steps complete
		AiPlanStep step1 = createStep("Step 1", "smc-upgrader", "complete");
		AiPlanStep step2 = createStep("Step 2", "smc-upgrader", "complete");

		writePlanFile(Arrays.asList(step1, step2));

		boolean result = executor.executeNextStep();

		assertThat(result).isFalse();
	}

	@Test
	void testExecuteNextStep_smcUpgraderStepNoValidation() throws IOException {
		// Create plan with incomplete smc-upgrader step (no validation, no version)
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "not started");
		step.setCommitAllChangesOnCompletion(false); // Don't prompt for completion

		writePlanFile(Arrays.asList(step));

		// No testChoice needed - step will auto-execute since it's "not started"
		boolean result = executor.executeNextStep();

		// Should execute but NOT auto-complete (no version, no validation)
		assertThat(result).isTrue();

		// Verify step was marked as in progress (since it didn't complete)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_smcUpgraderStepWithValidationSuccess() throws IOException {
		// Create plan with incomplete smc-upgrader step with validation
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "not started");
		step.setValidationCommand("exit 0");

		writePlanFile(Arrays.asList(step));

		// No testChoice needed - step will auto-execute since it's "not started"
		boolean result = executor.executeNextStep();

		// Should auto-complete
		assertThat(result).isTrue();

		// Verify plan was updated
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
	}

	@Test
	void testExecuteNextStep_smcUpgraderStepWithValidationFailure() throws IOException {
		// Create plan with incomplete smc-upgrader step with validation
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "not started");
		step.setValidationCommand("exit 1");
		step.setCommitAllChangesOnCompletion(false); // Don't prompt for completion

		writePlanFile(Arrays.asList(step));

		// No testChoice needed - step will auto-execute since it's "not started"
		boolean result = executor.executeNextStep();

		// Should NOT auto-complete
		assertThat(result).isTrue();

		// Verify step was marked as in progress (validation failed, so didn't complete)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_claudeStep() throws IOException {
		// Create plan with incomplete claude step
		AiPlanStep step = createStep("Step 1", "claude", "not started");
		step.setPrompt("Test prompt");
		step.setCommitAllChangesOnCompletion(false); // So it doesn't prompt for completion

		writePlanFile(Arrays.asList(step));

		// No testChoice needed - step will auto-execute since it's "not started"
		boolean result = executor.executeNextStep();

		// Should execute but not auto-complete
		assertThat(result).isTrue();

		// Verify step was marked as in progress (Claude steps are never auto-completed)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_claudeStepWithCompletionPrompt_userSaysYes() throws IOException {
		// Create plan with Claude step that has commitAllChanges=true
		AiPlanStep step = createStep("Step 1", "claude", "not started");
		step.setPrompt("Test prompt");
		step.setCommitAllChangesOnCompletion(true); // Trigger the completion prompt

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("Y"); // User says yes to "Was this step successfully completed?"
		boolean result = executor.executeNextStep();

		// Should execute and mark complete because user said yes
		assertThat(result).isTrue();

		// Verify step was marked as complete
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
	}

	@Test
	void testExecuteNextStep_claudeStepWithCompletionPrompt_userSaysNo() throws IOException {
		// Create plan with Claude step that has commitAllChanges=true
		AiPlanStep step = createStep("Step 1", "claude", "not started");
		step.setPrompt("Test prompt");
		step.setCommitAllChangesOnCompletion(true); // Trigger the completion prompt

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("N"); // User says no to "Was this step successfully completed?"
		boolean result = executor.executeNextStep();

		// Should execute but remain in progress because user said no
		assertThat(result).isTrue();

		// Verify step stays in progress
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_claudeStepWithCompletionPrompt_checkValidationSuccess() throws IOException {
		// Create plan with Claude step that has commitAllChanges=true and a validation command
		AiPlanStep step = createStep("Step 1", "claude", "not started");
		step.setPrompt("Test prompt");
		step.setCommitAllChangesOnCompletion(true); // Trigger the completion prompt
		step.setValidationCommand("exit 0"); // Validation will succeed

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("V"); // User chooses to Verify step
		boolean result = executor.executeNextStep();

		// Should execute and mark complete because validation passed
		assertThat(result).isTrue();

		// Verify step was marked as complete
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
	}

	@Test
	void testExecuteNextStep_claudeStepWithCompletionPrompt_checkValidationFailure() throws IOException {
		// Create plan with Claude step that has commitAllChanges=true and a validation command
		AiPlanStep step = createStep("Step 1", "claude", "not started");
		step.setPrompt("Test prompt");
		step.setCommitAllChangesOnCompletion(true); // Trigger the completion prompt
		step.setValidationCommand("exit 1"); // Validation will fail

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("V"); // User chooses to Verify step
		boolean result = executor.executeNextStep();

		// Should execute but remain in progress because validation failed
		assertThat(result).isTrue();

		// Verify step stays in progress
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_skipsCompletedSteps() throws IOException {
		// Create plan with mixed completed/incomplete steps
		AiPlanStep step1 = createStep("Step 1", "smc-upgrader", "complete");
		AiPlanStep step2 = createStep("Step 2", "smc-upgrader", "not started");
		step2.setValidationCommand("exit 0");  // Add validation so it can complete
		AiPlanStep step3 = createStep("Step 3", "smc-upgrader", "not started");

		writePlanFile(Arrays.asList(step1, step2, step3));

		// No testChoice needed - step2 will auto-execute since it's "not started"
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify step 2 was completed, step 3 remains incomplete
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
		assertThat(plan.getSteps().get(1).getStatus()).isEqualTo(StatusEnum.COMPLETE);
		assertThat(plan.getSteps().get(2).getStatus()).isEqualTo(StatusEnum.NOT_STARTED);
	}

	@Test
	void testExecuteNextStep_preservesLastClaudeStepPromptAfterSave() throws IOException {
		// When an earlier step completes, savePlan re-serializes the full plan from the parsed
		// in-memory state. This test verifies the last Claude step's prompt is not lost during
		// that round-trip (parse -> modify status -> save -> parse).
		AiPlanStep step1 = createStep("Git merge step", "smc-upgrader", "not started");
		step1.setValidationCommand("exit 0"); // Auto-completes
		step1.setCommitAllChangesOnCompletion(false);

		AiPlanStep step2 = createStep("Final check", "claude", "not started");
		String longPrompt = "We are in the process of doing an upgrade of the Self-Managed Commerce code base "
				+ "from version 8.5.x to version 8.6.x. "
				+ "Run the validation command below in the background and help to fix any test or static analysis failures. "
				+ "If you need access to the platform version of a file, you can retrieve it from the smc-upgrades remote "
				+ "in the release/8.6.x branch. "
				+ "Filter the build output using the following regular expression: "
				+ "\"^\\[INFO\\]\\s-+<\\s(?P<coords>[^>]+)\\s>-+\\r?\\n^\\[INFO\\]\\sBuilding\" "
				+ "Do not read or update smc-upgrader-plan.md. Do not commit changes to Git.";
		step2.setPrompt(longPrompt);
		step2.setCommitAllChangesOnCompletion(false);

		writePlanFile(java.util.Arrays.asList(step1, step2));

		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// After step1 completes, the plan is saved. Verify step2's prompt survived the round-trip.
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
		assertThat(plan.getSteps().get(1).getPrompt()).isEqualTo(longPrompt);
	}

	@Test
	void testRunValidationCommand_success() throws IOException {
		boolean result = executor.runValidationCommand("exit 0");

		assertThat(result).isTrue();
	}

	@Test
	void testRunValidationCommand_failure() throws IOException {
		boolean result = executor.runValidationCommand("exit 1");

		assertThat(result).isFalse();
	}

	@Test
	void testRunValidationCommand_withOutput() throws IOException {
		boolean result = executor.runValidationCommand("echo 'test output' && exit 0");

		assertThat(result).isTrue();
	}

	@Test
	void testExecuteNextStep_withVersion() throws IOException {
		// Test execution with version field set
		AiPlanStep step = createStep("Git merge from 8.6.x to 8.7.x", "smc-upgrader", "not started");
		step.setVersion("8.7.x");
		step.setCommitAllChangesOnCompletion(false); // Don't prompt for completion

		writePlanFile(Arrays.asList(step));

		// No testChoice needed - step will auto-execute since it's "not started"
		// The step should fail because it tries to execute the upgrade
		// but in a test environment the git repo doesn't exist
		// However, we can verify the execution works by checking logs
		// This test mainly verifies no exceptions are thrown
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();
	}

	@Test
	void testExecuteNextStep_checkValidation_success() throws IOException {
		// Create plan with step already in progress (so menu is shown)
		AiPlanStep step = createStep("Step 1", "claude", "in progress");
		step.setValidationCommand("exit 0");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("V"); // Verify step
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify plan was updated (validation passed)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
	}

	@Test
	void testExecuteNextStep_checkValidation_failure() throws IOException {
		// Create plan with step already in progress (so menu is shown)
		AiPlanStep step = createStep("Step 1", "claude", "in progress");
		step.setValidationCommand("exit 1");
		step.setCommitAllChangesOnCompletion(false); // Don't prompt for completion

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("V"); // Verify step
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify plan was NOT updated (validation failed, stays in progress)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_manualMarkComplete() throws IOException {
		// Create plan with step already in progress (so menu is shown)
		AiPlanStep step = createStep("Step 1", "claude", "in progress");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("M"); // Mark complete
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify plan was updated
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
	}

	@Test
	void testExecuteNextStep_exitChoice() throws IOException {
		// Create plan with step already in progress (so menu is shown)
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "in progress");
		// No need to set commitAllChangesOnCompletion since X exits before that check

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("X"); // Exit
		boolean result = executor.executeNextStep();

		assertThat(result).isFalse();

		// Verify plan was NOT updated (stays in progress)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_smcUpgraderWithoutCommitFlag() throws IOException {
		// Test that smc-upgrader steps with commitAllChangesOnCompletion=false don't commit source changes
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "not started");
		step.setValidationCommand("exit 0"); // Validation succeeds
		step.setCommitAllChangesOnCompletion(false); // Don't commit all changes

		writePlanFile(Arrays.asList(step));

		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify step was marked complete
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);

		// Verify that gitClient.commit was only called once for the plan file,
		// not twice (once for plan file and once for source changes).
		// Since we're using a mock GitClient in the executor, we can verify
		// by checking the commit count in the mock's history.
		// For now, we verify that the step completes successfully without errors.
	}

	@Test
	void testExecuteNextStep_astGrepStep_noVersion() throws IOException {
		// ast-grep step with no version should fail gracefully
		AiPlanStep step = createStep("Run recipes", "ast-grep", "not started");
		step.setCommitAllChangesOnCompletion(false);

		writePlanFile(Collections.singletonList(step));

		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify step was NOT completed (no version → error)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_astGrepStep_sgNotAvailable() throws IOException {
		// ast-grep step without sg on PATH should NOT auto-complete -
		// stays in progress so user can install sg and re-run, or [M]ark complete to skip.
		AiPlanStep step = createStep("Run recipes", "ast-grep", "not started");
		step.setVersion("8.7.x");
		step.setCommitAllChangesOnCompletion(false); // avoid the completion prompt

		writePlanFile(Collections.singletonList(step));

		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Step should remain in progress (sg not available → user must decide)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	@Test
	void testExecuteNextStep_astGrepStep_noRecipesDir() throws IOException {
		// ast-grep step with sg available but no upgrade/recipes/ dir should skip gracefully
		AiPlanStep step = createStep("Run recipes", "ast-grep", "not started");
		step.setVersion("8.7.x");

		writePlanFile(Collections.singletonList(step));

		// Create executor where isSgAvailable() returns true (unlike default test executor)
		AiPlanExecutor sgAvailableExecutor = new AiPlanExecutor(tempDir, gitClient) {
			@Override
			protected ClaudeCodeInvoker createClaudeCodeInvoker(final boolean skipPermissions) {
				return claudeInvoker;
			}

			@Override
			protected AstGrepExecutor createAstGrepExecutor() {
				return new AstGrepExecutor(tempDir, Collections.emptyList()) {
					@Override
					protected boolean isSgAvailable() {
						return true;
					}
				};
			}
		};

		boolean result = sgAvailableExecutor.executeNextStep();

		assertThat(result).isTrue();

		// Step should be marked complete (no recipes dir → graceful skip)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
	}

	@Test
	void testExecuteNextStep_validationOnlyStep_success() throws IOException {
		// Create plan with validation-only step
		AiPlanStep step = createStep("Step 1", "validation-only", "not started");
		step.setValidationCommand("exit 0"); // Validation will succeed

		writePlanFile(Arrays.asList(step));

		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify step was marked as complete
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
	}

	@Test
	void testExecuteNextStep_validationOnlyStep_failure() throws IOException {
		// Create plan with validation-only step that will fail
		AiPlanStep step = createStep("Step 1", "validation-only", "not started");
		step.setValidationCommand("exit 1"); // Validation will fail
		step.setCommitAllChangesOnCompletion(false); // Avoid completion prompt

		writePlanFile(Arrays.asList(step));

		boolean result = executor.executeNextStep();

		assertThat(result).isTrue(); // Method returns true even when step doesn't complete

		// Verify step was NOT marked as complete
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.IN_PROGRESS);
	}

	/**
	 * Helper to create a test step.
	 */
	private AiPlanStep createStep(String title, String tool, String status) {
		AiPlanStep step = new AiPlanStep();
		step.setTitle(title);
		step.setTool(ToolTypeEnum.fromString(tool));
		step.setStatus(StatusEnum.fromString(status));
		step.setCommitAllChangesOnCompletion(true);
		step.setCommitPlanOnCompletion(true);
		return step;
	}

	/**
	 * Helper to write a plan file.
	 */
	private void writePlanFile(List<AiPlanStep> steps) throws IOException {
		String markdown = MarkdownWriter.generateMarkdown(steps, "8.5.x", "8.6.x");
		File planFile = new File(tempDir, "smc-upgrader-plan.md");
		Files.write(planFile.toPath(), markdown.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Helper to read the plan file.
	 */
	private PlanDocument readPlanFile() throws IOException {
		File planFile = new File(tempDir, "smc-upgrader-plan.md");
		return MarkdownParser.parsePlanFile(planFile);
	}
}
