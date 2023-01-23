/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

/**
 * An exception thrown when a malformed or otherwise invalid {@link HTTPMessage} is encountered.
 * 
 * @since 1.2.1
 */
public class InvalidHTTPMessageException extends HTTPException {

	private static final long serialVersionUID = 1L;


	private final boolean msgUserVisible;

	/**
	 * Creates a new {@link InvalidHTTPMessageException} with no detail message.
	 */
	public InvalidHTTPMessageException() {
		this(null, false);
	}

	/**
	 * Creates a new {@link InvalidHTTPMessageException} with the given detail message.
	 * 
	 * @param msg The detail message
	 */
	public InvalidHTTPMessageException(String msg) {
		this(msg, false);
	}

	/**
	 * Creates a new {@link InvalidHTTPMessageException} with the given detail message.
	 * 
	 * @param msg The detail message
	 * @param msgUserVisible Whether the given <b>msg</b> may be shown to external parties
	 */
	public InvalidHTTPMessageException(String msg, boolean msgUserVisible) {
		super(msg);
		this.msgUserVisible = msgUserVisible;
	}

	/**
	 * Creates a new {@link InvalidHTTPMessageException} with the given detail message and cause.
	 * 
	 * @param msg The detail message
	 * @param cause The cause
	 */
	public InvalidHTTPMessageException(String msg, Throwable cause) {
		this(msg, cause, false);
	}

	/**
	 * Creates a new {@link InvalidHTTPMessageException} with the given detail message and cause.
	 * 
	 * @param msg The detail message
	 * @param cause The cause
	 * @param msgUserVisible Whether the given <b>msg</b> may be shown to external parties
	 */
	public InvalidHTTPMessageException(String msg, Throwable cause, boolean msgUserVisible) {
		super(msg, cause);
		this.msgUserVisible = msgUserVisible;
	}


	/**
	 * Returns whether the message of this {@code Throwable} may be shown to external parties, for example in a {@code 400 Bad Request} response body.
	 * 
	 * @return {@code true} if the exception message may be shown to external parties
	 */
	public boolean isMsgUserVisible() {
		return this.msgUserVisible;
	}
}
