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

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UpgradePath}.
 */
class UpgradePathTest {

	@Test
	void testLoadFromResource() throws IOException {
		UpgradePath upgradePath = UpgradePath.loadFromResource();

		assertThat(upgradePath).isNotNull();
		assertThat(upgradePath.getVersions()).isNotEmpty();
		assertThat(upgradePath.getDefaultSteps()).isNotEmpty();
	}

	@Test
	void testGetIntermediateVersions_singleStep() {
		UpgradePath upgradePath = createTestUpgradePath();

		List<String> intermediates = upgradePath.getIntermediateVersions("8.5.x", "8.6.x");

		assertThat(intermediates).containsExactly("8.5.x", "8.6.x");
	}

	@Test
	void testGetIntermediateVersions_multipleSteps() {
		UpgradePath upgradePath = createTestUpgradePath();

		List<String> intermediates = upgradePath.getIntermediateVersions("8.5.x", "8.8.x");

		assertThat(intermediates).containsExactly("8.5.x", "8.6.x", "8.7.x", "8.8.x");
	}

	@Test
	void testGetIntermediateVersions_allVersions() {
		UpgradePath upgradePath = createTestUpgradePath();

		List<String> intermediates = upgradePath.getIntermediateVersions("8.3.x", "8.8.x");

		assertThat(intermediates).containsExactly("8.3.x", "8.4.x", "8.5.x", "8.6.x", "8.7.x", "8.8.x");
	}

	@Test
	void testGetIntermediateVersions_invalidFromVersion() {
		UpgradePath upgradePath = createTestUpgradePath();

		assertThatThrownBy(() -> upgradePath.getIntermediateVersions("9.0.x", "8.6.x"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid from version");
	}

	@Test
	void testGetIntermediateVersions_invalidToVersion() {
		UpgradePath upgradePath = createTestUpgradePath();

		assertThatThrownBy(() -> upgradePath.getIntermediateVersions("8.5.x", "9.0.x"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid to version");
	}

	@Test
	void testGetIntermediateVersions_fromGreaterThanTo() {
		UpgradePath upgradePath = createTestUpgradePath();

		assertThatThrownBy(() -> upgradePath.getIntermediateVersions("8.7.x", "8.5.x"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("From version must be earlier than to version");
	}

	@Test
	void testGetIntermediateVersions_fromEqualsTo() {
		UpgradePath upgradePath = createTestUpgradePath();

		assertThatThrownBy(() -> upgradePath.getIntermediateVersions("8.6.x", "8.6.x"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("From version must be earlier than to version");
	}

	@Test
	void testValidateVersionPath_validPath() {
		UpgradePath upgradePath = createTestUpgradePath();

		boolean isValid = upgradePath.validateVersionPath("8.5.x", "8.7.x");

		assertThat(isValid).isTrue();
	}

	@Test
	void testValidateVersionPath_invalidPath() {
		UpgradePath upgradePath = createTestUpgradePath();

		boolean isValid = upgradePath.validateVersionPath("8.7.x", "8.5.x");

		assertThat(isValid).isFalse();
	}

	@Test
	void testValidateVersionPath_invalidVersion() {
		UpgradePath upgradePath = createTestUpgradePath();

		boolean isValid = upgradePath.validateVersionPath("9.0.x", "9.1.x");

		assertThat(isValid).isFalse();
	}

	@Test
	void testIsValidVersion_validVersion() {
		UpgradePath upgradePath = createTestUpgradePath();

		assertThat(upgradePath.isValidVersion("8.5.x")).isTrue();
		assertThat(upgradePath.isValidVersion("8.6.x")).isTrue();
		assertThat(upgradePath.isValidVersion("8.8.x")).isTrue();
	}

	@Test
	void testIsValidVersion_invalidVersion() {
		UpgradePath upgradePath = createTestUpgradePath();

		assertThat(upgradePath.isValidVersion("9.0.x")).isFalse();
		assertThat(upgradePath.isValidVersion("7.0.x")).isFalse();
	}

	@Test
	void testDefaultStepsLoaded() throws IOException {
		UpgradePath upgradePath = UpgradePath.loadFromResource();

		List<AiPlanStep> steps = upgradePath.getDefaultSteps();

		assertThat(steps).isNotEmpty();
		assertThat(steps).anyMatch(step -> "smc-upgrader".equals(step.getTool()));
		assertThat(steps).anyMatch(step -> "claude".equals(step.getTool()));
	}

	/**
	 * Create a test UpgradePath with predefined versions.
	 *
	 * @return the test UpgradePath
	 */
	private UpgradePath createTestUpgradePath() {
		List<String> versions = Arrays.asList("8.3.x", "8.4.x", "8.5.x", "8.6.x", "8.7.x", "8.8.x");
		return new UpgradePath(versions, null);
	}
}
