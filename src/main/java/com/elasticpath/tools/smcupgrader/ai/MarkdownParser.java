/*	Copyright 2025 Elastic Path Software Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.elasticpath.tools.smcupgrader.ai;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

/**
 * Parser for upgrade plan markdown files.
 */
public final class MarkdownParser {

	private static final Parser PARSER = Parser.builder().build();
	private static final Pattern FROM_VERSION_PATTERN = Pattern.compile("Upgrade from:\\s*(.+)");
	private static final Pattern TO_VERSION_PATTERN = Pattern.compile("Upgrade to:\\s*(.+)");
	private static final Pattern TASK_PATTERN = Pattern.compile("Task:\\s*(.+)");
	private static final Pattern TOOL_PATTERN = Pattern.compile("Tool:\\s*(.+)");
	private static final Pattern VERSION_PATTERN = Pattern.compile("Version:\\s*(.+)");
	private static final Pattern VALIDATION_PATTERN = Pattern.compile("Validation command:\\s*(.+)");
	private static final Pattern COMMIT_ALL_CHANGES_PATTERN = Pattern.compile("Commit all changes on completion:\\s*(.+)");
	private static final Pattern COMMIT_PLAN_PATTERN = Pattern.compile("Commit plan on completion:\\s*(.+)");
	private static final Pattern STATUS_PATTERN = Pattern.compile("Status:\\s*(.+)");

	private MarkdownParser() {
		// Utility class
	}

	/**
	 * Parse a markdown plan file into a PlanDocument.
	 *
	 * @param planFile the plan file to parse
	 * @return the parsed plan document
	 * @throws IOException if an error occurs reading the file
	 */
	public static PlanDocument parsePlanFile(final File planFile) throws IOException {
		byte[] bytes = Files.readAllBytes(planFile.toPath());
		String markdown = new String(bytes, StandardCharsets.UTF_8);
		return parsePlan(markdown);
	}

	/**
	 * Parse markdown content into a PlanDocument.
	 *
	 * @param markdown the markdown content
	 * @return the parsed plan document
	 */
	public static PlanDocument parsePlan(final String markdown) {
		PlanDocument plan = new PlanDocument();
		Node document = PARSER.parse(markdown);

		parseDocument(document, plan);

		return plan;
	}

	/**
	 * Parse the document tree.
	 *
	 * @param document the document node
	 * @param plan     the plan to populate
	 */
	private static void parseDocument(final Node document, final PlanDocument plan) {
		Node child = document.getFirstChild();
		AiPlanStep currentStep = null;
		StringBuilder promptBuilder = null;

		while (child != null) {
			if (child instanceof Heading) {
				Heading heading = (Heading) child;

				if (heading.getLevel() == 2) {
					// Save previous step if exists
					if (currentStep != null && promptBuilder != null) {
						currentStep.setPrompt(promptBuilder.toString().trim());
					}

					// Start new step
					String title = extractText(heading);
					if (!"Notes".equals(title)) {
						currentStep = new AiPlanStep();
						currentStep.setTitle(title);
						promptBuilder = new StringBuilder();
						plan.addStep(currentStep);
					} else {
						currentStep = null;
						promptBuilder = null;
					}
				}
			} else if (child instanceof Paragraph) {
				String paragraphText = extractText(child);

				// Try to parse metadata
				if (!parseMetadata(paragraphText, currentStep, plan)) {
					if (currentStep != null && currentStep.getTool() != null && promptBuilder != null) {
						// This is part of the prompt for claude steps
						if (promptBuilder.length() > 0) {
							promptBuilder.append("\n\n");
						}
						promptBuilder.append(paragraphText);
					}
				}
			}

			child = child.getNext();
		}

		// Save final step's prompt if needed
		if (currentStep != null && promptBuilder != null && promptBuilder.length() > 0) {
			currentStep.setPrompt(promptBuilder.toString().trim());
		}
	}

	/**
	 * Parse metadata from a paragraph.
	 *
	 * @param text        the paragraph text
	 * @param currentStep the current step being parsed (can be null)
	 * @param plan        the plan document
	 * @return true if metadata was parsed
	 */
	private static boolean parseMetadata(final String text, final AiPlanStep currentStep, final PlanDocument plan) {
		Matcher matcher;
		boolean foundMetadata = false;

		// Check for version metadata
		matcher = FROM_VERSION_PATTERN.matcher(text);
		if (matcher.find()) {
			plan.setFromVersion(matcher.group(1).trim());
			foundMetadata = true;
		}

		matcher = TO_VERSION_PATTERN.matcher(text);
		if (matcher.find()) {
			plan.setToVersion(matcher.group(1).trim());
			foundMetadata = true;
		}

		// If we found version metadata, return true
		if (foundMetadata) {
			return true;
		}

		// Check for step metadata (only if we have a current step)
		if (currentStep == null) {
			return false;
		}

		// Parse each line for metadata
		String[] lines = text.split("\n");

		for (String line : lines) {
			matcher = TASK_PATTERN.matcher(line);
			if (matcher.find()) {
				currentStep.setTask(matcher.group(1).trim());
				foundMetadata = true;
				continue;
			}

			matcher = TOOL_PATTERN.matcher(line);
			if (matcher.find()) {
				currentStep.setTool(matcher.group(1).trim());
				foundMetadata = true;
				continue;
			}

			matcher = VERSION_PATTERN.matcher(line);
			if (matcher.find()) {
				currentStep.setVersion(matcher.group(1).trim());
				foundMetadata = true;
				continue;
			}

			matcher = VALIDATION_PATTERN.matcher(line);
			if (matcher.find()) {
				currentStep.setValidationCommand(matcher.group(1).trim());
				foundMetadata = true;
				continue;
			}

			matcher = COMMIT_ALL_CHANGES_PATTERN.matcher(line);
			if (matcher.find()) {
				currentStep.setCommitAllChangesOnCompletion(Boolean.parseBoolean(matcher.group(1).trim()));
				foundMetadata = true;
				continue;
			}

			matcher = COMMIT_PLAN_PATTERN.matcher(line);
			if (matcher.find()) {
				currentStep.setCommitPlanOnCompletion(Boolean.parseBoolean(matcher.group(1).trim()));
				foundMetadata = true;
				continue;
			}

			matcher = STATUS_PATTERN.matcher(line);
			if (matcher.find()) {
				currentStep.setStatus(matcher.group(1).trim());
				foundMetadata = true;
			}
		}

		return foundMetadata;
	}

	/**
	 * Extract text content from a node and its children.
	 *
	 * @param node the node
	 * @return the text content
	 */
	private static String extractText(final Node node) {
		StringBuilder text = new StringBuilder();
		extractTextRecursive(node, text);
		return text.toString();
	}

	/**
	 * Recursively extract text from a node.
	 *
	 * @param node the node
	 * @param text the text builder
	 */
	private static void extractTextRecursive(final Node node, final StringBuilder text) {
		if (node instanceof Text) {
			text.append(((Text) node).getLiteral());
		} else if (node instanceof HardLineBreak) {
			text.append("\n");
		} else if (node instanceof SoftLineBreak) {
			text.append("\n");
		}

		Node child = node.getFirstChild();
		while (child != null) {
			extractTextRecursive(child, text);
			child = child.getNext();
		}
	}
}
