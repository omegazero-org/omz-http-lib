/*
 * Copyright (C) 2023 omegazero.org, warp03
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

/**
 * An exception thrown when a {@link org.omegazero.http.util.HTTPMessageStream} is closed unexpectedly.
 *
 * @since 1.4.1
 */
public class MessageStreamClosedException extends HTTPException {

	private static final long serialVersionUID = 1L;

	private final CloseReason closeReason;

	/**
	 * Creates a new {@link MessageStreamClosedException} with the given close reason.
	 *
	 * @param closeReason The close reason
	 */
	public MessageStreamClosedException(CloseReason closeReason) {
		this(closeReason, null, null);
	}

	/**
	 * Creates a new {@link MessageStreamClosedException} with the given close reason and cause.
	 *
	 * @param closeReason The close reason
	 * @param cause The cause
	 */
	public MessageStreamClosedException(CloseReason closeReason, Throwable cause) {
		this(closeReason, null, cause);
	}

	/**
	 * Creates a new {@link MessageStreamClosedException} with the given close reason, detail message, and cause.
	 *
	 * @param closeReason The close reason
	 * @param msg The detail message
	 * @param cause The cause
	 */
	public MessageStreamClosedException(CloseReason closeReason, String msg, Throwable cause) {
		super("Stream closed: " + closeReason + (msg != null ? " (" + msg + ")" : ""), cause);
		this.closeReason = closeReason;
	}


	/**
	 * Returns the close reason passed in the constructor.
	 *
	 * @return The close reason
	 */
	public CloseReason getCloseReason(){
		return this.closeReason;
	}


	public static enum CloseReason {
		UNKNOWN, PROTOCOL_ERROR, INTERNAL_ERROR, CANCEL, REFUSED, ENHANCE_YOUR_CALM, PROTOCOL_DOWNGRADE;
	}
}
