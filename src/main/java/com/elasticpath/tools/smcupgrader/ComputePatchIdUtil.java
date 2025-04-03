package com.elasticpath.tools.smcupgrader;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.PatchIdDiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

public class ComputePatchIdUtil {

	public String computePatchId(final Repository repository, final RevCommit commit, final String path) throws IOException {
		try (RevWalk revWalk = new RevWalk(repository)) {
			RevCommit parent = commit.getParentCount() > 0 ? revWalk.parseCommit(commit.getParent(0)) : null;
			try (PatchIdDiffFormatter diffFormatter = new PatchIdDiffFormatter()) {
				diffFormatter.setRepository(repository);
				diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
				diffFormatter.setDetectRenames(true);

				AbstractTreeIterator oldTree = getTreeIterator(repository, parent);
				AbstractTreeIterator newTree = getTreeIterator(repository, commit);

				List<DiffEntry> diffs = diffFormatter.scan(oldTree, newTree).stream()
						.filter(diff -> diff.getNewPath().equals(path) || diff.getOldPath().equals(path))
						.collect(Collectors.toList());
				diffFormatter.format(diffs);
				ObjectId patchId = diffFormatter.getCalulatedPatchId();
				if (patchId != null) {
					return patchId.getName();
				}
			}
		}
		return null;
	}

	private static AbstractTreeIterator getTreeIterator(final Repository repository, final RevCommit commit) throws IOException {
		if (commit == null) {
			return new CanonicalTreeParser();
		}
		ObjectReader reader = repository.newObjectReader();
		return new CanonicalTreeParser(null, reader, commit.getTree().getId());
	}
}
