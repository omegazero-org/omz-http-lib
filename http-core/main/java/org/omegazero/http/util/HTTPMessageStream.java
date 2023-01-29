/*
 * Copyright (C) 2023 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import java.util.function.Consumer;

import org.omegazero.http.common.HTTPRequest;

/**
 * A {@code HTTPMessageStream} represents a single HTTP request transaction.
 *
 * @since 1.4.1
 * @see HTTPClientStream
 * @see HTTPServerStream
 */
public interface HTTPMessageStream extends java.io.Closeable {

	/**
	 * Returns the request of this {@code HTTPMessageStream}.
	 *
	 * @return The request
	 */
	public HTTPRequest getRequest();

	/**
	 * Sets whether this stream should continue receiving data.
	 * <p>
	 * Disabling this will attempt to reduce or block incoming data passed to the data callback, if supported by the underlying protocol or transport.
	 *
	 * @param receiveData {@code true} to continue receiving data
	 */
	public void setReceiveData(boolean receiveData);

	/**
	 * Sets a callback that is called when this {@code HTTPMessageStream} has cleared its write buffer after a {@code sendData} call returned {@code false}.
	 *
	 * @param onWritable The callback
	 */
	public void onWritable(Runnable onWritable);

	/**
	 * Sets a callback that is called when this {@code HTTPMessageStream} encounters a fatal error. Usually, after this callback is called, this {@code HTTPMessageStream} is closed.
	 *
	 * @param onError The callback
	 */
	public void onError(Consumer<Exception> onError);


	/**
	 * Returns {@code true} if this {@code HTTPMessageStream} has closed, either forcibly (via {@link #close()}) or because the request transaction has completed.
	 *
	 * @return {@code true} if closed
	 */
	public boolean isClosed();

	/**
	 * Terminates this {@code HTTPMessageStream} at any time, causing no further callbacks to be called and the transaction with the peer to end abnormally.
	 * <p>
	 * Calling this method after this {@code HTTPMessageStream} ended (successfully or abnormally) has no effects.
	 */
	@Override
	public void close();
}
