package com.elasticpath.tools.smcupgrader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Records what the Git merge step did, so that AI Assist Mode can write it to the upgrade journal.
 */
public class UpgradeReport {
	private String currentVersion;
	private String targetBranch;
	private String targetCommitId;
	private int cleanMergeCount;
	private int conflictCount;
	private long conflictPassChangeCount;
	private long diffPassChangeCount;
	private final List<String> mergeFileChanges = Collections.synchronizedList(new ArrayList<>());
	private final List<String> conflictResolutionFileChanges = Collections.synchronizedList(new ArrayList<>());
	private final List<String> outdatedPlatformFileChanges = Collections.synchronizedList(new ArrayList<>());

	/**
	 * Formats a file change bullet.
	 *
	 * @param path        the path of the changed file
	 * @param explanation what happened to the file, and why
	 * @return the bullet text
	 */
	public static String fileChange(final String path, final String explanation) {
		return "`" + path + "`: " + explanation;
	}

	/**
	 * Adds the file change bullets of the Git merge phase, in the given order.
	 *
	 * @param changes the bullets, created with {@link #fileChange(String, String)}
	 */
	public void addMergeFileChanges(final Collection<String> changes) {
		mergeFileChanges.addAll(changes);
	}

	public List<String> getMergeFileChanges() {
		return copyOf(mergeFileChanges);
	}

	/**
	 * Adds the file change bullets of the deterministic conflict resolution phase, in the given order.
	 *
	 * @param changes the bullets, created with {@link #fileChange(String, String)}
	 */
	public void addConflictResolutionFileChanges(final Collection<String> changes) {
		conflictResolutionFileChanges.addAll(changes);
	}

	public List<String> getConflictResolutionFileChanges() {
		return copyOf(conflictResolutionFileChanges);
	}

	/**
	 * Adds the file change bullets of the outdated platform files phase, in the given order.
	 *
	 * @param changes the bullets, created with {@link #fileChange(String, String)}
	 */
	public void addOutdatedPlatformFileChanges(final Collection<String> changes) {
		outdatedPlatformFileChanges.addAll(changes);
	}

	public List<String> getOutdatedPlatformFileChanges() {
		return copyOf(outdatedPlatformFileChanges);
	}

	private static List<String> copyOf(final List<String> changes) {
		synchronized (changes) {
			return Collections.unmodifiableList(new ArrayList<>(changes));
		}
	}

	public String getCurrentVersion() {
		return currentVersion;
	}

	public void setCurrentVersion(final String currentVersion) {
		this.currentVersion = currentVersion;
	}

	public String getTargetBranch() {
		return targetBranch;
	}

	public void setTargetBranch(final String targetBranch) {
		this.targetBranch = targetBranch;
	}

	public String getTargetCommitId() {
		return targetCommitId;
	}

	public void setTargetCommitId(final String targetCommitId) {
		this.targetCommitId = targetCommitId;
	}

	public int getCleanMergeCount() {
		return cleanMergeCount;
	}

	public void setCleanMergeCount(final int cleanMergeCount) {
		this.cleanMergeCount = cleanMergeCount;
	}

	public int getConflictCount() {
		return conflictCount;
	}

	public void setConflictCount(final int conflictCount) {
		this.conflictCount = conflictCount;
	}

	public long getConflictPassChangeCount() {
		return conflictPassChangeCount;
	}

	public void setConflictPassChangeCount(final long conflictPassChangeCount) {
		this.conflictPassChangeCount = conflictPassChangeCount;
	}

	public long getDiffPassChangeCount() {
		return diffPassChangeCount;
	}

	public void setDiffPassChangeCount(final long diffPassChangeCount) {
		this.diffPassChangeCount = diffPassChangeCount;
	}
}
