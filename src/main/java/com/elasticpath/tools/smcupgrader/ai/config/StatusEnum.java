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
 * Enum representing the possible statuses of an upgrade plan step.
 */
public enum StatusEnum {
	/** Step has not been started yet. */
	NOT_STARTED("not started"),

	/** Step is currently being executed. */
	IN_PROGRESS("in progress"),

	/** Step has been completed. */
	COMPLETE("complete");

	private final String value;

	StatusEnum(final String value) {
		this.value = value;
	}

	/**
	 * Get the string value of this status.
	 *
	 * @return the string value
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Parse a string value into a StatusEnum (case-insensitive).
	 *
	 * @param value the string value
	 * @return the corresponding enum, or null if not recognized
	 */
	public static StatusEnum fromString(final String value) {
		if (value == null) {
			return null;
		}
		for (StatusEnum status : values()) {
			if (status.value.equalsIgnoreCase(value)) {
				return status;
			}
		}
		return null;
	}
}
