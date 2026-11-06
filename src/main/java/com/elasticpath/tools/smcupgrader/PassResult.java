package com.elasticpath.tools.smcupgrader;

import java.util.Collections;
import java.util.List;

/**
 * The journal entries from one pass of the merge step, and the number of files the pass left unchanged.
 */
public final class PassResult {
	private final List<JournalEntry> entries;
	private final int unchangedCount;

	/**
	 * Constructor.
	 *
	 * @param entries        one entry for each file the pass handled, sorted by path
	 * @param unchangedCount the number of files the pass looked at and left unchanged without an entry
	 */
	public PassResult(final List<JournalEntry> entries, final int unchangedCount) {
		this.entries = Collections.unmodifiableList(entries);
		this.unchangedCount = unchangedCount;
	}

	public List<JournalEntry> getEntries() {
		return entries;
	}

	public int getUnchangedCount() {
		return unchangedCount;
	}
}
