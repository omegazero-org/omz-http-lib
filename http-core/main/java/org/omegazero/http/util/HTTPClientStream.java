/*
 * Copyright (C) 2023 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import java.util.function.Consumer;

import org.omegazero.http.common.HTTPMessageTrailers;
import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.common.HTTPResponse;
import org.omegazero.http.common.HTTPResponseData;
import org.omegazero.http.util.HTTPResponder;

/**
 * A {@code HTTPClientStream} represents a single HTTP request transaction from the perspective of the client, including the outgoing {@link HTTPRequest} and its data,
 * and is used for receiving the corresponding response.
 *
 * @since 1.4.1
 * @see HTTPClient#newRequest(HTTPRequest)
 */
public interface HTTPClientStream extends HTTPMessageStream {

	/**
	 * Returns the {@link HTTPClient} that created this {@code HTTPClientStream}.
	 *
	 * @return The {@link HTTPClient}
	 */
	public HTTPClient getClient();


	/**
	 * Starts the request passed in a constructor.
	 * <p>
	 * This method must be followed by at least one call to {@link #sendRequestData(byte[], boolean)} or {@link #endRequest(HTTPMessageTrailers)}.
	 *
	 * @throws IllegalStateException If this method was called already (optional)
	 */
	public void startRequest();

	/**
	 * Streams request data.
	 *
	 * @param data The data
	 * @param lastPacket {@code true} if this is the last piece of data being sent
	 * @return {@code true} if the underlying connection is writable, {@code false} if the write buffer is full and data will have to be queued
	 * @throws IllegalStateException If the request has ended already (optional)
	 * @see #onWritable(Runnable)
	 */
	public boolean sendRequestData(byte[] data, boolean lastPacket);

	/**
	 * Ends this request, allowing the server to send the response. Optional trailers are sent with the request, if supported.
	 *
	 * @param trailers If not {@code null}, the trailers to send
	 */
	public default void endRequest(HTTPMessageTrailers trailers){
		this.sendRequestData(org.omegazero.http.util.HTTPResponder.EMPTY_BYTE_ARRAY, true);
	}


	/**
	 * Returns the received response of this {@code HTTPClientStream}, or {@code null} if no response was received yet.
	 *
	 * @return The response
	 */
	public HTTPResponse getResponse();


	/**
	 * Sets a callback that is called when this {@code HTTPClientStream} receives a response.
	 * <p>
	 * No other callbacks in this {@code HTTPClientStream} are called before the given callback has returned.
	 * <p>
	 * This callback may be called multiple times when receiving multiple responses (for example, a <i>102 Processing</i> followed by the actual response,
	 * see also: {@link HTTPResponse#isIntermediateMessage()}).
	 *
	 * @param onResponse The callback
	 */
	public void onResponse(Consumer<HTTPResponse> onResponse);

	/**
	 * Sets a callback that is called when this {@code HTTPClientStream} receives data for the response.
	 * <p>
	 * If {@link HTTPResponseData#isLastPacket()} is {@code true} for a {@code HTTPResponseData} object passed to the callback, it is followed by a {@code onResponseEnded} callback.
	 *
	 * @param onResponseData The callback
	 */
	public void onResponseData(Consumer<HTTPResponseData> onResponseData);

	/**
	 * Sets a callback that is called when this {@code HTTPClientStream} has finished receiving the response and data, including optional trailers.
	 * <p>
	 * If this callback is called, this {@code HTTPClientStream} is implicitly closed and no further callbacks will be called.
	 *
	 * @param onResponseEnded The callback
	 */
	public void onResponseEnded(Consumer<HTTPMessageTrailers> onResponseEnded);
}
