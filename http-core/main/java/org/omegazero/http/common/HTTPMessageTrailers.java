/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

/**
 * Represents HTTP trailers (headers sent after a request or response body).
 * 
 * @since 1.2.1
 */
public class HTTPMessageTrailers extends HTTPHeaderContainer {

	private static final long serialVersionUID = 1L;


	private final HTTPMessage httpMessage;

	/**
	 * Creates a new {@link HTTPMessageTrailers} instance.
	 * <p>
	 * Note that no deep copy of the <b>trailers</b> will be created, meaning changes made to the given {@link HTTPHeaderContainer} will reflect on this
	 * {@link HTTPMessageTrailers}.
	 * 
	 * @param httpMessage The {@link HTTPMessage} these trailers belong to
	 * @param trailers The trailers
	 */
	public HTTPMessageTrailers(HTTPMessage httpMessage, HTTPHeaderContainer trailers) {
		super(trailers != null ? trailers.headerFields : null);
		this.httpMessage = httpMessage;
	}

	/**
	 * Copies the given {@link HTTPMessageTrailers}.
	 * <p>
	 * This does not create a copy of the stored {@link #getHttpMessage() HTTPMessage}.
	 * 
	 * @param trailers The {@code HTTPMessageTrailers} to copy from
	 * @see HTTPHeaderContainer#HTTPHeaderContainer(HTTPHeaderContainer)
	 */
	public HTTPMessageTrailers(HTTPMessageTrailers trailers) {
		super(trailers);
		this.httpMessage = trailers.httpMessage;
	}


	/**
	 * Returns the {@link HTTPMessage} this trailer data belongs to.
	 * 
	 * @return The <code>HTTPMessage</code>
	 */
	public HTTPMessage getHttpMessage() {
		return this.httpMessage;
	}
}
