package com.elasticpath.tools.smcupgrader;

/**
 * Enumeration of possible resolutions for an individual merge conflict.
 */
public enum ConflictResolutionStrategy {
	ACCEPT_THEIRS,
	ACCEPT_OURS,
	MANUAL_RESOLUTION_REQUIRED
}
