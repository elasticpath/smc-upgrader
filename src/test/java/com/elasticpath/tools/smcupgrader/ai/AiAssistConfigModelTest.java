/*	Copyright 2024 Elastic Path Software Inc.

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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.elasticpath.tools.smcupgrader.ai.config.AiAssistConfigModel;
import com.elasticpath.tools.smcupgrader.ai.config.AiPlanStep;
import com.elasticpath.tools.smcupgrader.ai.config.ToolTypeEnum;
import com.elasticpath.tools.smcupgrader.ai.config.VersionEntry;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AiAssistConfigModel}.
 */
class AiAssistConfigModelTest {

	@Test
	void testLoadFromResource() throws IOException {
		AiAssistConfigModel upgradePath = AiAssistConfigModel.loadFromResource();

		assertThat(upgradePath).isNotNull();
		assertThat(upgradePath.getVersions()).isNotEmpty();
		assertThat(upgradePath.getSteps()).isNotEmpty();
	}

	@Test
	void testGetIntermediateVersions_singleStep() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		List<String> intermediates = upgradePath.getIntermediateVersions("8.5.x", "8.6.x");

		assertThat(intermediates).containsExactly("8.5.x", "8.6.x");
	}

	@Test
	void testGetIntermediateVersions_multipleSteps() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		List<String> intermediates = upgradePath.getIntermediateVersions("8.5.x", "8.8.x");

		assertThat(intermediates).containsExactly("8.5.x", "8.6.x", "8.7.x", "8.8.x");
	}

	@Test
	void testGetIntermediateVersions_allVersions() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		List<String> intermediates = upgradePath.getIntermediateVersions("8.3.x", "8.8.x");

		assertThat(intermediates).containsExactly("8.3.x", "8.4.x", "8.5.x", "8.6.x", "8.7.x", "8.8.x");
	}

	@Test
	void testGetIntermediateVersions_invalidFromVersion() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		assertThatThrownBy(() -> upgradePath.getIntermediateVersions("9.0.x", "8.6.x"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid from version");
	}

	@Test
	void testGetIntermediateVersions_invalidToVersion() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		assertThatThrownBy(() -> upgradePath.getIntermediateVersions("8.5.x", "9.0.x"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid to version");
	}

	@Test
	void testGetIntermediateVersions_fromGreaterThanTo() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		assertThatThrownBy(() -> upgradePath.getIntermediateVersions("8.7.x", "8.5.x"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("From version must not be later than to version");
	}

	@Test
	void testGetIntermediateVersions_fromEqualsTo() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		List<String> intermediates = upgradePath.getIntermediateVersions("8.6.x", "8.6.x");

		// For patch consumption, from can equal to
		assertThat(intermediates).containsExactly("8.6.x", "8.6.x");
	}

	@Test
	void testValidateVersionPath_validPath() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		boolean isValid = upgradePath.validateVersionPath("8.5.x", "8.7.x");

		assertThat(isValid).isTrue();
	}

	@Test
	void testValidateVersionPath_invalidPath() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		boolean isValid = upgradePath.validateVersionPath("8.7.x", "8.5.x");

		assertThat(isValid).isFalse();
	}

	@Test
	void testValidateVersionPath_invalidVersion() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		boolean isValid = upgradePath.validateVersionPath("9.0.x", "9.1.x");

		assertThat(isValid).isFalse();
	}

	@Test
	void testIsValidVersion_validVersion() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		assertThat(upgradePath.isValidVersion("8.5.x")).isTrue();
		assertThat(upgradePath.isValidVersion("8.6.x")).isTrue();
		assertThat(upgradePath.isValidVersion("8.8.x")).isTrue();
	}

	@Test
	void testIsValidVersion_invalidVersion() {
		AiAssistConfigModel upgradePath = createTestAiAssistConfigModel();

		assertThat(upgradePath.isValidVersion("9.0.x")).isFalse();
		assertThat(upgradePath.isValidVersion("7.0.x")).isFalse();
	}

	@Test
	void testStepsLoaded() throws IOException {
		AiAssistConfigModel upgradePath = AiAssistConfigModel.loadFromResource();

		List<AiPlanStep> steps = upgradePath.getSteps();

		assertThat(steps).isNotEmpty();
		assertThat(steps).anyMatch(step -> ToolTypeEnum.SMC_UPGRADER.equals(step.getTool()));
		assertThat(steps).anyMatch(step -> ToolTypeEnum.CLAUDE.equals(step.getTool()));
	}

	/**
	 * Create a test AiAssistConfigModel with predefined versions.
	 *
	 * @return the test AiAssistConfigModel
	 */
	private AiAssistConfigModel createTestAiAssistConfigModel() {
		List<VersionEntry> versions = Arrays.asList(
				new VersionEntry("8.3.x", ""),
				new VersionEntry("8.4.x", ""),
				new VersionEntry("8.5.x", ""),
				new VersionEntry("8.6.x", ""),
				new VersionEntry("8.7.x", ""),
				new VersionEntry("8.8.x", "")
		);
		return new AiAssistConfigModel(versions, "", "", null);
	}
}
