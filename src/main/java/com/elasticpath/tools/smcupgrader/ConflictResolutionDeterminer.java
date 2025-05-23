package com.elasticpath.tools.smcupgrader;

import java.util.function.Supplier;

/**
 * Determines the appropriate method of resolving conflicts.
 */
public class ConflictResolutionDeterminer {

	/**
	 * Determines the appropriate method of resolving a given conflict.
	 *
	 * @param change                             represents the conflict to resolve
	 * @param safeToOverwrite                    a function that determines whether the path is safe to overwrite
	 * @param fileContentsOursTheirsIdentical    a function that determines whether the contents for both sides of the conflict are identical
	 * @return the {@link ConflictResolutionStrategy} that should be used to resolve this conflict
	 */
	public ConflictResolutionStrategy determineResolution(final Change change,
			final Supplier<Boolean> safeToOverwrite,
			final Supplier<Boolean> fileContentsOursTheirsIdentical) {
		if (change.getTheirChangeType() == ChangeType.DELETED) {
			return ConflictResolutionStrategy.ACCEPT_THEIRS;
		}

		if (change.getOurChangeType() == ChangeType.CREATED && change.getTheirChangeType() == ChangeType.CREATED) {
			if (fileContentsOursTheirsIdentical.get()) {
				return ConflictResolutionStrategy.ACCEPT_OURS;
			}
		}

		if (safeToOverwrite.get()) {
			return ConflictResolutionStrategy.ACCEPT_THEIRS;
		}

		return ConflictResolutionStrategy.MANUAL_RESOLUTION_REQUIRED;
	}

}
