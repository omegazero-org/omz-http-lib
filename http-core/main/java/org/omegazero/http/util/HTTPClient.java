/*
 * Copyright (C) 2023 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import org.omegazero.http.common.HTTPException;
import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.util.WritableSocket;

/**
 * A {@code HTTPClient} is associated with a single connection to a server where requests can be sent to using {@link #newRequest(HTTPRequest)}.
 * <p>
 * The application must call {@link #receive(byte[])} when receiving data on the connection to the server.
 *
 * @since 1.4.1
 */
public interface HTTPClient extends java.io.Closeable {

	/**
	 * Called by the application when it receives data from the server connection this {@code HTTPClient} is associated with.
	 *
	 * @param data The data
	 * @throws HTTPException If the given data could not be successfully processed or is otherwise malformed
	 */
	public void receive(byte[] data) throws HTTPException;

	/**
	 * Returns the connection to the server.
	 *
	 * @return The connection
	 */
	public WritableSocket getConnection();

	/**
	 * Terminates this {@code HTTPClient} at any time, causing the connection to be terminated.
	 * <p>
	 * Calling this method also closes all {@linkplain #getActiveRequests(java.util.Collection) active requests}.
	 */
	@Override
	public void close();


	/**
	 * Enables or disabled server push.
	 * <p>
	 * If server push is not supported by the underlying protocol, this setting has no effect.
	 *
	 * @param enabled {@code true} to enable server push
	 */
	public default void setServerPushEnabled(boolean enabled){
	}


	/**
	 * Creates a new {@code HTTPClientStream} for the given <b>request</b>.
	 * <p>
	 * It is possible that this {@code HTTPClient} cannot create a new request stream. In this case, {@code null} is returned.
	 *
	 * @return The {@code HTTPClientStream}, or {@code null} if no new request can be sent
	 * @throws IllegalStateException If this {@code HTTPClient} is closed (optional)
	 * @see #getActiveRequests()
	 * @see #getMaxConcurrentRequestCount()
	 */
	public HTTPClientStream newRequest(HTTPRequest request);

	/**
	 * Returns a (possibly unmodifiable) collection of all currently active request transactions managed by this {@code HTTPClient}.
	 * This is the set of all {@link HTTPClientStream}s which have not received a complete response yet.
	 *
	 * @return The collection of active {@link HTTPClientStream}s
	 */
	public java.util.Collection<HTTPClientStream> getActiveRequests();

	/**
	 * Returns the maximum number of concurrent active requests this {@code HTTPClient} can be used for.
	 *
	 * @return The maximum number of concurrent requests
	 */
	public int getMaxConcurrentRequestCount();
}
