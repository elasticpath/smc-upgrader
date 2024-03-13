package com.elasticpath.tools.smcupgrader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Set;

import org.assertj.core.util.Sets;
import org.eclipse.jgit.lib.IndexDiff.StageState;
import org.junit.jupiter.api.Test;

class ChangeFactoryTest {

	private static final String FILENAME = "bill-of-materials/pom.xml";

	private final ChangeFactory changeFactory = new ChangeFactory();

	@Test
	public void testChangeCreatedWhenTheirsCreatedFile() {
		final String hexSha = "b3b047a9cf401d1b098e97a3d62a4e8acfb82a8a";
		final Set<IndexEntry> indices = Collections.singleton(new IndexEntry(FILENAME, 3, hexSha));

		final Change expected = new Change(FILENAME, ChangeType.CREATED, null, hexSha, null, null);
		final Change actual = changeFactory.createChange(FILENAME, StageState.ADDED_BY_THEM, indices);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testChangeCreatedWhenOursDeletedFile() {
		final String hexSha = "b3b047a9cf401d1b098e97a3d62a4e8acfb82a8a";
		final Set<IndexEntry> indices = Collections.singleton(new IndexEntry(FILENAME, 3, hexSha));

		final Change expected = new Change(FILENAME, ChangeType.UPDATED, ChangeType.DELETED, hexSha, null, null);
		final Change actual = changeFactory.createChange(FILENAME, StageState.DELETED_BY_US, indices);

		assertThat(actual).isEqualTo(expected);
	}

	@Test
	public void testChangeCreatedWhenBothModified() {
		final String hexSha1 = "0b8e30a3f522228015bc0d0486e1b68fd932b4b6";
		final String hexSha2 = "f68ebaca5b557abdfc5b8af362978873f3c3bbab";
		final String hexSha3 = "cc7dd7fd1bdf84c5aa333a0027b7e1d4f7a3bb36";

		final Set<IndexEntry> indices = Sets.newLinkedHashSet(
				new IndexEntry(FILENAME, 1, hexSha1),
				new IndexEntry(FILENAME, 2, hexSha2),
				new IndexEntry(FILENAME, 3, hexSha3)
		);

		final Change expected = new Change(FILENAME, ChangeType.UPDATED, ChangeType.UPDATED, hexSha3, hexSha2, null);
		final Change actual = changeFactory.createChange(FILENAME, StageState.BOTH_MODIFIED, indices);

		assertThat(actual).isEqualTo(expected);
	}

}