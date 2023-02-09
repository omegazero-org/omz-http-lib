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
import org.omegazero.http.common.HTTPRequestData;
import org.omegazero.http.common.HTTPResponse;
import org.omegazero.http.common.HTTPResponseData;
import org.omegazero.http.util.HTTPResponder;

/**
 * A {@code HTTPServerStream} represents a single HTTP request transaction from the perspective of the server, including the incoming {@link HTTPRequest} and its associated incoming data,
 * and is used to send a corresponding response.
 *
 * @since 1.4.1
 * @see HTTPServer#onNewRequest(SpecificThrowingConsumer)
 */
public interface HTTPServerStream extends HTTPMessageStream {

	/**
	 * Returns the {@link HTTPServer} that created this {@code HTTPServerStream}.
	 *
	 * @return The {@link HTTPServer}
	 */
	public HTTPServer getServer();


	/**
	 * Sets a callback that is called when this {@code HTTPServerStream} receives data for the request.
	 * <p>
	 * If {@link HTTPRequestData#isLastPacket()} is {@code true} for a {@code HTTPRequestData} object passed to the callback, it is followed by a {@code onRequestEnded} callback.
	 *
	 * @param onRequestData The callback
	 */
	public void onRequestData(Consumer<HTTPRequestData> onRequestData);

	/**
	 * Sets a callback that is called when this {@code HTTPServerStream} has finished receiving the request and data, including optional trailers.
	 * <p>
	 * If this callback is called, it ends this {@code HTTPServerStream} and no further callbacks will be called.
	 *
	 * @param onRequestEnded The callback
	 */
	public void onRequestEnded(Consumer<HTTPMessageTrailers> onRequestEnded);


	/**
	 * Starts a server push stream with the given push request.
	 * <p>
	 * This method must be called before {@link #startResponse(HTTPResponse)}.
	 * <p>
	 * The returned new {@code HTTPServerStream} is used to send the pushed response the same way a regular response is sent. No callbacks will be called on this {@code HTTPServerStream}.
	 *
	 * @param request The push request
	 * @return The new {@code HTTPServerStream} used to send the response
	 * @throws UnsupportedOperationException If server push is not supported or not enabled ({@link HTTPServer#isServerPushEnabled()} returns {@code false})
	 */
	public default HTTPServerStream startServerPush(HTTPRequest request){
		throw new UnsupportedOperationException();
	}


	/**
	 * Starts a response with the given response header.
	 * <p>
	 * Unlike the {@code respond} methods, this does not end the response stream, but allows data to be streamed in multiple calls to {@link #sendResponseData(byte[], boolean)}.
	 * <p>
	 * This method may be called multiple times, for example to send a <i>102 Processing</i> response before the actual response.
	 *
	 * @param response The response header
	 * @throws IllegalStateException If this method is called before the {@code onRequestEnded} callback (optional)
	 */
	public void startResponse(HTTPResponse response);

	/**
	 * Streams response data after a call to {@link #startResponse(HTTPResponse)}.
	 * <p>
	 * If <b>lastPacket</b> is {@code true}, the call closes this {@code HTTPServerStream}.
	 *
	 * @param data The data
	 * @param lastPacket {@code true} if this is the last piece of data being sent
	 * @return {@code true} if the underlying connection is writable, {@code false} if the write buffer is full and data will have to be queued
	 * @throws IllegalStateException If this method is called before the {@code onRequestEnded} callback (optional)
	 * @see #onWritable(Runnable)
	 */
	public boolean sendResponseData(byte[] data, boolean lastPacket);

	/**
	 * Ends the response with optional trailers, if supported.
	 * This method implicitly closes this {@code HTTPServerStream}.
	 *
	 * @param trailers If not {@code null}, the trailers to send
	 */
	public default void endResponse(HTTPMessageTrailers trailers){
		this.sendResponseData(HTTPResponder.EMPTY_BYTE_ARRAY, true);
	}


	/**
	 * Responds to this {@code HTTPServerStream} with the given full <b>response</b>, if no response was already sent.
	 * This method implicitly closes this {@code HTTPServerStream}.
	 * <p>
	 * See {@link HTTPResponder#respond(HTTPRequest, HTTPResponseData)} for additional information.
	 *
	 * @param response The response
	 * @see #respond(int, byte[], String...)
	 * @see #startResponse(HTTPResponse)
	 */
	public void respond(HTTPResponseData response);

	/**
	 * Responds to this {@code HTTPServerStream} with a new HTTP response with the given <b>status</b>, <b>data</b> and <b>headers</b>, if no response was already sent.
	 * This method implicitly closes this {@code HTTPServerStream}.
	 * <p>
	 * See {@link HTTPResponder#respond(HTTPRequest, int, byte[], String...)} for additional information.
	 *
	 * @param status The status code of the response
	 * @param data The data to send in the response
	 * @param headers Headers to send in the response
	 * @see #respond(HTTPResponseData)
	 * @see #startResponse(HTTPResponse)
	 */
	public void respond(int status, byte[] data, String... headers);
}
