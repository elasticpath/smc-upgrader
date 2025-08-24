package com.elasticpath.tools.smcupgrader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.elasticpath.tools.smcupgrader.impl.GitClientImpl;

/**
 * Main class that orchestrates a code update, by merging an upstream branch and resolving conflicts.
 */
public class UpgradeController {
	/**
	 * The Logger for the application.
	 */
	static final Logger LOGGER = LoggerFactory.getLogger(UpgradeController.class);

	/**
	 * Pattern to match version strings in README.txt files.
	 * Matches strings like "Elastic Path Commerce 8.7.0 (build 8.7.0.20250730203654-29b4ea)"
	 * and captures just the version number (e.g., "8.7.0").
	 */
	private static final Pattern VERSION_PATTERN = Pattern.compile("Elastic Path Commerce\\s+([0-9]+\\.[0-9]+\\.[0-9]+)");

	private final UpstreamRemoteManager upstreamRemoteManager;

	private final PatchReverter patchReverter;

	private final Merger merger;

	private final MergeConflictResolver mergeConflictResolver;

	private final DiffConflictResolver diffConflictResolver;

	/**
	 * Constructor.
	 *
	 * @param workingDir                  the working directory containing the git repo to be upgraded
	 * @param upstreamRemoteRepositoryUrl the URL of the upstream repository containing upgrade commits
	 */
	public UpgradeController(final File workingDir, final String upstreamRemoteRepositoryUrl) {
		final FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try {
			// scan environment GIT_* variables
			final Repository repository = builder.setGitDir(new File(workingDir, ".git"))
					.readEnvironment() // scan environment GIT_* variables
					.build();

			final GitClient gitClient = new GitClientImpl(repository);
			upstreamRemoteManager = new UpstreamRemoteManager(gitClient, upstreamRemoteRepositoryUrl);
			patchReverter = new PatchReverter(gitClient);
			merger = new Merger(gitClient);
			mergeConflictResolver = new MergeConflictResolver(gitClient);
			diffConflictResolver = new DiffConflictResolver(gitClient);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Performs the upgrade, by merging an upstream branch and resolving conflicts.
	 * Setting both {@code doMerge} and {@code doConflictResolution} to {@code false} will not modify the local working state and is akin to a dry
	 * run.
	 *
	 * @param version                      the target version to upgrade to
	 * @param doCleanWorkingDirectoryCheck perform a clean working directory check
	 * @param doRevertPatches              revert any patches
	 * @param doMerge                      perform the code merge
	 * @param doConflictResolution         perform conflict resolution
	 * @param doDiffResolution             perform diff resolution
	 */
	public void performUpgrade(final String version, final boolean doCleanWorkingDirectoryCheck, final boolean doRevertPatches,
							   final boolean doMerge, final boolean doConflictResolution, final boolean doDiffResolution) {
		String currentVersion = convertVersionToReleaseFormat(determineCurrentVersion());
		LOGGER.info("Detected current version {}.", currentVersion);

		final String upstreamRemoteName = upstreamRemoteManager.getUpstreamRemoteName();
		LOGGER.debug("Upgrading from remote repository '{}'", upstreamRemoteName);

		if (doRevertPatches) {
			if (!currentVersion.equals(version)) {
				patchReverter.revertPatches(upstreamRemoteName, currentVersion);
			} else {
				LOGGER.info("We're not doing a version upgrade, so skipping the patch revert step.");
			}
		}

		if (doMerge) {
			merger.merge(doCleanWorkingDirectoryCheck, upstreamRemoteName, version);
			LOGGER.info("Merge succeeded.");
		} else {
			LOGGER.info("Skipping merge.");
		}

		if (doConflictResolution) {
			mergeConflictResolver.resolveMergeConflicts(upstreamRemoteName);
		} else {
			LOGGER.info("Skipping merge conflict resolution.");
		}

		if (doDiffResolution) {
			diffConflictResolver.resolveDiffConflicts(upstreamRemoteName, version);
		} else {
			LOGGER.info("Skipping diff conflict resolution.");
		}

		LOGGER.info("Use your IDE to resolve any remaining merge conflicts, or run the following command:\n\n"
				+ "git mergetool\n\n"
				+ "Once all conflicts have been resolved, stage the changes and commit to complete the merge:\n\n"
				+ "git add -A .\n"
				+ "git commit");
	}

	/**
	 * Determines the current version of the source code by checking either the pom.xml or README.txt.
	 *
	 * @return the current version string
	 * @throws IllegalStateException if the version cannot be determined from either source
	 */
	public String determineCurrentVersion() {
		try {
			// First try to get version from pom.xml
			try {
				File pomFile = new File("pom.xml");
				if (pomFile.exists()) {
					Document doc = DocumentBuilderFactory.newInstance()
							.newDocumentBuilder()
							.parse(pomFile);
					doc.getDocumentElement().normalize();

					// Try to get version from project > properties > ep.release.version
					NodeList propertiesNodes = doc.getElementsByTagName("properties");
					if (propertiesNodes.getLength() > 0) {
						Element properties = (Element) propertiesNodes.item(0);
						NodeList versionProperty = properties.getElementsByTagName("ep.release.version");
						if (versionProperty.getLength() > 0) {
							String version = versionProperty.item(0).getTextContent().trim();
							if (!version.isEmpty()) {
								return version;
							}
						}
					}
				}
			} catch (Exception e) {
				LOGGER.debug("Could not read version from pom.xml, trying README.txt", e);
			}

			// If pom.xml method failed, try README.txt
			Path readmePath = Paths.get("README.txt");
			if (Files.exists(readmePath)) {
				String firstLine;
				try (Stream<String> lines = Files.lines(readmePath, StandardCharsets.UTF_8)) {
					firstLine = lines.findFirst().orElse("");
				}

				// Match version using the VERSION_PATTERN constant
				Matcher matcher = VERSION_PATTERN.matcher(firstLine);

				if (matcher.find()) {
					return matcher.group(1);
				}
			}

			throw new IllegalStateException("Could not determine current version from pom.xml or README.txt");

		} catch (IOException e) {
			throw new IllegalStateException("Error while trying to determine current version", e);
		}
	}

	/**
	 * Converts a version string in the format "8.6.0" to the format "8.6.x", used by the release branches.
	 *
	 * @param version the version string to convert
	 * @return the converted version string
	 */
	public String convertVersionToReleaseFormat(final String version) {
		Pattern twoPartVersion = Pattern.compile("^(\\d+\\.\\d+).*");
		Matcher matcher = twoPartVersion.matcher(version);
		if (matcher.matches()) {
			return matcher.group(1) + ".x";
		}
		return "";
	}
}
