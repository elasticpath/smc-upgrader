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
	 * @param allLocalCommitsExistInUpstreamRepo a function that determines whether all commits for the given change also exist on the remote
	 *                                           repository
	 * @return the {@link ConflictResolutionStrategy} that should be used to resolve this conflict
	 */
	public ConflictResolutionStrategy determineResolution(final Change change,
			final Supplier<Boolean> allLocalCommitsExistInUpstreamRepo) {
		if (allLocalCommitsExistInUpstreamRepo.get()) {
			return ConflictResolutionStrategy.ACCEPT_THEIRS;
		} else {
			return ConflictResolutionStrategy.ACCEPT_OURS;
		}
	}

}
