/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

/**
 * Special form of a {@link HTTPMessageData} object for {@link HTTPRequest}s.
 * 
 * @since 1.2.1
 */
public class HTTPRequestData extends HTTPMessageData {

	private static final long serialVersionUID = 1L;


	/**
	 * Creates a new {@link HTTPRequestData} instance.
	 * 
	 * @param httpMessage The HTTP request
	 * @param data The full or partial body of the <b>httpMessage</b>
	 */
	public HTTPRequestData(HTTPRequest httpMessage, byte[] data) {
		this(httpMessage, false, data);
	}

	/**
	 * Creates a new {@link HTTPRequestData} instance.
	 * 
	 * @param httpMessage The HTTP request
	 * @param lastPacket Whether this {@code HTTPMessageData} represents the last body data part
	 * @param data The full or partial body of the <b>httpMessage</b>
	 */
	public HTTPRequestData(HTTPRequest httpMessage, boolean lastPacket, byte[] data) {
		super(httpMessage, lastPacket, data);
	}


	/**
	 * Returns the {@link HTTPRequest} stored in this {@link HTTPRequestData} object.
	 * 
	 * @return The {@link HTTPRequest}
	 */
	@Override
	public HTTPRequest getHttpMessage() {
		return (HTTPRequest) super.httpMessage;
	}
}
