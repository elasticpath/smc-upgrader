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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.UpgradeController;

/**
 * Tests for {@link AiPlanExecutor}.
 */
class AiPlanExecutorTest {

	@TempDir
	File tempDir;

	private UpgradeController upgradeController;
	private GitClient gitClient;
	private AiPlanExecutor executor;

	@BeforeEach
	void setUp() {
		upgradeController = mock(UpgradeController.class);
		gitClient = mock(GitClient.class);

		// Configure mock to accept git operations without doing anything
		doNothing().when(gitClient).stage(anyString());
		doNothing().when(gitClient).stageAll();
		doNothing().when(gitClient).unstage(anyString());
		doNothing().when(gitClient).commit(anyString());

		executor = new AiPlanExecutor(tempDir, gitClient);
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
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "incomplete");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("E"); // Execute the step
		boolean result = executor.executeNextStep();

		// Should execute but NOT auto-complete (no version, no validation)
		assertThat(result).isTrue();

		// Verify plan was NOT updated
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("incomplete");
	}

	@Test
	void testExecuteNextStep_smcUpgraderStepWithValidationSuccess() throws IOException {
		// Create plan with incomplete smc-upgrader step with validation
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "incomplete");
		step.setValidationCommand("exit 0");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("E"); // Execute the step
		boolean result = executor.executeNextStep();

		// Should auto-complete
		assertThat(result).isTrue();

		// Verify plan was updated
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("complete");
	}

	@Test
	void testExecuteNextStep_smcUpgraderStepWithValidationFailure() throws IOException {
		// Create plan with incomplete smc-upgrader step with validation
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "incomplete");
		step.setValidationCommand("exit 1");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("E"); // Execute the step
		boolean result = executor.executeNextStep();

		// Should NOT auto-complete
		assertThat(result).isTrue();

		// Verify plan was NOT updated
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("incomplete");
	}

	@Test
	void testExecuteNextStep_claudeStep() throws IOException {
		// Create plan with incomplete claude step
		AiPlanStep step = createStep("Step 1", "claude", "incomplete");
		step.setPrompt("Test prompt");

		writePlanFile(Arrays.asList(step));

		// Only run this test if Claude is NOT available (to avoid interactive blocking)
		ClaudeCodeInvoker invoker = new ClaudeCodeInvoker(tempDir);
		if (invoker.isClaudeCodeAvailable()) {
			// Skip test - would block on interactive prompt
			return;
		}

		executor.setTestChoice("E"); // Execute the step
		boolean result = executor.executeNextStep();

		// Should execute but not auto-complete
		assertThat(result).isTrue();

		// Verify plan was NOT updated (Claude steps are never auto-completed)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("incomplete");
	}

	@Test
	void testExecuteNextStep_skipsCompletedSteps() throws IOException {
		// Create plan with mixed completed/incomplete steps
		AiPlanStep step1 = createStep("Step 1", "smc-upgrader", "complete");
		AiPlanStep step2 = createStep("Step 2", "smc-upgrader", "incomplete");
		step2.setValidationCommand("exit 0");  // Add validation so it can complete
		AiPlanStep step3 = createStep("Step 3", "smc-upgrader", "incomplete");

		writePlanFile(Arrays.asList(step1, step2, step3));

		executor.setTestChoice("E"); // Execute the step
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify step 2 was completed, step 3 remains incomplete
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("complete");
		assertThat(plan.getSteps().get(1).getStatus()).isEqualTo("complete");
		assertThat(plan.getSteps().get(2).getStatus()).isEqualTo("incomplete");
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
		AiPlanStep step = createStep("Git merge from 8.6.x to 8.7.x", "smc-upgrader", "incomplete");
		step.setVersion("8.7.x");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("E"); // Execute the step
		// The step should fail because it tries to execute the upgrade
		// but in a test environment the git repo doesn't exist
		// However, we can verify the execution works by checking logs
		// This test mainly verifies no exceptions are thrown
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();
	}

	@Test
	void testExecuteNextStep_checkValidation_success() throws IOException {
		// Create plan with step that has validation
		AiPlanStep step = createStep("Step 1", "claude", "incomplete");
		step.setValidationCommand("exit 0");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("C"); // Check validation
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify plan was updated (validation passed)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("complete");
	}

	@Test
	void testExecuteNextStep_checkValidation_failure() throws IOException {
		// Create plan with step that has validation
		AiPlanStep step = createStep("Step 1", "claude", "incomplete");
		step.setValidationCommand("exit 1");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("C"); // Check validation
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify plan was NOT updated (validation failed)
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("incomplete");
	}

	@Test
	void testExecuteNextStep_manualMarkComplete() throws IOException {
		// Create plan with incomplete step
		AiPlanStep step = createStep("Step 1", "claude", "incomplete");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("M"); // Mark complete
		boolean result = executor.executeNextStep();

		assertThat(result).isTrue();

		// Verify plan was updated
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("complete");
	}

	@Test
	void testExecuteNextStep_exitChoice() throws IOException {
		// Create plan with incomplete step
		AiPlanStep step = createStep("Step 1", "smc-upgrader", "incomplete");

		writePlanFile(Arrays.asList(step));

		executor.setTestChoice("X"); // Exit
		boolean result = executor.executeNextStep();

		assertThat(result).isFalse();

		// Verify plan was NOT updated
		PlanDocument plan = readPlanFile();
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("incomplete");
	}

	/**
	 * Helper to create a test step.
	 */
	private AiPlanStep createStep(String title, String tool, String status) {
		AiPlanStep step = new AiPlanStep();
		step.setTitle(title);
		step.setTask("Test task");
		step.setTool(tool);
		step.setStatus(status);
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
