package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConflictResolutionDeterminerTest {

	private static final String FILENAME = "extensions/database/ext-data/src/main/resources/environments/local-perf/files/conf/cache/ehcache-local.xml";

	private static final String HEX_SHA = "abcdefg";

	private final ConflictResolutionDeterminer conflictResolutionDeterminer = new ConflictResolutionDeterminer();

	@Test
	public void testTheirChangeAcceptedWhenTheirChangeIsDelete() {
		final Change change = new Change(FILENAME, ChangeType.DELETED, ChangeType.UPDATED, HEX_SHA, HEX_SHA, null);
		assertThat(conflictResolutionDeterminer.determineResolution(change, () -> true, () -> true))
				.isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
	}

	@Test
	public void testTheirChangeAcceptedWhenAllCommitsExistInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.UPDATED, ChangeType.UPDATED, HEX_SHA, HEX_SHA, null);
		assertThat(conflictResolutionDeterminer.determineResolution(change, () -> true, () -> true))
				.isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
	}

	@Test
	public void testManualResolutionRequiredWhenLocalCommitsDoNotExistInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.UPDATED, ChangeType.UPDATED, HEX_SHA, HEX_SHA, null);
		assertThat(conflictResolutionDeterminer.determineResolution(change, () -> false, () -> true))
				.isEqualTo(ConflictResolutionStrategy.MANUAL_RESOLUTION_REQUIRED);
	}

	@Test
	public void testOurChangeAcceptedWhenBothChangesAreCreatedAndContentsAreIdentical() {
		final Change change = new Change(FILENAME, ChangeType.CREATED, ChangeType.CREATED, HEX_SHA, HEX_SHA, null);
		assertThat(conflictResolutionDeterminer.determineResolution(change, () -> false, () -> true))
				.isEqualTo(ConflictResolutionStrategy.ACCEPT_OURS);
	}

	@Test
	public void testManualResolutionRequiredWhenBothChangesAreCreatedAndContentsAreNotIdentical() {
		final Change change = new Change(FILENAME, ChangeType.CREATED, ChangeType.CREATED, HEX_SHA, HEX_SHA, null);
		assertThat(conflictResolutionDeterminer.determineResolution(change, () -> false, () -> false))
				.isEqualTo(ConflictResolutionStrategy.MANUAL_RESOLUTION_REQUIRED);
	}

	@Test
	public void testTheirChangeAcceptedWhenOurChangeIsCreatedAndExistsInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.CREATED, ChangeType.UPDATED, HEX_SHA, HEX_SHA, null);
		assertThat(conflictResolutionDeterminer.determineResolution(change, () -> true, () -> false)).isEqualTo(
				ConflictResolutionStrategy.ACCEPT_THEIRS);
	}

}


