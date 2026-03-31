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

package com.elasticpath.tools.smcupgrader.ai.config;

/**
 * Model class representing a single step in an AI-assisted upgrade plan.
 */
public class AiPlanStep {
	private String title;
	private ToolTypeEnum tool;
	private String version;
	private String versionFilter;
	private String validationCommand;
	private StatusEnum status;
	private String prompt;
	private boolean commitAllChangesOnCompletion;
	private boolean commitPlanOnCompletion;
	private boolean allowManualValidation = true;

	/**
	 * Default constructor for JSON deserialization.
	 */
	public AiPlanStep() {
	}

	/**
	 * Constructor for creating a plan step.
	 *
	 * @param title             the step title
	 * @param tool              the tool to use
	 * @param validationCommand optional validation command
	 * @param status            the step status (not started, in progress, or complete)
	 * @param prompt            optional prompt for claude steps
	 */
	public AiPlanStep(final String title, final ToolTypeEnum tool,
					  final String validationCommand, final StatusEnum status, final String prompt) {
		this.title = title;
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
	 * Get the tool type.
	 *
	 * @return the tool type
	 */
	public ToolTypeEnum getTool() {
		return tool;
	}

	/**
	 * Set the tool type.
	 *
	 * @param tool the tool type
	 */
	public void setTool(final ToolTypeEnum tool) {
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
	 * Get the version filter. If set, this step is only included in plans targeting this version.
	 *
	 * @return the version filter, or null if not filtered
	 */
	public String getVersionFilter() {
		return versionFilter;
	}

	/**
	 * Set the version filter. If set, this step is only included in plans targeting this version.
	 *
	 * @param versionFilter the version filter
	 */
	public void setVersionFilter(final String versionFilter) {
		this.versionFilter = versionFilter;
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
	 * @return the status (not started, in progress, or complete)
	 */
	public StatusEnum getStatus() {
		return status;
	}

	/**
	 * Set the step status.
	 *
	 * @param status the status (not started, in progress, or complete)
	 */
	public void setStatus(final StatusEnum status) {
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
	 * Check if all changes should be committed when this step is completed.
	 *
	 * @return true if all changes should be committed on completion
	 */
	public boolean isCommitAllChangesOnCompletion() {
		return commitAllChangesOnCompletion;
	}

	/**
	 * Set whether all changes should be committed when this step is completed.
	 *
	 * @param commitAllChangesOnCompletion true to commit all changes on completion
	 */
	public void setCommitAllChangesOnCompletion(final boolean commitAllChangesOnCompletion) {
		this.commitAllChangesOnCompletion = commitAllChangesOnCompletion;
	}

	/**
	 * Check if the plan file should be committed when this step is completed.
	 *
	 * @return true if the plan file should be committed on completion
	 */
	public boolean isCommitPlanOnCompletion() {
		return commitPlanOnCompletion;
	}

	/**
	 * Set whether the plan file should be committed when this step is completed.
	 *
	 * @param commitPlanOnCompletion true to commit the plan file on completion
	 */
	public void setCommitPlanOnCompletion(final boolean commitPlanOnCompletion) {
		this.commitPlanOnCompletion = commitPlanOnCompletion;
	}

	/**
	 * Check if manual validation is allowed for this step.
	 * When false, the "Verify that this step is complete" option is not presented to the user.
	 *
	 * @return true if manual validation is allowed (default), false otherwise
	 */
	public boolean isAllowManualValidation() {
		return allowManualValidation;
	}

	/**
	 * Set whether manual validation is allowed for this step.
	 *
	 * @param allowManualValidation false to hide the "Verify that this step is complete" option
	 */
	public void setAllowManualValidation(final boolean allowManualValidation) {
		this.allowManualValidation = allowManualValidation;
	}

	/**
	 * Check if this step is complete.
	 *
	 * @return true if status is "complete"
	 */
	public boolean isComplete() {
		return StatusEnum.COMPLETE.equals(status);
	}

	/**
	 * Check if this step is in progress.
	 *
	 * @return true if status is "in progress"
	 */
	public boolean isInProgress() {
		return StatusEnum.IN_PROGRESS.equals(status);
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
