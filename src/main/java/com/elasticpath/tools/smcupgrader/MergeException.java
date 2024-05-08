/*
 * Copyright (c) Elastic Path Software Inc., 2024
 */
package com.elasticpath.tools.smcupgrader;

/**
 * Thrown when a merge exception is encountered.
 */
public class MergeException extends RuntimeException {

	/**
	 * Serial version id.
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Constructor. Creates a new exception object with the given message.
	 *
	 * @param message the reason for this exception
	 */
	public MergeException(final String message) {
		super(message);
	}
}
