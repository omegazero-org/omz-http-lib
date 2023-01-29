/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.common.HTTPResponseData;

/**
 * A {@code HTTPResponder} is used for responding to {@link HTTPRequest}s given a {@linkplain HTTPResponseData response and body}.
 * 
 * @since 1.2.1
 */
public interface HTTPResponder {

	/**
	 * A byte array with length zero.
	 *
	 * @since 1.4.1
	 */
	public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];


	/**
	 * Responds to the given <b>request</b> with the given <b>response</b>, if the request has not already received a response.
	 * <p>
	 * A responder may set additional, edit, or delete any headers in the HTTP response.
	 * 
	 * @param request The request to respond to
	 * @param response The response
	 * @see #respond(HTTPRequest, int, byte[], String...)
	 */
	public void respond(HTTPRequest request, HTTPResponseData response);

	/**
	 * Responds to the given <b>request</b> with a new HTTP response with the given <b>status</b>, <b>data</b> and <b>headers</b>, if the request has not already received a
	 * response.
	 * <p>
	 * In the <b>headers</b> array, each value at an even index (starting at 0) is a header key (name), followed by the values at odd indices. If the array length is not a
	 * multiple of 2, the last element is ignored. For example, an array like <code>{"x-example", "123", "x-another-header", "value here"}</code> will set two headers in the
	 * response with names "x-example" and "x-another-header" and values "123" and "value here", respectively.
	 * <p>
	 * A responder may set additional, edit, or delete any headers in the HTTP response.
	 * 
	 * @param request The request to respond to
	 * @param status The status code of the response
	 * @param data The data to send in the response
	 * @param headers Headers to send in the response. See explanation in description
	 * @see #respond(HTTPRequest, HTTPResponseData)
	 */
	public void respond(HTTPRequest request, int status, byte[] data, String... headers);
}
