/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Provides an abstraction layer for outgoing data over a socket.
 * 
 * @since 1.2.1
 */
public interface WritableSocket extends Closeable, Flushable {


	/**
	 * Writes the given <b>data</b> to this {@link WritableSocket}.
	 * 
	 * @param data The data
	 * @throws IOException If an IO error occurs
	 * @see #write(byte[], int, int)
	 */
	public default void write(byte[] data) throws IOException {
		this.write(data, 0, data.length);
	}

	/**
	 * Writes the given <b>data</b> to this {@link WritableSocket}.
	 * 
	 * @param data The data
	 * @param offset The index to start writing from
	 * @param length The number of bytes to write, starting at <b>offset</b>
	 * @throws IOException If an IO error occurs
	 * @see #write(byte[])
	 */
	public void write(byte[] data, int offset, int length) throws IOException;

	/**
	 * Flushes any remaining data in this {@link WritableSocket}'s write buffer.
	 * 
	 * @throws IOException If an IO error occurs
	 */
	@Override
	public void flush() throws IOException;

	/**
	 * Returns {@code true} if this {@link WritableSocket} is connected, meaning data can still be written. This is distinct from {@link #isWritable()} because a socket may
	 * still be connected, while not being writable, because data is backed up locally.
	 * 
	 * @return {@code true} if this {@code WritableSocket} is connected
	 * @see #isWritable()
	 */
	public boolean isConnected();

	/**
	 * Returns {@code true} if this {@link WritableSocket} is writable. A socket is writable if data can be {@linkplain #write(byte[]) written} without requiring this data to
	 * be buffered locally, potentially causing excessive memory consumption.
	 * 
	 * @return {@code true} if this {@code WritableSocket} is writable
	 * @see #isConnected()
	 */
	public boolean isWritable();

	/**
	 * Returns a readable string identifier of the remote peer.
	 * 
	 * @return A readable remote string identifier
	 */
	public String getRemoteName();

	/**
	 * Closes this {@link WritableSocket}.
	 * 
	 * @throws IOException If an IO error occurs
	 */
	@Override
	public void close() throws IOException;
}
