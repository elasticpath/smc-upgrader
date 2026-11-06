package com.elasticpath.tools.smcupgrader;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes the upgrade journal ({@link Constants#JOURNAL_FILE_NAME}) in the root of the working directory.
 *
 * <p>The journal has one {@code ## <step title>} section for each step, and one {@code ### } subsection for each run of
 * a step. This class only appends: it never changes or removes earlier content. If the last step section in the file
 * belongs to a different step, a new section with the same title is appended, so that new content is always under the
 * right heading.</p>
 */
public class UpgradeJournal {
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	private static final String STEP_HEADING_PREFIX = "## ";
	private static final Pattern FILE_FIELD_PATTERN = Pattern.compile("^\\s*file:\\s*\"?(.*?)\"?\\s*$", Pattern.MULTILINE);

	private final File journalFile;
	private final Clock clock;
	private String currentStep;

	/**
	 * Constructor.
	 *
	 * @param workingDir the root of the codebase being upgraded
	 */
	public UpgradeJournal(final File workingDir) {
		this(workingDir, Clock.systemDefaultZone());
	}

	/**
	 * Constructor.
	 *
	 * @param workingDir the root of the codebase being upgraded
	 * @param clock      the clock used for run timestamps
	 */
	public UpgradeJournal(final File workingDir, final Clock clock) {
		this.journalFile = new File(workingDir, Constants.JOURNAL_FILE_NAME);
		this.clock = clock;
	}

	/**
	 * Start a new run subsection, titled {@code Run <date and time>}, under the section for the given step.
	 *
	 * @param stepTitle the step title
	 */
	public void startRun(final String stepTitle) {
		startTimestampedSubsection(stepTitle, "Run");
	}

	/**
	 * Start a new subsection, titled {@code <label> <date and time>}, under the section for the given step.
	 *
	 * @param stepTitle the step title
	 * @param label     the subsection label
	 */
	public void startTimestampedSubsection(final String stepTitle, final String label) {
		startSubsection(stepTitle, label + " " + timestamp());
	}

	/**
	 * Start a new subsection with the given heading under the section for the given step.
	 *
	 * @param stepTitle the step title
	 * @param heading   the subsection heading, without the leading {@code ###}
	 */
	public void startSubsection(final String stepTitle, final String heading) {
		StringBuilder text = new StringBuilder();
		String stepHeading = STEP_HEADING_PREFIX + stepTitle;
		if (!stepHeading.equals(lastStepHeading())) {
			text.append('\n').append(stepHeading).append('\n');
		}
		text.append("\n### ").append(heading).append('\n');
		append(text.toString());
		currentStep = stepTitle;
	}

	/**
	 * Append a paragraph of narrative text.
	 *
	 * @param paragraph the text
	 */
	public void appendParagraph(final String paragraph) {
		append("\n" + paragraph + "\n");
	}

	/**
	 * Append entries. Entries without a {@code step} field get the title of the current step.
	 *
	 * @param entries the entries
	 */
	public void appendEntries(final List<JournalEntry> entries) {
		StringBuilder text = new StringBuilder();
		for (JournalEntry entry : entries) {
			if (entry.get(JournalEntry.STEP) == null) {
				entry.with(JournalEntry.STEP, currentStep);
			}
			text.append('\n').append(entry.render());
		}
		if (text.length() > 0) {
			append(text.toString());
		}
	}

	/**
	 * Read the whole journal.
	 *
	 * @return the journal content, or an empty string if the journal does not exist
	 */
	public String read() {
		if (!journalFile.exists()) {
			return "";
		}
		try {
			return new String(Files.readAllBytes(journalFile.toPath()), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Count the {@code file:} fields in journal content, by path.
	 *
	 * @param content journal content
	 * @return the number of {@code file:} fields for each path
	 */
	public static Map<String, Integer> countFileFields(final String content) {
		Map<String, Integer> counts = new HashMap<>();
		Matcher matcher = FILE_FIELD_PATTERN.matcher(content);
		while (matcher.find()) {
			counts.merge(normalizePath(matcher.group(1)), 1, Integer::sum);
		}
		return counts;
	}

	private static String normalizePath(final String path) {
		String normalized = path.trim().replace('\\', '/');
		while (normalized.startsWith("./")) {
			normalized = normalized.substring(2);
		}
		return normalized;
	}

	private String lastStepHeading() {
		String lastHeading = null;
		for (String line : read().split("\n")) {
			String trimmed = line.trim();
			if (trimmed.startsWith(STEP_HEADING_PREFIX)) {
				lastHeading = trimmed;
			}
		}
		return lastHeading;
	}

	private String timestamp() {
		return LocalDateTime.now(clock).format(TIMESTAMP_FORMAT);
	}

	private void append(final String text) {
		String content = read();
		String toWrite;
		if (content.isEmpty()) {
			toWrite = text.replaceFirst("^\n+", "");
		} else if (content.endsWith("\n")) {
			toWrite = text;
		} else {
			toWrite = "\n" + text;
		}
		try {
			Files.write(journalFile.toPath(), toWrite.getBytes(StandardCharsets.UTF_8),
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
