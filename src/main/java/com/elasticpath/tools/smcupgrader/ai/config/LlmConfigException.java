package com.elasticpath.tools.smcupgrader.ai.config;

/**
 * Thrown when the CLI LLM configuration in {@code ~/.smc-upgrader.json} is invalid.
 */
public class LlmConfigException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructor.
	 *
	 * @param message the detail message describing the misconfiguration
	 */
	public LlmConfigException(final String message) {
		super(message);
	}
}
