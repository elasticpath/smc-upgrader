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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.Gson;

/**
 * Model for the ai-assist-config.json configuration, including valid versions and default step templates.
 */
public class AiAssistConfigModel {
	private List<VersionEntry> versions;
	private String upgradePromptPrefix;
	private String patchConsumptionPromptPrefix;
	private List<AiPlanStep> steps;

	/**
	 * Default constructor for JSON deserialization.
	 */
	public AiAssistConfigModel() {
	}

	/**
	 * Constructor.
	 *
	 * @param versions                      list of valid version entries
	 * @param upgradePromptPrefix           prompt prefix for upgrade steps
	 * @param patchConsumptionPromptPrefix  prompt prefix for patch consumption steps
	 * @param steps                         list of step templates
	 */
	public AiAssistConfigModel(final List<VersionEntry> versions, final String upgradePromptPrefix, final String patchConsumptionPromptPrefix,
			final List<AiPlanStep> steps) {
		this.versions = versions;
		this.upgradePromptPrefix = upgradePromptPrefix;
		this.patchConsumptionPromptPrefix = patchConsumptionPromptPrefix;
		this.steps = steps;
	}

	/**
	 * Load the AI assist configuration from the classpath resource.
	 *
	 * @return the loaded AiAssistConfigModel
	 * @throws IOException if the configuration file cannot be loaded
	 */
	public static AiAssistConfigModel loadFromResource() throws IOException {
		try (InputStream inputStream = AiAssistConfigModel.class.getResourceAsStream("/ai-assist-config.json")) {
			if (inputStream == null) {
				throw new IOException("Could not find ai-assist-config.json in classpath");
			}

			try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
				Gson gson = new Gson();
				return gson.fromJson(reader, AiAssistConfigModel.class);
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
		List<String> versionStrings = getVersionStrings();
		int fromIndex = versionStrings.indexOf(fromVersion);
		int toIndex = versionStrings.indexOf(toVersion);

		if (fromIndex == -1) {
			throw new IllegalArgumentException("Invalid from version: " + fromVersion);
		}
		if (toIndex == -1) {
			throw new IllegalArgumentException("Invalid to version: " + toVersion);
		}
		if (fromIndex > toIndex) {
			throw new IllegalArgumentException(
					"From version must not be later than to version. From: " + fromVersion + ", To: " + toVersion);
		}

		// Special case: if from equals to (patch consumption), return a sequence with the version repeated
		// This creates one transition from version to version for applying patches
		if (fromIndex == toIndex) {
			List<String> result = new ArrayList<>();
			result.add(fromVersion);
			result.add(toVersion);
			return result;
		}

		// Return all versions from fromIndex to toIndex (inclusive)
		return new ArrayList<>(versionStrings.subList(fromIndex, toIndex + 1));
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
	 * Get the list of valid version strings.
	 *
	 * @return the version strings
	 */
	public List<String> getVersions() {
		return getVersionStrings();
	}

	/**
	 * Set the list of valid version entries.
	 *
	 * @param versions the version entries
	 */
	public void setVersions(final List<VersionEntry> versions) {
		this.versions = versions;
	}

	/**
	 * Get the upgrade notes URL for a given version.
	 *
	 * @param version the version string
	 * @return the upgrade notes URL, or empty string if not set
	 */
	public String getUpgradeNotesUrl(final String version) {
		if (versions == null) {
			return "";
		}
		return versions.stream()
				.filter(entry -> version.equals(entry.getVersion()))
				.map(VersionEntry::getUpgradeNotesUrl)
				.findFirst()
				.orElse("");
	}

	private List<String> getVersionStrings() {
		if (versions == null) {
			return new ArrayList<>();
		}
		return versions.stream().map(VersionEntry::getVersion).collect(Collectors.toList());
	}

	/**
	 * Get the list of step templates.
	 *
	 * @return the steps
	 */
	public List<AiPlanStep> getSteps() {
		return steps;
	}

	/**
	 * Set the list of step templates.
	 *
	 * @param steps the steps
	 */
	public void setSteps(final List<AiPlanStep> steps) {
		this.steps = steps;
	}

	/**
	 * Check if a version is in the valid versions list.
	 *
	 * @param version the version to check
	 * @return true if the version is valid
	 */
	public boolean isValidVersion(final String version) {
		return versions != null && getVersionStrings().contains(version);
	}

	/**
	 * Get the upgrade prompt prefix.
	 *
	 * @return the upgrade prompt prefix
	 */
	public String getUpgradePromptPrefix() {
		return upgradePromptPrefix;
	}

	/**
	 * Set the upgrade prompt prefix.
	 *
	 * @param upgradePromptPrefix the upgrade prompt prefix
	 */
	public void setUpgradePromptPrefix(final String upgradePromptPrefix) {
		this.upgradePromptPrefix = upgradePromptPrefix;
	}

	/**
	 * Get the patch consumption prompt prefix.
	 *
	 * @return the patch consumption prompt prefix
	 */
	public String getPatchConsumptionPromptPrefix() {
		return patchConsumptionPromptPrefix;
	}

	/**
	 * Set the patch consumption prompt prefix.
	 *
	 * @param patchConsumptionPromptPrefix the patch consumption prompt prefix
	 */
	public void setPatchConsumptionPromptPrefix(final String patchConsumptionPromptPrefix) {
		this.patchConsumptionPromptPrefix = patchConsumptionPromptPrefix;
	}
}
