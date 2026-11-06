package com.elasticpath.tools.smcupgrader;

import java.util.Optional;

/**
 * The strategy chosen for a file, the rule that chose it, and the upstream match the rule used.
 */
public final class ResolutionDecision {
	private final ConflictResolutionStrategy strategy;
	private final ResolutionRule rule;
	private final UpstreamMatch upstreamMatch;

	/**
	 * Constructor.
	 *
	 * @param strategy      the resolution strategy
	 * @param rule          the rule that chose the strategy, or null if the file is left unchanged by the diff pass
	 * @param upstreamMatch the upstream match the rule used, or null if none
	 */
	public ResolutionDecision(final ConflictResolutionStrategy strategy, final ResolutionRule rule, final UpstreamMatch upstreamMatch) {
		this.strategy = strategy;
		this.rule = rule;
		this.upstreamMatch = upstreamMatch;
	}

	public ConflictResolutionStrategy getStrategy() {
		return strategy;
	}

	public ResolutionRule getRule() {
		return rule;
	}

	public Optional<UpstreamMatch> getUpstreamMatch() {
		return Optional.ofNullable(upstreamMatch);
	}
}
