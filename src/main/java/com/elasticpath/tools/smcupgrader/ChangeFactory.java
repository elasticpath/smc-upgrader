package com.elasticpath.tools.smcupgrader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.ObjectId;

import com.elasticpath.tools.smcupgrader.IndexEntry.Stage;

/**
 * Creates {@link Change} instances.
 */
public class ChangeFactory {

	/**
	 * Creates a list of {@link Change} instances.
	 *
	 * @param conflicts    a mapping of conflicting file names to their {@link IndexDiff.StageState StageState}.
	 * @param indexEntries the set of {@link IndexEntry} instances representing every index entry in the git database
	 * @return a list of {@link Change} instances
	 */
	public List<Change> createChanges(final Map<String, IndexDiff.StageState> conflicts, final Set<IndexEntry> indexEntries) {
		return conflicts.entrySet().stream()
				.map(entry -> createChange(entry.getKey(), entry.getValue(), indexEntries))
				.collect(Collectors.toList());
	}

	/**
	 * Creates a list of {@link Change} instances.
	 *
	 * @param diffs a list of {@link DiffEntry diffs}
	 * @return a list of {@link Change} instances
	 */
	public List<Change> createChanges(final List<DiffEntry> diffs) {
		List<Change> changes = diffs.stream()
				.map(this::createChange)
				.collect(Collectors.toList());

		// multiple changes with the same path occur for files with unresolved conflicts. we can filter out any such case
		Map<String, Integer> changeCounts = new HashMap<>();
		for (Change change : changes) {
			changeCounts.put(change.getPath(), changeCounts.get(change.getPath()) == null ? 1 : changeCounts.get(change.getPath()) + 1);
		}

		return changes.stream()
				.filter(change -> changeCounts.get(change.getPath()) == 1)
				.collect(Collectors.toList());
	}

	Change createChange(final String path, final IndexDiff.StageState changeType, final Set<IndexEntry> indexItems) {
		String theirVersionSha = null;
		String ourVersionSha = null;

		for (final IndexEntry indexItem : indexItems) {
			if (indexItem.getPath().equals(path)) {
				if (indexItem.getStage() == Stage.OURS) {
					ourVersionSha = indexItem.getSha();
				} else if (indexItem.getStage() == Stage.THEIRS) {
					theirVersionSha = indexItem.getSha();
				}
			}
		}

		return new Change(path,
				createTheirChangeType(changeType),
				createOurChangeType(changeType),
				theirVersionSha,
				ourVersionSha,
				null);
	}

	Change createChange(final DiffEntry diffEntry) {
		final ObjectId newObjectId = diffEntry.getNewId().toObjectId();
		final ObjectId oldObjectId = diffEntry.getOldId().toObjectId();

		final String newPath = diffEntry.getNewPath();
		final String oldPath = diffEntry.getOldPath();

		return new Change(diffEntry.getChangeType() == DiffEntry.ChangeType.ADD ? newPath : oldPath,
				createChangeType(diffEntry.getChangeType()),
				null,
				newObjectId.equals(ObjectId.zeroId()) ? null : newObjectId.getName(),
				oldObjectId.equals(ObjectId.zeroId()) ? null : oldObjectId.getName(),
				toFilePermission(diffEntry.getNewMode()));
	}

	private static ChangeType createChangeType(final DiffEntry.ChangeType diffEntryChangeType) {
		switch (diffEntryChangeType) {
			case ADD:
			case COPY:
				return ChangeType.CREATED;
			case MODIFY:
				return ChangeType.UPDATED;
			case DELETE:
				return ChangeType.DELETED;
			case RENAME:
				return ChangeType.RENAMED;
			default:
				throw new IllegalArgumentException("Unexpected diff entry change type " + diffEntryChangeType);
		}
	}

	private static ChangeType createOurChangeType(final IndexDiff.StageState stageState) {
		switch (stageState) {
			case BOTH_DELETED:
			case DELETED_BY_US:
				return ChangeType.DELETED;
			case ADDED_BY_US:
			case BOTH_ADDED:
				return ChangeType.CREATED;
			case BOTH_MODIFIED:
			case DELETED_BY_THEM:
				return ChangeType.UPDATED;
			case ADDED_BY_THEM:
				return null;
			default:
				throw new IllegalArgumentException("Unexpected stage state type " + stageState);
		}
	}

	private static ChangeType createTheirChangeType(final IndexDiff.StageState stageState) {
		switch (stageState) {
			case BOTH_DELETED:
			case DELETED_BY_THEM:
				return ChangeType.DELETED;
			case ADDED_BY_THEM:
			case BOTH_ADDED:
				return ChangeType.CREATED;
			case DELETED_BY_US:
			case BOTH_MODIFIED:
				return ChangeType.UPDATED;
			case ADDED_BY_US:
				return null;
			default:
				throw new IllegalArgumentException("Unexpected stage state type " + stageState);
		}
	}

	private FilePermissions toFilePermission(final FileMode fileMode) {
		if (fileMode == null) {
			return null;
		}

		if (fileMode.equals(FileMode.REGULAR_FILE)) { // POSIX mode 0100644
			return new FilePermissions(true, true, false);
		}

		if (fileMode.equals(FileMode.EXECUTABLE_FILE)) { // POSIX mode 0100755
			return new FilePermissions(true, true, true);
		}

		return null;
	}
}
