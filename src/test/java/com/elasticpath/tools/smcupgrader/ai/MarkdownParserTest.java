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

import com.elasticpath.tools.smcupgrader.ai.config.AiPlanStep;
import com.elasticpath.tools.smcupgrader.ai.config.StatusEnum;
import com.elasticpath.tools.smcupgrader.ai.config.ToolTypeEnum;

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
				+ "Status: not started\n\n";

		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getSteps()).hasSize(1);
		AiPlanStep step = plan.getSteps().get(0);
		assertThat(step.getTitle()).isEqualTo("Git merge from 8.5.x to 8.6.x");
		assertThat(step.getTool()).isEqualTo(ToolTypeEnum.SMC_UPGRADER);
		assertThat(step.getStatus()).isEqualTo(StatusEnum.NOT_STARTED);
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
		assertThat(step.getTool()).isEqualTo(ToolTypeEnum.CLAUDE);
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
		assertThat(plan.getSteps().get(0).getStatus()).isEqualTo(StatusEnum.COMPLETE);
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
		step1.setTool(ToolTypeEnum.SMC_UPGRADER);
		step1.setStatus(StatusEnum.NOT_STARTED);

		AiPlanStep step2 = new AiPlanStep();
		step2.setTitle("Resolve conflicts");
		step2.setTool(ToolTypeEnum.CLAUDE);
		step2.setValidationCommand("git diff --check");
		step2.setStatus(StatusEnum.NOT_STARTED);
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
		assertThat(parsedStep1.getTool()).isEqualTo(ToolTypeEnum.SMC_UPGRADER);
		assertThat(parsedStep1.getStatus()).isEqualTo(StatusEnum.NOT_STARTED);

		AiPlanStep parsedStep2 = plan.getSteps().get(1);
		assertThat(parsedStep2.getTitle()).isEqualTo("Resolve conflicts");
		assertThat(parsedStep2.getTool()).isEqualTo(ToolTypeEnum.CLAUDE);
		assertThat(parsedStep2.getValidationCommand()).isEqualTo("git diff --check");
		assertThat(parsedStep2.getStatus()).isEqualTo(StatusEnum.NOT_STARTED);
		assertThat(parsedStep2.getPrompt()).isEqualTo("Please resolve merge conflicts");
	}

	@Test
	void testParsePlan_roundTripWithRealLengthPrompt() {
		// The existing round-trip test uses a very short prompt. This test uses a long, realistic
		// prompt similar to what the actual ai-assist-config.json generates, including special
		// characters (regex patterns, URLs, markdown-sensitive chars like [, ], >) that could
		// potentially affect parsing.
		String longPrompt = "We are in the process of doing an upgrade of the Self-Managed Commerce code base "
				+ "from version 8.5.x to version 8.6.x. "
				+ "These upgrade notes may be helpful: "
				+ "https://documentation.elasticpath.com/commerce/docs/8.6.x/release-notes.html#upgrade-notes "
				+ "or https://documentation.elasticpath.com/commerce/docs/upgrade-notes.html "
				+ "(use the first URL that doesn't return 404). "
				+ "Run the validation command below in the background and help to fix any test or static analysis failures. "
				+ "If you need access to the platform version of a file, you can retrieve it from the smc-upgrades remote "
				+ "in the release/8.6.x branch. Before inspecting local build artifacts or .m2 jars, always check the "
				+ "platform version of any relevant file from the smc-upgrades remote in release/8.6.x. "
				+ "Use that as your primary reference for what changed. "
				+ "Make sure to follow coding best practices: "
				+ "https://documentation.elasticpath.com/commerce/docs/core/development/code-best-practices.html. "
				+ "Avoid removing intentional customizations in the customer's source code that affect functionality. "
				+ "The validation command is expected to take around 20 minutes to execute. "
				+ "When running Maven builds to verify changes, always add -DskipAllTests to skip tests and static analysis. "
				+ "Filter the build output using the following regular expression to periodically report on build progress: "
				+ "\"^\\[INFO\\]\\s-+<\\s(?P<coords>[^>]+)\\s>-+\\r?\\n"
				+ "^\\[INFO\\]\\sBuilding\\s(?P<module>.+?)\\s+\\S+\\s+"
				+ "\\[(?P<module_num>\\d+)\\/(?P<module_total>\\d+)\\]\\r?\\n"
				+ "^\\[INFO\\]\\s+from\\s(?P<path>\\S+)\\s*$\" "
				+ "Do not read or update smc-upgrader-plan.md. Do not commit changes to Git.";

		AiPlanStep step1 = new AiPlanStep();
		step1.setTitle("Git merge from 8.5.x to 8.6.x");
		step1.setTool(ToolTypeEnum.SMC_UPGRADER);
		step1.setVersion("8.6.x");
		step1.setStatus(StatusEnum.NOT_STARTED);
		step1.setCommitAllChangesOnCompletion(false);
		step1.setCommitPlanOnCompletion(false);

		AiPlanStep step2 = new AiPlanStep();
		step2.setTitle("Final check for unit test and static analysis failures");
		step2.setTool(ToolTypeEnum.CLAUDE);
		step2.setVersion("8.6.x");
		step2.setValidationCommand("mvn clean install -DskipITests -DskipCucumberTests");
		step2.setStatus(StatusEnum.NOT_STARTED);
		step2.setCommitAllChangesOnCompletion(true);
		step2.setCommitPlanOnCompletion(true);
		step2.setPrompt(longPrompt);

		java.util.List<AiPlanStep> steps = java.util.Arrays.asList(step1, step2);
		String markdown = MarkdownWriter.generateMarkdown(steps, "8.5.x", "8.6.x");

		PlanDocument plan = MarkdownParser.parsePlan(markdown);

		assertThat(plan.getSteps()).hasSize(2);
		AiPlanStep parsedLastStep = plan.getSteps().get(1);
		assertThat(parsedLastStep.getTitle()).isEqualTo("Final check for unit test and static analysis failures");
		assertThat(parsedLastStep.getTool()).isEqualTo(ToolTypeEnum.CLAUDE);
		assertThat(parsedLastStep.getPrompt()).isEqualTo(longPrompt);
	}
}
