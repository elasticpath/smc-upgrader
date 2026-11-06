package com.elasticpath.tools.smcupgrader;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A content hash of every uncommitted file in the working tree, used to find the files that a step changed.
 *
 * <p>Comparing two snapshots finds files that a step changed even when the working tree already had uncommitted
 * changes before the step, as it does during a merge.</p>
 */
public final class WorkingTreeSnapshot {
	private static final String MISSING = "missing";
	private static final List<String> EXCLUDED_PATHS = Arrays.asList(Constants.PLAN_FILE_NAME, Constants.JOURNAL_FILE_NAME);

	private final Map<String, String> hashes;

	private WorkingTreeSnapshot(final Map<String, String> hashes) {
		this.hashes = hashes;
	}

	/**
	 * Capture a snapshot of the uncommitted files in the working tree.
	 *
	 * @param gitClient the Git client
	 * @return the snapshot
	 */
	public static WorkingTreeSnapshot capture(final GitClient gitClient) {
		File workingDir = gitClient.getWorkingDir();
		Map<String, String> hashes = new HashMap<>();
		for (String path : gitClient.getUncommittedPaths()) {
			if (!EXCLUDED_PATHS.contains(path)) {
				hashes.put(path, hashFile(new File(workingDir, path)));
			}
		}
		return new WorkingTreeSnapshot(hashes);
	}

	/**
	 * Find the files that differ between an earlier snapshot and this one.
	 *
	 * @param before the earlier snapshot
	 * @return the changed paths, sorted
	 */
	public Set<String> changedSince(final WorkingTreeSnapshot before) {
		Set<String> allPaths = new HashSet<>(hashes.keySet());
		allPaths.addAll(before.hashes.keySet());
		Set<String> changed = new TreeSet<>();
		for (String path : allPaths) {
			if (!Objects.equals(hashes.get(path), before.hashes.get(path))) {
				changed.add(path);
			}
		}
		return changed;
	}

	private static String hashFile(final File file) {
		if (!file.isFile()) {
			return MISSING;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(Files.readAllBytes(file.toPath()));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
