package com.elasticpath.tools.smcupgrader;

import java.util.function.Supplier;

/**
 * Determines the appropriate method of resolving conflicts.
 */
public class DiffResolutionDeterminer {

	/**
	 * Determines the appropriate method of resolving a given conflict.
	 *
	 * @param change                             represents the conflict to resolve
	 * @param safeToOverwrite                    a function that determines whether the path is safe to overwrite
	 * @return the {@link ConflictResolutionStrategy} that should be used to resolve this conflict
	 */
	public ConflictResolutionStrategy determineResolution(final Change change,
			final Supplier<Boolean> safeToOverwrite) {
		if (safeToOverwrite.get()) {
			return ConflictResolutionStrategy.ACCEPT_THEIRS;
		} else {
			return ConflictResolutionStrategy.ACCEPT_OURS;
		}
	}

}
