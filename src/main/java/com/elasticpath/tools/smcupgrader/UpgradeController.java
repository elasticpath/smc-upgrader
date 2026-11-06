package com.elasticpath.tools.smcupgrader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
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

	private final GitClient gitClient;

	private UpgradeJournal activeJournal;

	/**
	 * Constructor.
	 *
	 * @param workingDir the working directory containing the git repo to be upgraded
	 */
	public UpgradeController(final File workingDir) {
		this(buildGitClient(workingDir));
	}

	UpgradeController(final GitClient gitClient) {
		this.gitClient = gitClient;
		this.upstreamRemoteManager = new UpstreamRemoteManager(gitClient);
		this.patchReverter = new PatchReverter(gitClient);
		this.merger = new Merger(gitClient);
		this.mergeConflictResolver = new MergeConflictResolver(gitClient);
		this.diffConflictResolver = new DiffConflictResolver(gitClient);
	}

	private void journalCleanMerges(final String preMergeHead, final String version) {
		final Set<String> cleanMerges = new HashSet<>(gitClient.getPathsChangedSince(preMergeHead));
		cleanMerges.removeAll(gitClient.getConflicts().keySet());
		cleanMerges.remove(Constants.PLAN_FILE_NAME);
		cleanMerges.remove(Constants.JOURNAL_FILE_NAME);
		activeJournal.appendParagraph(String.format("Git merged %d file(s) from the %s release branch without a conflict. "
				+ "These files have no entries.", cleanMerges.size(), version));
	}

	private void journalPass(final String summary, final PassResult passResult, final String upstreamRemoteName) {
		for (JournalEntry entry : passResult.getEntries()) {
			final Object commitId = entry.get(JournalEntry.MATCHED_UPSTREAM_COMMIT);
			if (commitId != null) {
				gitClient.findUpstreamBranchContaining(commitId.toString(), upstreamRemoteName)
						.ifPresent(branch -> entry.with(JournalEntry.MATCHED_UPSTREAM_BRANCH, branch));
			}
		}
		activeJournal.appendParagraph(summary);
		activeJournal.appendEntries(passResult.getEntries());
	}

	private static GitClient buildGitClient(final File workingDir) {
		final FileRepositoryBuilder builder = new FileRepositoryBuilder();
		try {
			final Repository repository = builder.setGitDir(new File(workingDir, ".git"))
					.readEnvironment() // scan environment GIT_* variables
					.build();
			return new GitClientImpl(repository);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Performs the upgrade as AI Assist Mode runs it: fetch, merge, conflict pass, and diff pass, without the clean working
	 * directory check or the patch revert. Writes one journal entry for each file the conflict pass or the diff pass handled.
	 *
	 * @param version the target version to upgrade to
	 * @param journal the journal to write to. The caller starts the run subsection.
	 */
	public void performUpgrade(final String version, final UpgradeJournal journal) {
		activeJournal = journal;
		try {
			performUpgrade(version, false, true, false, true, true, true);
		} finally {
			activeJournal = null;
		}
	}

	/**
	 * Performs the upgrade, by merging an upstream branch and resolving conflicts.
	 * Setting both {@code doMerge} and {@code doConflictResolution} to {@code false} will not modify the local working state and is akin to a dry
	 * run.
	 *
	 * @param version                      the target version to upgrade to
	 * @param doCleanWorkingDirectoryCheck perform a clean working directory check
	 * @param doFetch                      fetch the latest updates from the remote
	 * @param doRevertPatches              revert any patches
	 * @param doMerge                      perform the code merge
	 * @param doConflictResolution         perform conflict resolution
	 * @param doDiffResolution             perform diff resolution
	 */
	public void performUpgrade(final String version,
							   final boolean doCleanWorkingDirectoryCheck,
							   final boolean doFetch,
							   final boolean doRevertPatches,
							   final boolean doMerge,
							   final boolean doConflictResolution,
							   final boolean doDiffResolution) {
		String currentVersion = convertVersionToReleaseFormat(determineCurrentVersion());
		LOGGER.info("Detected current version {}.", currentVersion);

		final String upstreamRemoteName = upstreamRemoteManager.getUpstreamRemoteName();

		if (doFetch) {
			LOGGER.info("Fetching latest updates from remote '{}'", upstreamRemoteName);
			gitClient.fetch(upstreamRemoteName);
		} else {
			LOGGER.info("Skipping fetch.");
		}

		if (doRevertPatches) {
			if (!currentVersion.equals(version)) {
				patchReverter.revertPatches(upstreamRemoteName, currentVersion);
			} else {
				LOGGER.info("We're not doing a version upgrade, so skipping the patch revert step.");
			}
		}

		if (doMerge) {
			final String preMergeHead = activeJournal == null ? null : gitClient.getHeadCommitId();
			merger.merge(doCleanWorkingDirectoryCheck, upstreamRemoteName, version);
			LOGGER.info("Merge succeeded.");
			if (activeJournal != null) {
				journalCleanMerges(preMergeHead, version);
			}
		} else {
			LOGGER.info("Skipping merge.");
		}

		if (doConflictResolution) {
			final PassResult conflictPass = mergeConflictResolver.resolveMergeConflicts(upstreamRemoteName);
			if (activeJournal != null) {
				final String summary = String.format("The conflict pass handled %d conflicted file(s). "
								+ "Entries with `needs_review: true` are left for the next step to resolve.",
						conflictPass.getEntries().size());
				journalPass(summary, conflictPass, upstreamRemoteName);
			}
		} else {
			LOGGER.info("Skipping merge conflict resolution.");
		}

		if (doDiffResolution) {
			final PassResult diffPass = diffConflictResolver.resolveDiffConflicts(upstreamRemoteName, version);
			if (activeJournal != null) {
				final String summary = String.format("The diff pass changed %d file(s) that differed from the %s release branch. "
								+ "It left %d file(s) unchanged without an entry, "
								+ "because their latest local version matches no upstream version.",
						diffPass.getEntries().size(), version, diffPass.getUnchangedCount());
				journalPass(summary, diffPass, upstreamRemoteName);
			}
		} else {
			LOGGER.info("Skipping diff conflict resolution.");
		}
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
				File pomFile = new File(gitClient.getWorkingDir(), "pom.xml");
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
			Path readmePath = Paths.get(gitClient.getWorkingDir().getAbsolutePath(), "README.txt");
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
