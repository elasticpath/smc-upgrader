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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PlanDocument}.
 */
class PlanDocumentTest {

	private PlanDocument plan;

	@BeforeEach
	void setUp() {
		plan = new PlanDocument();
		plan.setFromVersion("8.5.x");
		plan.setToVersion("8.6.x");
	}

	@Test
	void testVersions() {
		assertThat(plan.getFromVersion()).isEqualTo("8.5.x");
		assertThat(plan.getToVersion()).isEqualTo("8.6.x");
	}

	@Test
	void testAddStep() {
		AiPlanStep step = createStep("Step 1", "not started");

		plan.addStep(step);

		assertThat(plan.getSteps()).hasSize(1);
		assertThat(plan.getSteps().get(0)).isEqualTo(step);
	}

	@Test
	void testFindNextIncompleteStep_noSteps() {
		assertThat(plan.findNextIncompleteStep()).isNull();
	}

	@Test
	void testFindNextIncompleteStep_allComplete() {
		plan.addStep(createStep("Step 1", "complete"));
		plan.addStep(createStep("Step 2", "complete"));

		assertThat(plan.findNextIncompleteStep()).isNull();
	}

	@Test
	void testFindNextIncompleteStep_firstIncomplete() {
		AiPlanStep step1 = createStep("Step 1", "not started");
		plan.addStep(step1);
		plan.addStep(createStep("Step 2", "not started"));

		assertThat(plan.findNextIncompleteStep()).isEqualTo(step1);
	}

	@Test
	void testFindNextIncompleteStep_secondIncomplete() {
		plan.addStep(createStep("Step 1", "complete"));
		AiPlanStep step2 = createStep("Step 2", "not started");
		plan.addStep(step2);
		plan.addStep(createStep("Step 3", "not started"));

		assertThat(plan.findNextIncompleteStep()).isEqualTo(step2);
	}

	@Test
	void testIsAllStepsComplete_empty() {
		assertThat(plan.isAllStepsComplete()).isTrue();
	}

	@Test
	void testIsAllStepsComplete_allComplete() {
		plan.addStep(createStep("Step 1", "complete"));
		plan.addStep(createStep("Step 2", "complete"));

		assertThat(plan.isAllStepsComplete()).isTrue();
	}

	@Test
	void testIsAllStepsComplete_someIncomplete() {
		plan.addStep(createStep("Step 1", "complete"));
		plan.addStep(createStep("Step 2", "not started"));

		assertThat(plan.isAllStepsComplete()).isFalse();
	}

	@Test
	void testCountCompletedSteps_empty() {
		assertThat(plan.countCompletedSteps()).isEqualTo(0);
	}

	@Test
	void testCountCompletedSteps_mixed() {
		plan.addStep(createStep("Step 1", "complete"));
		plan.addStep(createStep("Step 2", "not started"));
		plan.addStep(createStep("Step 3", "complete"));

		assertThat(plan.countCompletedSteps()).isEqualTo(2);
	}

	@Test
	void testGetTotalSteps() {
		plan.addStep(createStep("Step 1", "complete"));
		plan.addStep(createStep("Step 2", "not started"));

		assertThat(plan.getTotalSteps()).isEqualTo(2);
	}

	/**
	 * Helper to create a test step.
	 */
	private AiPlanStep createStep(String title, String status) {
		AiPlanStep step = new AiPlanStep();
		step.setTitle(title);
		step.setTool("smc-upgrader");
		step.setStatus(status);
		return step;
	}
}
