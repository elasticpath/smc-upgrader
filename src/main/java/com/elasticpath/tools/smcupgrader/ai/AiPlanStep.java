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

/**
 * Model class representing a single step in an AI-assisted upgrade plan.
 */
public class AiPlanStep {
	private String title;
	private String task;
	private String tool;
	private String version;
	private String validationCommand;
	private String status;
	private String prompt;

	/**
	 * Default constructor for JSON deserialization.
	 */
	public AiPlanStep() {
	}

	/**
	 * Constructor for creating a plan step.
	 *
	 * @param title             the step title
	 * @param task              the task description
	 * @param tool              the tool to use (smc-upgrader or claude)
	 * @param validationCommand optional validation command
	 * @param status            the step status (incomplete or complete)
	 * @param prompt            optional prompt for claude steps
	 */
	public AiPlanStep(final String title, final String task, final String tool,
					  final String validationCommand, final String status, final String prompt) {
		this.title = title;
		this.task = task;
		this.tool = tool;
		this.validationCommand = validationCommand;
		this.status = status;
		this.prompt = prompt;
	}

	/**
	 * Get the step title.
	 *
	 * @return the title
	 */
	public String getTitle() {
		return title;
	}

	/**
	 * Set the step title.
	 *
	 * @param title the title
	 */
	public void setTitle(final String title) {
		this.title = title;
	}

	/**
	 * Get the task description.
	 *
	 * @return the task
	 */
	public String getTask() {
		return task;
	}

	/**
	 * Set the task description.
	 *
	 * @param task the task
	 */
	public void setTask(final String task) {
		this.task = task;
	}

	/**
	 * Get the tool name.
	 *
	 * @return the tool (smc-upgrader or claude)
	 */
	public String getTool() {
		return tool;
	}

	/**
	 * Set the tool name.
	 *
	 * @param tool the tool (smc-upgrader or claude)
	 */
	public void setTool(final String tool) {
		this.tool = tool;
	}

	/**
	 * Get the version.
	 *
	 * @return the version, or null if not applicable
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Set the version.
	 *
	 * @param version the version
	 */
	public void setVersion(final String version) {
		this.version = version;
	}

	/**
	 * Get the validation command.
	 *
	 * @return the validation command, or null if none
	 */
	public String getValidationCommand() {
		return validationCommand;
	}

	/**
	 * Set the validation command.
	 *
	 * @param validationCommand the validation command
	 */
	public void setValidationCommand(final String validationCommand) {
		this.validationCommand = validationCommand;
	}

	/**
	 * Get the step status.
	 *
	 * @return the status (incomplete or complete)
	 */
	public String getStatus() {
		return status;
	}

	/**
	 * Set the step status.
	 *
	 * @param status the status (incomplete or complete)
	 */
	public void setStatus(final String status) {
		this.status = status;
	}

	/**
	 * Get the prompt for claude steps.
	 *
	 * @return the prompt, or null if not a claude step
	 */
	public String getPrompt() {
		return prompt;
	}

	/**
	 * Set the prompt for claude steps.
	 *
	 * @param prompt the prompt
	 */
	public void setPrompt(final String prompt) {
		this.prompt = prompt;
	}

	/**
	 * Check if this is a claude tool step.
	 *
	 * @return true if tool is "claude"
	 */
	public boolean isClaudeStep() {
		return "claude".equals(tool);
	}

	/**
	 * Check if this is an smc-upgrader tool step.
	 *
	 * @return true if tool is "smc-upgrader"
	 */
	public boolean isSmcUpgraderStep() {
		return "smc-upgrader".equals(tool);
	}

	/**
	 * Check if this step is complete.
	 *
	 * @return true if status is "complete"
	 */
	public boolean isComplete() {
		return "complete".equals(status);
	}

	/**
	 * Check if this step has a validation command.
	 *
	 * @return true if validationCommand is not null and not empty
	 */
	public boolean hasValidationCommand() {
		return validationCommand != null && !validationCommand.trim().isEmpty();
	}
}
