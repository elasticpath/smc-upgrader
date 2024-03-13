package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link DiffResolutionDeterminer}
 */
class DiffResolutionDeterminerTest {

	private static final String FILENAME = "pom.xml";

	private final DiffResolutionDeterminer diffResolutionDeterminer = new DiffResolutionDeterminer();

	@Test
	public void verifyTheirChangeAcceptedWhenAllCommitsExistInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.UPDATED, null, null, null, null);
		assertThat(diffResolutionDeterminer.determineResolution(change, () -> true)).isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
	}

	@Test
	public void verifyOurChangeUsedWhenNotAllCommitsExistInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.UPDATED, null, null, null, null);
		assertThat(diffResolutionDeterminer.determineResolution(change, () -> false)).isEqualTo(ConflictResolutionStrategy.ACCEPT_OURS);
	}

}