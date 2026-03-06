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
 * Represents a version entry in the upgrade path configuration.
 */
public class VersionEntry {
	private String version;
	private String upgradeNotesUrl;

	/**
	 * Default constructor for JSON deserialization.
	 */
	public VersionEntry() {
	}

	/**
	 * Constructor.
	 *
	 * @param version         the version string
	 * @param upgradeNotesUrl the URL for upgrade notes
	 */
	public VersionEntry(final String version, final String upgradeNotesUrl) {
		this.version = version;
		this.upgradeNotesUrl = upgradeNotesUrl;
	}

	/**
	 * Get the version string.
	 *
	 * @return the version string
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * Set the version string.
	 *
	 * @param version the version string
	 */
	public void setVersion(final String version) {
		this.version = version;
	}

	/**
	 * Get the upgrade notes URL.
	 *
	 * @return the upgrade notes URL
	 */
	public String getUpgradeNotesUrl() {
		return upgradeNotesUrl;
	}

	/**
	 * Set the upgrade notes URL.
	 *
	 * @param upgradeNotesUrl the upgrade notes URL
	 */
	public void setUpgradeNotesUrl(final String upgradeNotesUrl) {
		this.upgradeNotesUrl = upgradeNotesUrl;
	}
}
