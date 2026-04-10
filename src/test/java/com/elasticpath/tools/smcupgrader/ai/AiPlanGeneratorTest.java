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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.elasticpath.tools.smcupgrader.GitClient;
import com.elasticpath.tools.smcupgrader.UpgradeController;

import com.elasticpath.tools.smcupgrader.ai.config.AiAssistConfigModel;
import com.elasticpath.tools.smcupgrader.ai.config.AiPlanStep;
import com.elasticpath.tools.smcupgrader.ai.config.StatusEnum;
import com.elasticpath.tools.smcupgrader.ai.config.ToolTypeEnum;
import com.elasticpath.tools.smcupgrader.ai.config.VersionEntry;

/**
 * Tests for {@link AiPlanGenerator}.
 */
class AiPlanGeneratorTest {

	@TempDir
	File tempDir;

	@Mock
	private UpgradeController upgradeController;

	private AiAssistConfigModel upgradePath;
	private AiPlanGenerator generator;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);

		// Create test upgrade path
		List<VersionEntry> versions = Arrays.asList(
				new VersionEntry("8.5.x", ""),
				new VersionEntry("8.6.x", ""),
				new VersionEntry("8.7.x", ""),
				new VersionEntry("8.8.x", "")
		);
		List<AiPlanStep> steps = Arrays.asList(
				createStep("Git merge from {FROM_VERSION} to {TO_VERSION}", "smc-upgrader", null),
				createStep("Resolve {TO_VERSION} merge conflicts", "claude", "git diff --check")
		);
		upgradePath = new AiAssistConfigModel(versions, "", "", steps);

		// Create generator that doesn't perform Git operations in tests
		generator = new AiPlanGenerator(upgradePath, upgradeController) {
			@Override
			protected GitClient createGitClient(final File workingDir) {
				// Return null to prevent Git operations during tests
				return null;
			}
		};
	}

	@AfterEach
	void tearDown() {
		// Clean up plan file after each test
		File planFile = new File(tempDir, "smc-upgrader-plan.md");
		if (planFile.exists()) {
			planFile.delete();
		}
	}

	@Test
	void testGeneratePlan_singleVersionUpgrade() throws IOException {
		when(upgradeController.determineCurrentVersion()).thenReturn("8.5.0");
		when(upgradeController.convertVersionToReleaseFormat("8.5.0")).thenReturn("8.5.x");

		boolean result = generator.generatePlan("8.6.x", tempDir);

		assertThat(result).isTrue();

		File planFile = new File(tempDir, "smc-upgrader-plan.md");
		assertThat(planFile).exists();

		String content = new String(Files.readAllBytes(planFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(content).contains("# SMC Upgrader - AI Assist Plan");
		assertThat(content).contains("Upgrade from: 8.5.x");
		assertThat(content).contains("Upgrade to: 8.6.x");
		assertThat(content).contains("## Git merge from 8.5.x to 8.6.x");
		assertThat(content).contains("## Resolve 8.6.x merge conflicts");
		assertThat(content).contains("Tool: smc-upgrader");
		assertThat(content).contains("Tool: claude");
	}

	@Test
	void testGeneratePlan_multiVersionUpgrade() throws IOException {
		when(upgradeController.determineCurrentVersion()).thenReturn("8.5.0");
		when(upgradeController.convertVersionToReleaseFormat("8.5.0")).thenReturn("8.5.x");

		boolean result = generator.generatePlan("8.7.x", tempDir);

		assertThat(result).isTrue();

		File planFile = new File(tempDir, "smc-upgrader-plan.md");
		String content = new String(Files.readAllBytes(planFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);

		// Should have steps for 8.5.x -> 8.6.x
		assertThat(content).contains("## Git merge from 8.5.x to 8.6.x");
		assertThat(content).contains("## Resolve 8.6.x merge conflicts");

		// And steps for 8.6.x -> 8.7.x
		assertThat(content).contains("## Git merge from 8.6.x to 8.7.x");
		assertThat(content).contains("## Resolve 8.7.x merge conflicts");
	}

	@Test
	void testGeneratePlan_invalidTargetVersion() {
		when(upgradeController.determineCurrentVersion()).thenReturn("8.5.0");
		when(upgradeController.convertVersionToReleaseFormat("8.5.0")).thenReturn("8.5.x");

		assertThatThrownBy(() -> generator.generatePlan("9.0.x", tempDir))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid target version");
	}

	@Test
	void testGeneratePlan_downgradePath() {
		when(upgradeController.determineCurrentVersion()).thenReturn("8.7.0");
		when(upgradeController.convertVersionToReleaseFormat("8.7.0")).thenReturn("8.7.x");

		assertThatThrownBy(() -> generator.generatePlan("8.5.x", tempDir))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Current version must not be later than target version");
	}

	@Test
	void testCalculateVersionSequence() {
		List<String> sequence = generator.calculateVersionSequence("8.5.x", "8.7.x");

		assertThat(sequence).containsExactly("8.5.x", "8.6.x", "8.7.x");
	}

	@Test
	void testExpandStepsForVersions() {
		List<String> versions = Arrays.asList("8.5.x", "8.6.x", "8.7.x");

		List<AiPlanStep> steps = generator.expandStepsForVersions(versions);

		// Should have 2 steps per transition, and we have 2 transitions (8.5->8.6, 8.6->8.7)
		assertThat(steps).hasSize(4);

		// Check first transition
		assertThat(steps.get(0).getTitle()).isEqualTo("Git merge from 8.5.x to 8.6.x");
		assertThat(steps.get(1).getTitle()).isEqualTo("Resolve 8.6.x merge conflicts");

		// Check second transition
		assertThat(steps.get(2).getTitle()).isEqualTo("Git merge from 8.6.x to 8.7.x");
		assertThat(steps.get(3).getTitle()).isEqualTo("Resolve 8.7.x merge conflicts");
	}

	@Test
	void testExpandStepsForVersions_withVersionFilter() {
		// Create upgrade path with a step that has versionFilter set to "8.7.x"
		List<VersionEntry> versions = Arrays.asList(
				new VersionEntry("8.5.x", ""),
				new VersionEntry("8.6.x", ""),
				new VersionEntry("8.7.x", "")
		);
		AiPlanStep filteredStep = createStep("Jakarta Migration for {TO_VERSION}", "claude", null);
		filteredStep.setVersionFilter("8.7.x");

		List<AiPlanStep> stepsWithFilter = Arrays.asList(
				createStep("Git merge from {FROM_VERSION} to {TO_VERSION}", "smc-upgrader", null),
				filteredStep
		);
		AiAssistConfigModel upgradePathWithFilter = new AiAssistConfigModel(versions, "", "", stepsWithFilter);
		AiPlanGenerator generatorWithFilter = new AiPlanGenerator(upgradePathWithFilter, upgradeController) {
			@Override
			protected GitClient createGitClient(final File workingDir) {
				return null;
			}
		};

		List<AiPlanStep> steps = generatorWithFilter.expandStepsForVersions(Arrays.asList("8.5.x", "8.6.x", "8.7.x"));

		// Should have:
		// - 8.5.x -> 8.6.x: 1 step (Git merge only, filtered step excluded because toVersion is 8.6.x)
		// - 8.6.x -> 8.7.x: 2 steps (Git merge + Jakarta Migration because toVersion is 8.7.x)
		assertThat(steps).hasSize(3);

		// Check first transition (8.5.x -> 8.6.x) - only merge step
		assertThat(steps.get(0).getTitle()).isEqualTo("Git merge from 8.5.x to 8.6.x");

		// Check second transition (8.6.x -> 8.7.x) - both steps included
		assertThat(steps.get(1).getTitle()).isEqualTo("Git merge from 8.6.x to 8.7.x");
		assertThat(steps.get(2).getTitle()).isEqualTo("Jakarta Migration for 8.7.x");
	}

	@Test
	void testExpandStepsForVersions_withVersionFilter_greaterThan() {
		// filter ">8.6.x" should include steps for 8.7.x and 8.8.x transitions, but not 8.6.x
		AiPlanStep filteredStep = createStep("Special step for {TO_VERSION}", "claude", null);
		filteredStep.setVersionFilter(">8.6.x");

		AiAssistConfigModel config = new AiAssistConfigModel(
				Arrays.asList(
						new VersionEntry("8.5.x", ""),
						new VersionEntry("8.6.x", ""),
						new VersionEntry("8.7.x", ""),
						new VersionEntry("8.8.x", "")),
				"", "",
				Arrays.asList(createStep("Git merge to {TO_VERSION}", "smc-upgrader", null), filteredStep));
		AiPlanGenerator gen = new AiPlanGenerator(config, upgradeController) {
			@Override
			protected GitClient createGitClient(final File workingDir) {
				return null;
			}
		};

		List<AiPlanStep> steps = gen.expandStepsForVersions(Arrays.asList("8.5.x", "8.6.x", "8.7.x", "8.8.x"));

		// 8.5->8.6: 1 step (merge only); 8.6->8.7: 2 steps; 8.7->8.8: 2 steps
		assertThat(steps).hasSize(5);
		assertThat(steps.get(0).getTitle()).isEqualTo("Git merge to 8.6.x");
		assertThat(steps.get(1).getTitle()).isEqualTo("Git merge to 8.7.x");
		assertThat(steps.get(2).getTitle()).isEqualTo("Special step for 8.7.x");
		assertThat(steps.get(3).getTitle()).isEqualTo("Git merge to 8.8.x");
		assertThat(steps.get(4).getTitle()).isEqualTo("Special step for 8.8.x");
	}

	@Test
	void testExpandStepsForVersions_withVersionFilter_lessThan() {
		// filter "<8.7.x" should include steps for 8.6.x transition only
		AiPlanStep filteredStep = createStep("Special step for {TO_VERSION}", "claude", null);
		filteredStep.setVersionFilter("<8.7.x");

		AiAssistConfigModel config = new AiAssistConfigModel(
				Arrays.asList(
						new VersionEntry("8.5.x", ""),
						new VersionEntry("8.6.x", ""),
						new VersionEntry("8.7.x", ""),
						new VersionEntry("8.8.x", "")),
				"", "",
				Arrays.asList(createStep("Git merge to {TO_VERSION}", "smc-upgrader", null), filteredStep));
		AiPlanGenerator gen = new AiPlanGenerator(config, upgradeController) {
			@Override
			protected GitClient createGitClient(final File workingDir) {
				return null;
			}
		};

		List<AiPlanStep> steps = gen.expandStepsForVersions(Arrays.asList("8.5.x", "8.6.x", "8.7.x", "8.8.x"));

		// 8.5->8.6: 2 steps; 8.6->8.7: 1 step (merge only); 8.7->8.8: 1 step (merge only)
		assertThat(steps).hasSize(4);
		assertThat(steps.get(0).getTitle()).isEqualTo("Git merge to 8.6.x");
		assertThat(steps.get(1).getTitle()).isEqualTo("Special step for 8.6.x");
		assertThat(steps.get(2).getTitle()).isEqualTo("Git merge to 8.7.x");
		assertThat(steps.get(3).getTitle()).isEqualTo("Git merge to 8.8.x");
	}

	@Test
	void testExpandStepsForVersions_withVersionFilter_greaterThanOrEqual() {
		// filter ">=8.7.x" should include steps for 8.7.x and 8.8.x transitions, but not 8.6.x
		AiPlanStep filteredStep = createStep("Special step for {TO_VERSION}", "claude", null);
		filteredStep.setVersionFilter(">=8.7.x");

		AiAssistConfigModel config = new AiAssistConfigModel(
				Arrays.asList(
						new VersionEntry("8.5.x", ""),
						new VersionEntry("8.6.x", ""),
						new VersionEntry("8.7.x", ""),
						new VersionEntry("8.8.x", "")),
				"", "",
				Arrays.asList(createStep("Git merge to {TO_VERSION}", "smc-upgrader", null), filteredStep));
		AiPlanGenerator gen = new AiPlanGenerator(config, upgradeController) {
			@Override
			protected GitClient createGitClient(final File workingDir) {
				return null;
			}
		};

		List<AiPlanStep> steps = gen.expandStepsForVersions(Arrays.asList("8.5.x", "8.6.x", "8.7.x", "8.8.x"));

		// 8.5->8.6: 1 step (merge only); 8.6->8.7: 2 steps; 8.7->8.8: 2 steps
		assertThat(steps).hasSize(5);
		assertThat(steps.get(0).getTitle()).isEqualTo("Git merge to 8.6.x");
		assertThat(steps.get(1).getTitle()).isEqualTo("Git merge to 8.7.x");
		assertThat(steps.get(2).getTitle()).isEqualTo("Special step for 8.7.x");
		assertThat(steps.get(3).getTitle()).isEqualTo("Git merge to 8.8.x");
		assertThat(steps.get(4).getTitle()).isEqualTo("Special step for 8.8.x");
	}

	@Test
	void testExpandStepsForVersions_withVersionFilter_lessThanOrEqual() {
		// filter "<=8.7.x" should include steps for 8.6.x and 8.7.x transitions, but not 8.8.x
		AiPlanStep filteredStep = createStep("Special step for {TO_VERSION}", "claude", null);
		filteredStep.setVersionFilter("<=8.7.x");

		AiAssistConfigModel config = new AiAssistConfigModel(
				Arrays.asList(
						new VersionEntry("8.5.x", ""),
						new VersionEntry("8.6.x", ""),
						new VersionEntry("8.7.x", ""),
						new VersionEntry("8.8.x", "")),
				"", "",
				Arrays.asList(createStep("Git merge to {TO_VERSION}", "smc-upgrader", null), filteredStep));
		AiPlanGenerator gen = new AiPlanGenerator(config, upgradeController) {
			@Override
			protected GitClient createGitClient(final File workingDir) {
				return null;
			}
		};

		List<AiPlanStep> steps = gen.expandStepsForVersions(Arrays.asList("8.5.x", "8.6.x", "8.7.x", "8.8.x"));

		// 8.5->8.6: 2 steps; 8.6->8.7: 2 steps; 8.7->8.8: 1 step (merge only)
		assertThat(steps).hasSize(5);
		assertThat(steps.get(0).getTitle()).isEqualTo("Git merge to 8.6.x");
		assertThat(steps.get(1).getTitle()).isEqualTo("Special step for 8.6.x");
		assertThat(steps.get(2).getTitle()).isEqualTo("Git merge to 8.7.x");
		assertThat(steps.get(3).getTitle()).isEqualTo("Special step for 8.7.x");
		assertThat(steps.get(4).getTitle()).isEqualTo("Git merge to 8.8.x");
	}

	@Test
	void testSubstituteVariables() {
		String template = "Upgrade from {FROM_VERSION} to {TO_VERSION}";

		String result = generator.substituteVariables(template, "8.5.x", "8.6.x", "");

		assertThat(result).isEqualTo("Upgrade from 8.5.x to 8.6.x");
	}

	@Test
	void testSubstituteVariables_multipleOccurrences() {
		String template = "{FROM_VERSION} and {TO_VERSION} and {FROM_VERSION} again";

		String result = generator.substituteVariables(template, "8.5.x", "8.6.x", "");

		assertThat(result).isEqualTo("8.5.x and 8.6.x and 8.5.x again");
	}

	@Test
	void testSubstituteVariables_upgradeNotesUrl() {
		String template = "See upgrade notes at {UPGRADE_NOTES_URL} for details.";

		String result = generator.substituteVariables(template, "8.5.x", "8.6.x", "https://example.com/notes");

		assertThat(result).isEqualTo("See upgrade notes at https://example.com/notes for details.");
	}

	@Test
	void testSubstituteVariables_templateVariable() {
		Map<String, String> templates = new HashMap<>();
		templates.put("ACCELERATOR_NAME", "my-accelerator");
		upgradePath.setTemplates(templates);

		String template = "See the accelerator at https://example.com/{ACCELERATOR_NAME}/README.";

		String result = generator.substituteVariables(template, "8.5.x", "8.6.x", "");

		assertThat(result).isEqualTo("See the accelerator at https://example.com/my-accelerator/README.");
	}

	@Test
	void testSubstituteVariables_templateValueContainsSystemPlaceholders() {
		Map<String, String> templates = new HashMap<>();
		templates.put("ACCELERATOR_LINK", "https://example.com/accelerators/{TO_VERSION}");
		upgradePath.setTemplates(templates);

		String template = "See {ACCELERATOR_LINK} for details.";

		String result = generator.substituteVariables(template, "8.5.x", "8.6.x", "");

		assertThat(result).isEqualTo("See https://example.com/accelerators/8.6.x for details.");
	}

	@Test
	void testSubstituteVariables_templateAndSystemPlaceholders() {
		Map<String, String> templates = new HashMap<>();
		templates.put("CUSTOM_NAME", "my-project");
		upgradePath.setTemplates(templates);

		String template = "Upgrading {CUSTOM_NAME} from {FROM_VERSION} to {TO_VERSION}.";

		String result = generator.substituteVariables(template, "8.5.x", "8.6.x", "");

		assertThat(result).isEqualTo("Upgrading my-project from 8.5.x to 8.6.x.");
	}

	@Test
	void testGeneratePlan_existingFile_userDeclines() throws IOException {
		when(upgradeController.determineCurrentVersion()).thenReturn("8.5.0");
		when(upgradeController.convertVersionToReleaseFormat("8.5.0")).thenReturn("8.5.x");

		// Create existing plan file
		File planFile = new File(tempDir, "smc-upgrader-plan.md");
		Files.write(planFile.toPath(), "Existing plan".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		// Create generator that always returns false for overwrite
		AiPlanGenerator testGenerator = new AiPlanGenerator(upgradePath, upgradeController) {
			@Override
			boolean promptForOverwrite(File file) throws IOException {
				return false;
			}

			@Override
			protected GitClient createGitClient(final File workingDir) {
				// Return null to prevent Git operations during tests
				return null;
			}
		};

		boolean result = testGenerator.generatePlan("8.6.x", tempDir);

		assertThat(result).isFalse();
		// Original file should be unchanged
		String content = new String(Files.readAllBytes(planFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(content).isEqualTo("Existing plan");
	}

	@Test
	void testGeneratePlan_existingFile_userAccepts() throws IOException {
		when(upgradeController.determineCurrentVersion()).thenReturn("8.5.0");
		when(upgradeController.convertVersionToReleaseFormat("8.5.0")).thenReturn("8.5.x");

		// Create existing plan file
		File planFile = new File(tempDir, "smc-upgrader-plan.md");
		Files.write(planFile.toPath(), "Existing plan".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		// Create generator that always returns true for overwrite
		AiPlanGenerator testGenerator = new AiPlanGenerator(upgradePath, upgradeController) {
			@Override
			boolean promptForOverwrite(File file) throws IOException {
				return true;
			}

			@Override
			protected GitClient createGitClient(final File workingDir) {
				// Return null to prevent Git operations during tests
				return null;
			}
		};

		boolean result = testGenerator.generatePlan("8.6.x", tempDir);

		assertThat(result).isTrue();
		// File should be overwritten
		String content = new String(Files.readAllBytes(planFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
		assertThat(content).contains("# SMC Upgrader - AI Assist Plan");
		assertThat(content).doesNotContain("Existing plan");
	}

	/**
	 * Helper method to create a test step.
	 */
	private AiPlanStep createStep(String title, String tool, String validationCommand) {
		AiPlanStep step = new AiPlanStep();
		step.setTitle(title);
		step.setTool(ToolTypeEnum.fromString(tool));
		step.setStatus(StatusEnum.NOT_STARTED);
		step.setValidationCommand(validationCommand);
		if ("claude".equals(tool)) {
			step.setPrompt("Test prompt for {FROM_VERSION} to {TO_VERSION}");
		}
		return step;
	}
}
