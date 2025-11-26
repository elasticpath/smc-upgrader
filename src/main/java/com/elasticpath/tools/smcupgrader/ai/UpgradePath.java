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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

/**
 * Model for upgrade path configuration, including valid versions and default step templates.
 */
public class UpgradePath {
	private List<String> versions;
	private List<AiPlanStep> defaultSteps;

	/**
	 * Default constructor for JSON deserialization.
	 */
	public UpgradePath() {
	}

	/**
	 * Constructor.
	 *
	 * @param versions     list of valid version strings
	 * @param defaultSteps list of default step templates
	 */
	public UpgradePath(final List<String> versions, final List<AiPlanStep> defaultSteps) {
		this.versions = versions;
		this.defaultSteps = defaultSteps;
	}

	/**
	 * Load the upgrade path configuration from the classpath resource.
	 *
	 * @return the loaded UpgradePath
	 * @throws IOException if the configuration file cannot be loaded
	 */
	public static UpgradePath loadFromResource() throws IOException {
		try (InputStream inputStream = UpgradePath.class.getResourceAsStream("/ai-assist-config.json")) {
			if (inputStream == null) {
				throw new IOException("Could not find ai-assist-config.json in classpath");
			}

			try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
				Gson gson = new Gson();
				return gson.fromJson(reader, UpgradePath.class);
			}
		}
	}

	/**
	 * Get the list of intermediate versions between from and to versions.
	 *
	 * @param fromVersion the starting version (e.g., "8.5.x")
	 * @param toVersion   the target version (e.g., "8.7.x")
	 * @return list of intermediate versions including from and to, in order
	 * @throws IllegalArgumentException if versions are invalid or not in sequence
	 */
	public List<String> getIntermediateVersions(final String fromVersion, final String toVersion) {
		int fromIndex = versions.indexOf(fromVersion);
		int toIndex = versions.indexOf(toVersion);

		if (fromIndex == -1) {
			throw new IllegalArgumentException("Invalid from version: " + fromVersion);
		}
		if (toIndex == -1) {
			throw new IllegalArgumentException("Invalid to version: " + toVersion);
		}
		if (fromIndex >= toIndex) {
			throw new IllegalArgumentException(
					"From version must be earlier than to version. From: " + fromVersion + ", To: " + toVersion);
		}

		// Return all versions from fromIndex to toIndex (inclusive)
		return new ArrayList<>(versions.subList(fromIndex, toIndex + 1));
	}

	/**
	 * Validate that an upgrade path exists from fromVersion to toVersion.
	 *
	 * @param fromVersion the starting version
	 * @param toVersion   the target version
	 * @return true if the path is valid
	 */
	public boolean validateVersionPath(final String fromVersion, final String toVersion) {
		try {
			getIntermediateVersions(fromVersion, toVersion);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Get the list of valid versions.
	 *
	 * @return the versions list
	 */
	public List<String> getVersions() {
		return versions;
	}

	/**
	 * Set the list of valid versions.
	 *
	 * @param versions the versions list
	 */
	public void setVersions(final List<String> versions) {
		this.versions = versions;
	}

	/**
	 * Get the list of default step templates.
	 *
	 * @return the default steps
	 */
	public List<AiPlanStep> getDefaultSteps() {
		return defaultSteps;
	}

	/**
	 * Set the list of default step templates.
	 *
	 * @param defaultSteps the default steps
	 */
	public void setDefaultSteps(final List<AiPlanStep> defaultSteps) {
		this.defaultSteps = defaultSteps;
	}

	/**
	 * Check if a version is in the valid versions list.
	 *
	 * @param version the version to check
	 * @return true if the version is valid
	 */
	public boolean isValidVersion(final String version) {
		return versions != null && versions.contains(version);
	}
}
