package com.elasticpath.tools.smcupgrader;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Determines the appropriate method of resolving conflicts.
 */
public class DiffResolutionDeterminer {

	/**
	 * Determines the appropriate method of resolving a given conflict.
	 *
	 * @param change        represents the conflict to resolve
	 * @param upstreamMatch a function that finds the upstream version matching the latest local version, if any
	 * @return the decision. When the file is left unchanged, the rule is null.
	 */
	public ResolutionDecision determineResolution(final Change change,
			final Supplier<Optional<UpstreamMatch>> upstreamMatch) {
		Optional<UpstreamMatch> match = upstreamMatch.get();
		if (!match.isPresent()) {
			return new ResolutionDecision(ConflictResolutionStrategy.ACCEPT_OURS, null, null);
		}
		return new ResolutionDecision(ConflictResolutionStrategy.ACCEPT_THEIRS, diffRule(change), match.get());
	}

	private static ResolutionRule diffRule(final Change change) {
		if (change.getTheirChangeType() == ChangeType.DELETED) {
			return ResolutionRule.D_DELETE;
		}
		if (change.getTheirChangeType() == ChangeType.CREATED) {
			return ResolutionRule.D_READD;
		}
		return ResolutionRule.D_REPLACE;
	}
}
