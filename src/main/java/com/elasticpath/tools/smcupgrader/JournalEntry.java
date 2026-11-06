package com.elasticpath.tools.smcupgrader;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One fixed-field entry in the upgrade journal, rendered as a fenced YAML block.
 */
public final class JournalEntry {
	/** Repository-relative path of the file. */
	public static final String FILE = "file";
	/** Title of the step that made the decision. */
	public static final String STEP = "step";
	/** The pass: conflict, diff, ast-grep, or llm. */
	public static final String PASS = "pass";
	/** Two-letter Git stage of a conflicted file. */
	public static final String GIT_STAGE = "git_stage";
	/** Rule code, or recipe and rule ID. */
	public static final String RULE = "rule";
	/** What was done to the file. */
	public static final String ACTION = "action";
	/** The part of the file the decision applies to. */
	public static final String REGION = "region";
	/** Whether the file is extension or core code. */
	public static final String EXTENSION_OR_CORE = "extension_or_core";
	/** Upstream commit whose contents matched. */
	public static final String MATCHED_UPSTREAM_COMMIT = "matched_upstream_commit";
	/** Blob of the file in the matched upstream commit. */
	public static final String MATCHED_UPSTREAM_BLOB = "matched_upstream_blob";
	/** Upstream branch that contains the matched commit. */
	public static final String MATCHED_UPSTREAM_BRANCH = "matched_upstream_branch";
	/** Blob of the file after the merge and before the diff pass. */
	public static final String RAW_MERGE_BLOB = "raw_merge_blob";
	/** Blob of the file after the diff pass. */
	public static final String FINAL_BLOB = "final_blob";
	/** Why the decision was made. */
	public static final String REASON = "reason";
	/** What supports the decision. */
	public static final String EVIDENCE = "evidence";
	/** The alternatives that were rejected, and why. */
	public static final String REJECTED_ALTERNATIVES = "rejected_alternatives";
	/** Whether a person must review the decision. */
	public static final String NEEDS_REVIEW = "needs_review";

	/** Region value for decisions that apply to the whole file. */
	public static final String WHOLE_FILE = "whole file";

	private static final String EXTENSIONS_PREFIX = "extensions/";

	private static final List<String> FIELD_ORDER = Arrays.asList(FILE, STEP, PASS, GIT_STAGE, RULE, ACTION, REGION, EXTENSION_OR_CORE,
			MATCHED_UPSTREAM_COMMIT, MATCHED_UPSTREAM_BLOB, MATCHED_UPSTREAM_BRANCH, RAW_MERGE_BLOB, FINAL_BLOB,
			REASON, EVIDENCE, REJECTED_ALTERNATIVES, NEEDS_REVIEW);

	private final Map<String, Object> fields = new LinkedHashMap<>();

	/**
	 * Create an entry for the given file. The {@code extension_or_core} field is derived from the path.
	 *
	 * @param path repository-relative path of the file
	 * @param pass the pass that made the decision
	 * @return the entry
	 */
	public static JournalEntry forFile(final String path, final String pass) {
		JournalEntry entry = new JournalEntry();
		// Insert every known field first, so rendered blocks always list fields in the same order
		for (String name : FIELD_ORDER) {
			entry.with(name, null);
		}
		entry.with(FILE, path);
		entry.with(PASS, pass);
		entry.with(EXTENSION_OR_CORE, extensionOrCore(path));
		return entry;
	}

	/**
	 * Classify a path as extension or core code.
	 *
	 * @param path repository-relative path
	 * @return {@code extension} for paths under {@code extensions/}, otherwise {@code core}
	 */
	public static String extensionOrCore(final String path) {
		return path.startsWith(EXTENSIONS_PREFIX) ? "extension" : "core";
	}

	/**
	 * Set a field. A null value keeps the field position but leaves it out of the rendered block.
	 *
	 * @param name  the field name
	 * @param value the value: a String, a Boolean, or null
	 * @return this entry
	 */
	public JournalEntry with(final String name, final Object value) {
		fields.put(name, value);
		return this;
	}

	/**
	 * Get a field value.
	 *
	 * @param name the field name
	 * @return the value, or null
	 */
	public Object get(final String name) {
		return fields.get(name);
	}

	public Map<String, Object> getFields() {
		return Collections.unmodifiableMap(fields);
	}

	/**
	 * Render the entry as a fenced YAML block. Strings are double-quoted and escaped. Booleans are unquoted.
	 *
	 * @return the rendered block, ending with a newline
	 */
	public String render() {
		StringBuilder builder = new StringBuilder("```yaml\n");
		for (Map.Entry<String, Object> field : fields.entrySet()) {
			Object value = field.getValue();
			if (value == null) {
				continue;
			}
			builder.append(field.getKey()).append(": ");
			if (value instanceof Boolean) {
				builder.append(value);
			} else {
				builder.append(quote(value.toString()));
			}
			builder.append('\n');
		}
		return builder.append("```\n").toString();
	}

	private static String quote(final String value) {
		String escaped = value.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\r", "\\r")
				.replace("\n", "\\n")
				.replace("\t", "\\t");
		return "\"" + escaped + "\"";
	}
}
