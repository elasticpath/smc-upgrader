package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Test class for {@link DiffResolutionDeterminer}
 */
class DiffResolutionDeterminerTest {

	private static final String FILENAME = "pom.xml";

	private static final UpstreamMatch MATCH = new UpstreamMatch("commit1", "blob1");

	private final DiffResolutionDeterminer diffResolutionDeterminer = new DiffResolutionDeterminer();

	@Test
	public void verifyTheirChangeAcceptedWhenAllCommitsExistInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.UPDATED, null, "theirs", "ours", null);
		final ResolutionDecision decision = diffResolutionDeterminer.determineResolution(change, () -> Optional.of(MATCH));
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.D_REPLACE);
		assertThat(decision.getUpstreamMatch()).contains(MATCH);
	}

	@Test
	public void verifyDeleteRuleWhenTargetReleaseDoesNotHaveFile() {
		final Change change = new Change(FILENAME, ChangeType.DELETED, null, null, "ours", null);
		final ResolutionDecision decision = diffResolutionDeterminer.determineResolution(change, () -> Optional.of(MATCH));
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.D_DELETE);
	}

	@Test
	public void verifyReaddRuleWhenLocalTreeDoesNotHaveFile() {
		final Change change = new Change(FILENAME, ChangeType.CREATED, null, "theirs", null, null);
		final ResolutionDecision decision = diffResolutionDeterminer.determineResolution(change, () -> Optional.of(MATCH));
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.ACCEPT_THEIRS);
		assertThat(decision.getRule()).isEqualTo(ResolutionRule.D_READD);
	}

	@Test
	public void verifyOurChangeUsedWhenNotAllCommitsExistInUpstreamRepo() {
		final Change change = new Change(FILENAME, ChangeType.UPDATED, null, "theirs", "ours", null);
		final ResolutionDecision decision = diffResolutionDeterminer.determineResolution(change, Optional::empty);
		assertThat(decision.getStrategy()).isEqualTo(ConflictResolutionStrategy.ACCEPT_OURS);
		assertThat(decision.getRule()).isNull();
	}

}
