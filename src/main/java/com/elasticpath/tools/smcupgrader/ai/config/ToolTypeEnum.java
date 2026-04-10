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

import com.google.gson.annotations.SerializedName;

/**
 * Enum representing the tool types available for upgrade plan steps.
 */
public enum ToolTypeEnum {
	/** Step executed by the smc-upgrader tool itself. */
	@SerializedName("smc-upgrader")
	SMC_UPGRADER("smc-upgrader"),

	/** Step executed by Claude Code CLI. */
	@SerializedName("claude")
	CLAUDE("claude"),

	/** Step that only runs a validation command. */
	@SerializedName("validation-only")
	VALIDATION_ONLY("validation-only");

	private final String value;

	ToolTypeEnum(final String value) {
		this.value = value;
	}

	/**
	 * Get the string value of this tool type.
	 *
	 * @return the string value
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Parse a string value into a ToolTypeEnum.
	 *
	 * @param value the string value
	 * @return the corresponding enum, or null if not recognized
	 */
	public static ToolTypeEnum fromString(final String value) {
		if (value == null) {
			return null;
		}
		for (ToolTypeEnum type : values()) {
			if (type.value.equalsIgnoreCase(value)) {
				return type;
			}
		}
		return null;
	}
}
