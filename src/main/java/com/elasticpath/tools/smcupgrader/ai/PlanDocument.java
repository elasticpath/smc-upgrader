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

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parsed upgrade plan document.
 */
public class PlanDocument {
	private String fromVersion;
	private String toVersion;
	private boolean skipPermissions;
	private final List<AiPlanStep> steps;

	/**
	 * Constructor.
	 */
	public PlanDocument() {
		this.steps = new ArrayList<>();
	}

	/**
	 * Get the from version.
	 *
	 * @return the from version
	 */
	public String getFromVersion() {
		return fromVersion;
	}

	/**
	 * Set the from version.
	 *
	 * @param fromVersion the from version
	 */
	public void setFromVersion(final String fromVersion) {
		this.fromVersion = fromVersion;
	}

	/**
	 * Get the to version.
	 *
	 * @return the to version
	 */
	public String getToVersion() {
		return toVersion;
	}

	/**
	 * Set the to version.
	 *
	 * @param toVersion the to version
	 */
	public void setToVersion(final String toVersion) {
		this.toVersion = toVersion;
	}

	/**
	 * Check if skip permissions is enabled.
	 *
	 * @return true if skip permissions is enabled
	 */
	public boolean isSkipPermissions() {
		return skipPermissions;
	}

	/**
	 * Set whether to skip permissions.
	 *
	 * @param skipPermissions true to skip permissions
	 */
	public void setSkipPermissions(final boolean skipPermissions) {
		this.skipPermissions = skipPermissions;
	}

	/**
	 * Get all steps.
	 *
	 * @return the list of steps
	 */
	public List<AiPlanStep> getSteps() {
		return steps;
	}

	/**
	 * Add a step to the plan.
	 *
	 * @param step the step to add
	 */
	public void addStep(final AiPlanStep step) {
		this.steps.add(step);
	}

	/**
	 * Find the next incomplete step.
	 *
	 * @return the next incomplete step, or null if all steps are complete
	 */
	public AiPlanStep findNextIncompleteStep() {
		for (AiPlanStep step : steps) {
			if (!step.isComplete()) {
				return step;
			}
		}
		return null;
	}

	/**
	 * Find the first step that is in progress.
	 *
	 * @return the first in-progress step, or null if none are in progress
	 */
	public AiPlanStep findInProgressStep() {
		for (AiPlanStep step : steps) {
			if (step.isInProgress()) {
				return step;
			}
		}
		return null;
	}

	/**
	 * Check if all steps are complete.
	 *
	 * @return true if all steps are complete
	 */
	public boolean isAllStepsComplete() {
		return findNextIncompleteStep() == null;
	}

	/**
	 * Count completed steps.
	 *
	 * @return the number of completed steps
	 */
	public int countCompletedSteps() {
		int count = 0;
		for (AiPlanStep step : steps) {
			if (step.isComplete()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Get total number of steps.
	 *
	 * @return the total number of steps
	 */
	public int getTotalSteps() {
		return steps.size();
	}
}
