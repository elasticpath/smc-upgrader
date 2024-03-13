package com.elasticpath.tools.smcupgrader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Parent class for conflict resolvers.
 */
public class AbstractConflictResolver {
	private final GitClient gitClient;

	/**
	 * Constructor.
	 *
	 * @param gitClient the git client
	 */
	public AbstractConflictResolver(final GitClient gitClient) {
		this.gitClient = gitClient;
	}

	/**
	 * Resolve the given conflict by accepting our change.
	 *
	 * @param change the change representing the conflict to resolve
	 */
	protected void resolveConflictOurs(final Change change) {
		if (change.getOurChangeType() == ChangeType.CREATED || change.getOurChangeType() == ChangeType.UPDATED) {
			replaceContents(change.getPath(), change.getOurVersionSHA(), null);
			stage(change.getPath());
		}

		// resolution for other change types not implemented as currently no use case exists to support them
	}

	/**
	 * Resolve the given conflict by accepting the incoming change.
	 *
	 * @param change the change representing the conflict to resolve
	 */
	protected void resolveConflictTheirs(final Change change) {
		if (change.getTheirChangeType() == ChangeType.DELETED || change.getOurChangeType() == ChangeType.CREATED) {
			delete(change.getPath());
			return;
		}

		replaceContents(change.getPath(), change.getTheirVersionSHA(), change.getTheirFilePermissions());
		stage(change.getPath());
	}

	private void replaceContents(final String repoFile, final String hexSha, final FilePermissions filePermissions) {
		final Path filePath = Paths.get(gitClient.getWorkingDir().getAbsolutePath(), repoFile);
		try {
			Files.createDirectories(filePath.getParent());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		final File file = filePath.toFile();
		try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
			gitClient.writeBlobContents(hexSha, fileOutputStream);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}

		if (filePermissions != null) {
			file.setReadable(filePermissions.isReadable());
			file.setWritable(filePermissions.isWritable());
			file.setExecutable(filePermissions.isExecutable());
		}
	}

	private void stage(final String path) {
		gitClient.stage(path);
	}

	private void delete(final String path) {
		gitClient.delete(path);
	}

	protected GitClient getGitClient() {
		return gitClient;
	}
}
