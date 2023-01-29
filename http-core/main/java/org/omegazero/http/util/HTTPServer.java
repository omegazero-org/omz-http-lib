/*
 * Copyright (C) 2023 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import java.util.function.Consumer;

import org.omegazero.http.util.HTTPResponder;
import org.omegazero.http.util.WritableSocket;

/**
 * A {@code HTTPServer} is associated with a single client connection from which it {@linkplain #receive(byte[]) receives} data, which is then processed to create {@link HTTPServerStream}s
 * usable by the application.
 *
 * @since 1.4.1
 */
public interface HTTPServer extends HTTPResponder, java.io.Closeable {

	/**
	 * Called by the application when it receives data from the client connection this {@code HTTPServer} is associated with.
	 * <p>
	 * Any protocol errors caused by malformed data passed to this method should be handled internally (for example, simply closing the connection is an appropriate minimum action).
	 * 
	 * @param data The data
	 */
	public void receive(byte[] data);

	/**
	 * Returns the client connection this {@code HTTPServer} is associated with.
	 *
	 * @return The connection
	 */
	public WritableSocket getConnection();

	/**
	 * Terminates this {@code HTTPServer} at any time, causing no further callbacks to be called and the connection to be terminated.
	 * <p>
	 * Calling this method also closes all {@linkplain #getActiveRequests(java.util.Collection) active requests}.
	 */
	@Override
	public void close();


	/**
	 * Sets the callback that is called when a new {@link HTTPServerStream} was created after receiving a request from the client.
	 * <p>
	 * No callbacks in the {@code HTTPServerStream} are called before the given callback has returned.
	 *
	 * @param callback The callback
	 * @implNote The implementation should catch exceptions thrown by the callback and return a HTTP 500 error
	 */
	public void onNewRequest(Consumer<HTTPServerStream> callback);

	/**
	 * Returns a (possibly unmodifiable) collection of all currently active request transactions managed by this {@code HTTPServer}.
	 * This is the set of all {@link HTTPServerStream}s which have not received a complete response yet.
	 *
	 * @return The collection of active {@link HTTPServerStream}s
	 */
	public java.util.Collection<HTTPServerStream> getActiveRequests();
}
