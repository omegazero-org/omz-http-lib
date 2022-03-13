/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

/**
 * Special form of a {@link HTTPMessageData} object for {@link HTTPResponse}s.
 * 
 * @since 1.2.1
 */
public class HTTPResponseData extends HTTPMessageData {

	private static final long serialVersionUID = 1L;


	/**
	 * Creates a new {@link HTTPResponseData} instance.
	 * 
	 * @param httpMessage The HTTP response
	 * @param data The full or partial body of the <b>httpMessage</b>
	 */
	public HTTPResponseData(HTTPResponse httpMessage, byte[] data) {
		this(httpMessage, false, data);
	}

	/**
	 * Creates a new {@link HTTPResponseData} instance.
	 * 
	 * @param httpMessage The HTTP response
	 * @param lastPacket Whether this {@code HTTPMessageData} represents the last body data part
	 * @param data The full or partial body of the <b>httpMessage</b>
	 */
	public HTTPResponseData(HTTPResponse httpMessage, boolean lastPacket, byte[] data) {
		super(httpMessage, lastPacket, data);
	}


	/**
	 * Returns the {@link HTTPResponse} stored in this {@link HTTPResponseData} object.
	 * 
	 * @return The {@link HTTPResponse}
	 */
	@Override
	public HTTPResponse getHttpMessage() {
		return (HTTPResponse) super.httpMessage;
	}
}
