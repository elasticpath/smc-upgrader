package com.elasticpath.tools.smcupgrader;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Determines the appropriate method of resolving conflicts.
 */
public class ConflictResolutionDeterminer {

	/**
	 * Determines the appropriate method of resolving a given conflict.
	 *
	 * @param change                             represents the conflict to resolve
	 * @param upstreamMatch                      a function that finds the upstream version matching the latest local version, if any
	 * @param fileContentsOursTheirsIdentical    a function that determines whether the contents for both sides of the conflict are identical
	 * @return the decision: the strategy, the rule that chose it, and the upstream match it used
	 */
	public ResolutionDecision determineResolution(final Change change,
			final Supplier<Optional<UpstreamMatch>> upstreamMatch,
			final Supplier<Boolean> fileContentsOursTheirsIdentical) {
		if (change.getTheirChangeType() == ChangeType.DELETED) {
			return new ResolutionDecision(ConflictResolutionStrategy.ACCEPT_THEIRS, ResolutionRule.R_DEL, null);
		}

		if (change.getOurChangeType() == ChangeType.CREATED && change.getTheirChangeType() == ChangeType.CREATED) {
			if (fileContentsOursTheirsIdentical.get()) {
				return new ResolutionDecision(ConflictResolutionStrategy.ACCEPT_OURS, ResolutionRule.R_SAME, null);
			}
		}

		Optional<UpstreamMatch> match = upstreamMatch.get();
		if (match.isPresent()) {
			return new ResolutionDecision(ConflictResolutionStrategy.ACCEPT_THEIRS, ResolutionRule.R_SAFE, match.get());
		}

		return new ResolutionDecision(ConflictResolutionStrategy.MANUAL_RESOLUTION_REQUIRED, ResolutionRule.R_MANUAL, null);
	}
}
