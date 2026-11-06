package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ConflictResolutionDeterminerTest {

	private static final String FILENAME = "extensions/database/ext-data/src/main/resources/environments/local-perf/files/conf/cache/ehcache-local.xml";

	private static final String HEX_SHA = "abcdefg";

	private static final UpstreamMatch MATCH = new UpstreamMatch("commit1", "blob1");

	private final ConflictResolutionDeterminer conflictResolutionDeterminer = new ConflictResolutionDeterminer();

	@Test
	public void testTheirChangeAcceptedWhenTheirChangeIsDelete() {
		final Change change = new Change(FILENAME, ChangeType.DELETED, ChangeType.UPDATED, HEX_SHA, HEX_SHA, null);
		final ResolutionDecision decision = conflictResolutionDeterminer.determineResolution(change, () -> Optional.of(MATCH), () -> true);
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.R_DEL);
		assertThat(decision.getUpstreamMatch()).isEmpty();
	}

	@Test
	public void testTheirChangeAcceptedWhenAllCommitsExistInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.UPDATED, ChangeType.UPDATED, HEX_SHA, HEX_SHA, null);
		final ResolutionDecision decision = conflictResolutionDeterminer.determineResolution(change, () -> Optional.of(MATCH), () -> true);
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.R_SAFE);
		assertThat(decision.getUpstreamMatch()).contains(MATCH);
	}

	@Test
	public void testManualResolutionRequiredWhenLocalCommitsDoNotExistInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.UPDATED, ChangeType.UPDATED, HEX_SHA, HEX_SHA, null);
		final ResolutionDecision decision = conflictResolutionDeterminer.determineResolution(change, Optional::empty, () -> true);
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.MANUAL_RESOLUTION_REQUIRED);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.R_MANUAL);
	}

	@Test
	public void testOurChangeAcceptedWhenBothChangesAreCreatedAndContentsAreIdentical() {
		final Change change = new Change(FILENAME, ChangeType.CREATED, ChangeType.CREATED, HEX_SHA, HEX_SHA, null);
		final ResolutionDecision decision = conflictResolutionDeterminer.determineResolution(change, Optional::empty, () -> true);
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.ACCEPT_OURS);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.R_SAME);
	}

	@Test
	public void testManualResolutionRequiredWhenBothChangesAreCreatedAndContentsAreNotIdentical() {
		final Change change = new Change(FILENAME, ChangeType.CREATED, ChangeType.CREATED, HEX_SHA, HEX_SHA, null);
		final ResolutionDecision decision = conflictResolutionDeterminer.determineResolution(change, Optional::empty, () -> false);
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.MANUAL_RESOLUTION_REQUIRED);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.R_MANUAL);
	}

	@Test
	public void testTheirChangeAcceptedWhenOurChangeIsCreatedAndExistsInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.CREATED, ChangeType.UPDATED, HEX_SHA, HEX_SHA, null);
		final ResolutionDecision decision = conflictResolutionDeterminer.determineResolution(change, () -> Optional.of(MATCH), () -> false);
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.R_SAFE);
	}

}
