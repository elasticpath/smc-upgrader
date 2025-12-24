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

import java.util.List;

/**
 * Utility class for writing upgrade plan steps to markdown format.
 */
public final class MarkdownWriter {

	private MarkdownWriter() {
		// Utility class
	}

	/**
	 * Generate markdown content for an upgrade plan.
	 *
	 * @param steps       the list of plan steps
	 * @param fromVersion the starting version
	 * @param toVersion   the target version
	 * @return the markdown content as a string
	 */
	public static String generateMarkdown(final List<AiPlanStep> steps, final String fromVersion, final String toVersion) {
		StringBuilder markdown = new StringBuilder();

		// Header
		markdown.append("# SMC Upgrader - AI Assist Plan\n\n");
		markdown.append("Upgrade from: ").append(fromVersion).append("\n");
		markdown.append("Upgrade to: ").append(toVersion).append("\n\n");
		markdown.append("---\n\n");

		// Steps
		for (AiPlanStep step : steps) {
			appendStep(markdown, step);
		}

		// Footer
		markdown.append("---\n\n");
		markdown.append("## Notes\n\n");
		markdown.append("Customers can edit this plan to add custom steps or modify existing ones. ");
		markdown.append("Each step will be executed in sequence when running `smc-upgrader --ai:continue`.\n\n");
		markdown.append("Steps progress through three states: `not started` -> `in progress` -> `complete`.\n\n");

		return markdown.toString();
	}

	/**
	 * Append a single step to the markdown.
	 *
	 * @param markdown the markdown builder
	 * @param step     the step to append
	 */
	private static void appendStep(final StringBuilder markdown, final AiPlanStep step) {
		// Step title
		markdown.append("## ").append(step.getTitle()).append("\n\n");

		// Metadata
		markdown.append("Tool: ").append(step.getTool()).append("\n");

		if (step.getVersion() != null && !step.getVersion().trim().isEmpty()) {
			markdown.append("Version: ").append(step.getVersion()).append("\n");
		}

		if (step.hasValidationCommand()) {
			markdown.append("Validation command: ").append(step.getValidationCommand()).append("\n");
		}

		markdown.append("Commit all changes on completion: ").append(step.isCommitAllChangesOnCompletion()).append("\n");
		markdown.append("Commit plan on completion: ").append(step.isCommitPlanOnCompletion()).append("\n");
		markdown.append("Status: ").append(step.getStatus()).append("\n\n");

		// Prompt for claude steps
		if (step.isClaudeStep() && step.getPrompt() != null && !step.getPrompt().trim().isEmpty()) {
			markdown.append(step.getPrompt()).append("\n\n");
		}
	}
}
