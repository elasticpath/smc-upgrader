/*	Copyright 2024 Elastic Path Software Inc.

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

package com.elasticpath.tools.smcupgrader;

import static com.elasticpath.tools.smcupgrader.UpgradeController.LOGGER;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import com.elasticpath.tools.smcupgrader.ai.AiPlanGenerator;
import com.elasticpath.tools.smcupgrader.ai.UpgradePath;

/**
 * The main SMC Upgrader class.
 */
@CommandLine.Command(name = "smc-upgrader", mixinStandardHelpOptions = true, version = "smc-upgrader 1.0",
		description = "Utility to apply Elastic Path Self-Managed Commerce updates to a codebase.")
public class SMCUpgraderCLI implements Callable<Integer> {
	private static final String DEFAULT_UPSTREAM_REPO_URL = "git@code.elasticpath.com:ep-commerce/ep-commerce.git";

	@CommandLine.Parameters(index = "0", arity = "0..1",
			description = "The version of Elastic Path Self-Managed Commerce to upgrade to. "
					+ "Optional when using --ai:start or --ai:continue.")
	private String version;

	@CommandLine.Option(names = { "-C" },
			description = "The working directory containing the git repo to be upgraded. Defaults to the current working directory.",
			defaultValue = "${sys:user.dir}"
	)
	private File workingDir;

	@CommandLine.Option(names = { "-u", "--upstream-repository-url" },
			description = "The URL of the upstream repository containing upgrade commits.",
			defaultValue = DEFAULT_UPSTREAM_REPO_URL)
	private String upstreamRemoteRepositoryUrl;

	@CommandLine.Option(names = { "-v", "--verbose" },
			description = "Enables debug logging.",
			defaultValue = "false")
	private boolean debugLogging;

	@CommandLine.Option(names = { "--clean-working-directory-check" },
			description = "Indicates whether to do a clean working directory check. Enabled by default.",
			negatable = true,
			defaultValue = "true")
	private boolean doCleanWorkingDirectoryCheck;

	@CommandLine.Option(names = { "-f", "--fetch" },
			description = "Indicates whether to fetch the latest updates from the remote. Enabled by default.",
			negatable = true, defaultValue = "true")
	private boolean doFetch;

	@CommandLine.Option(names = { "-p", "--revert-patches" },
			description = "Indicates whether to revert patches before merging. Enabled by default.",
			negatable = true,
			defaultValue = "true")
	private boolean doRevertPatches;

	@CommandLine.Option(names = { "-m", "--merge" },
			description = "Indicates whether to perform a merge. Enabled by default.",
			negatable = true,
			defaultValue = "true")
	private boolean doMerge;

	@CommandLine.Option(names = { "-r", "--resolve-conflicts" },
			description = "Indicates whether to resolve merge conflicts. Enabled by default.",
			negatable = true,
			defaultValue = "true")
	private boolean doConflictResolution;

	@CommandLine.Option(names = { "-d", "--resolve-diffs" },
			description = "Indicates whether to reconcile diffs between the merged branch and the upstream contents. Enabled by default.",
			negatable = true, defaultValue = "true")
	private boolean doDiffResolution;

	@CommandLine.Option(names = { "--ai:start" },
			description = "Start AI-assisted upgrade mode and generate upgrade plan. Requires version parameter.")
	private boolean aiStart;

	@CommandLine.Option(names = { "--ai:continue" },
			description = "Continue AI-assisted upgrade from saved plan.")
	private boolean aiContinue;

	@Override
	public Integer call() {
		try {
			if (debugLogging) {
				Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
				rootLogger.setLevel(Level.DEBUG);
			}

			final UpgradeController upgradeController = new UpgradeController(workingDir, upstreamRemoteRepositoryUrl);

			// Handle AI assist modes
			if (aiStart) {
				return handleAiStart(upgradeController);
			} else if (aiContinue) {
				return handleAiContinue(upgradeController);
			}

			// Standard upgrade mode - version is required
			if (version == null || version.trim().isEmpty()) {
				LOGGER.error("Version parameter is required for standard upgrade mode.");
				LOGGER.error("Usage: smc-upgrader <version>");
				LOGGER.error("   or: smc-upgrader --ai:start <version>");
				LOGGER.error("   or: smc-upgrader --ai:continue");
				return 1;
			}

			upgradeController.performUpgrade(version, doCleanWorkingDirectoryCheck, doFetch, doRevertPatches, doMerge,
					doConflictResolution, doDiffResolution);

			return 0;
		} catch (RuntimeException e) {
			LOGGER.error("Unexpected error encountered while upgrading", e);
		} catch (IOException e) {
			LOGGER.error("IO error encountered", e);
		}

		return 1;
	}

	/**
	 * Handle AI assist start mode.
	 *
	 * @param upgradeController the upgrade controller
	 * @return exit code
	 * @throws IOException if an error occurs
	 */
	private Integer handleAiStart(final UpgradeController upgradeController) throws IOException {
		if (version == null || version.trim().isEmpty()) {
			LOGGER.error("Version parameter is required for --ai:start mode.");
			LOGGER.error("Usage: smc-upgrader --ai:start <version>");
			return 1;
		}

		UpgradePath upgradePath = UpgradePath.loadFromResource();
		AiPlanGenerator generator = new AiPlanGenerator(upgradePath, upgradeController);

		boolean generated = generator.generatePlan(version, workingDir);
		return generated ? 0 : 1;
	}

	/**
	 * Handle AI assist continue mode.
	 *
	 * @param upgradeController the upgrade controller
	 * @return exit code
	 */
	private Integer handleAiContinue(final UpgradeController upgradeController) {
		LOGGER.info("AI continue mode not yet implemented.");
		LOGGER.info("This will be implemented in Sprint 4.");
		return 1;
	}

	/**
	 * Main entrypoint.
	 *
	 * @param args command-line arguments
	 */
	public static void main(final String... args) {
		System.exit(new CommandLine(new SMCUpgraderCLI())
				.setToggleBooleanFlags(true)
				.execute(args));
	}
}
