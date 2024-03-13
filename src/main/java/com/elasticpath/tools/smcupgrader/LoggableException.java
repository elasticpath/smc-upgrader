package com.elasticpath.tools.smcupgrader;

/**
 * An exception that should be logged and reported to the end-user caller.
 */
public class LoggableException extends RuntimeException {
	/**
	 * Serial version id.
	 */
	private static final long serialVersionUID = 5000000001L;

	/**
	 * Creates a new exception object with the given message.
	 *
	 * @param message the reason for this exception
	 */
	public LoggableException(final String message) {
		super(message);
	}

	/**
	 * Creates a new exception object using the given message and cause exception.
	 *
	 * @param message the reason for this exception
	 * @param cause   the <code>Throwable</code> that caused this exception
	 */
	public LoggableException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
