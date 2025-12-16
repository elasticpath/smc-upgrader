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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link MarkdownParser}.
 */
class MarkdownParserTest {

	@TempDir
	File tempDir;

	@Test
	void testParsePlan_versions() {
		String markdown = "# SMC Upgrader - AI Assist Plan\n\n"
				+ "Upgrade from: 8.5.x  \n"
				+ "Upgrade to: 8.6.x  \n\n";

		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getFromVersion()).isEqualTo("8.5.x");
		assertThat(plan.getToVersion()).isEqualTo("8.6.x");
	}

	@Test
	void testParsePlan_singleStep() {
		String markdown = "# SMC Upgrader - AI Assist Plan\n\n"
				+ "Upgrade from: 8.5.x  \n"
				+ "Upgrade to: 8.6.x  \n\n"
				+ "---\n\n"
				+ "## Git merge from 8.5.x to 8.6.x\n\n"
				+ "Tool: smc-upgrader  \n"
				+ "Status: incomplete\n\n";

		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getSteps()).hasSize(1);
		AiPlanStep step = plan.getSteps().get(0);
		assertThat(step.getTitle()).isEqualTo("Git merge from 8.5.x to 8.6.x");
		assertThat(step.getTool()).isEqualTo("smc-upgrader");
		assertThat(step.getStatus()).isEqualTo("incomplete");
		assertThat(step.getValidationCommand()).isNull();
	}

	@Test
	void testParsePlan_stepWithValidation() {
		String markdown = "# SMC Upgrader - AI Assist Plan\n\n"
				+ "## Test Step\n\n"
				+ "Task: Test task  \n"
				+ "Tool: smc-upgrader  \n"
				+ "Validation command: git diff --check  \n"
				+ "Status: incomplete\n\n";

		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getSteps()).hasSize(1);
		AiPlanStep step = plan.getSteps().get(0);
		assertThat(step.getValidationCommand()).isEqualTo("git diff --check");
	}

	@Test
	void testParsePlan_claudeStepWithPrompt() {
		String markdown = "# SMC Upgrader - AI Assist Plan\n\n"
				+ "## Resolve merge conflicts\n\n"
				+ "Task: Resolve conflicts  \n"
				+ "Tool: claude  \n"
				+ "Status: incomplete\n\n"
				+ "Please resolve all merge conflicts in the codebase.\n\n"
				+ "Focus on maintaining compatibility with existing code.\n\n";

		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getSteps()).hasSize(1);
		AiPlanStep step = plan.getSteps().get(0);
		assertThat(step.getTitle()).isEqualTo("Resolve merge conflicts");
		assertThat(step.getTool()).isEqualTo("claude");
		assertThat(step.getPrompt()).contains("Please resolve all merge conflicts");
		assertThat(step.getPrompt()).contains("Focus on maintaining compatibility");
	}

	@Test
	void testParsePlan_multipleSteps() {
		String markdown = "# SMC Upgrader - AI Assist Plan\n\n"
				+ "Upgrade from: 8.5.x  \n"
				+ "Upgrade to: 8.6.x  \n\n"
				+ "---\n\n"
				+ "## Step 1\n\n"
				+ "Task: Task 1  \n"
				+ "Tool: smc-upgrader  \n"
				+ "Status: complete\n\n"
				+ "## Step 2\n\n"
				+ "Task: Task 2  \n"
				+ "Tool: claude  \n"
				+ "Status: incomplete\n\n"
				+ "Prompt for step 2\n\n"
				+ "## Step 3\n\n"
				+ "Task: Task 3  \n"
				+ "Tool: smc-upgrader  \n"
				+ "Status: incomplete\n\n";

		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getSteps()).hasSize(3);
		assertThat(plan.getSteps().get(0).getTitle()).isEqualTo("Step 1");
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo("complete");
		assertThat(plan.getSteps().get(1).getTitle()).isEqualTo("Step 2");
		assertThat(plan.getSteps().get(1).getPrompt()).isEqualTo("Prompt for step 2");
		assertThat(plan.getSteps().get(2).getTitle()).isEqualTo("Step 3");
	}

	@Test
	void testParsePlan_skipsNotesSection() {
		String markdown = "# SMC Upgrader - AI Assist Plan\n\n"
				+ "## Step 1\n\n"
				+ "Task: Task 1  \n"
				+ "Tool: smc-upgrader  \n"
				+ "Status: incomplete\n\n"
				+ "---\n\n"
				+ "## Notes\n\n"
				+ "This is a note section that should not be parsed as a step.\n\n";

		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getSteps()).hasSize(1);
		assertThat(plan.getSteps().get(0).getTitle()).isEqualTo("Step 1");
	}

	@Test
	void testParsePlanFile() throws IOException {
		File planFile = new File(tempDir, "test-plan.md");
		String markdown = "# SMC Upgrader - AI Assist Plan\n\n"
				+ "Upgrade from: 8.5.x  \n"
				+ "Upgrade to: 8.6.x  \n\n"
				+ "## Test Step\n\n"
				+ "Task: Test  \n"
				+ "Tool: smc-upgrader  \n"
				+ "Status: incomplete\n\n";

		Files.write(planFile.toPath(), markdown.getBytes(StandardCharsets.UTF_8));

		PlanDocument plan = MarkdownParser.parsePlanFile(planFile);

		assertThat(plan.getFromVersion()).isEqualTo("8.5.x");
		assertThat(plan.getToVersion()).isEqualTo("8.6.x");
		assertThat(plan.getSteps()).hasSize(1);
	}

	@Test
	void testParsePlan_roundTrip() throws IOException {
		// Generate a plan, write it to markdown, then parse it back
		AiPlanStep step1 = new AiPlanStep();
		step1.setTitle("Git merge from 8.5.x to 8.6.x");
		step1.setTool("smc-upgrader");
		step1.setStatus("incomplete");

		AiPlanStep step2 = new AiPlanStep();
		step2.setTitle("Resolve conflicts");
		step2.setTool("claude");
		step2.setValidationCommand("git diff --check");
		step2.setStatus("incomplete");
		step2.setPrompt("Please resolve merge conflicts");

		java.util.List<AiPlanStep> steps = java.util.Arrays.asList(step1, step2);
		String markdown = MarkdownWriter.generateMarkdown(steps, "8.5.x", "8.6.x");

		// Parse it back
		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getFromVersion()).isEqualTo("8.5.x");
		assertThat(plan.getToVersion()).isEqualTo("8.6.x");
		assertThat(plan.getSteps()).hasSize(2);

		AiPlanStep parsedStep1 = plan.getSteps().get(0);
		assertThat(parsedStep1.getTitle()).isEqualTo("Git merge from 8.5.x to 8.6.x");
		assertThat(parsedStep1.getTool()).isEqualTo("smc-upgrader");
		assertThat(parsedStep1.getStatus()).isEqualTo("incomplete");

		AiPlanStep parsedStep2 = plan.getSteps().get(1);
		assertThat(parsedStep2.getTitle()).isEqualTo("Resolve conflicts");
		assertThat(parsedStep2.getTool()).isEqualTo("claude");
		assertThat(parsedStep2.getValidationCommand()).isEqualTo("git diff --check");
		assertThat(parsedStep2.getStatus()).isEqualTo("incomplete");
		assertThat(parsedStep2.getPrompt()).isEqualTo("Please resolve merge conflicts");
	}
}
