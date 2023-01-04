/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import java.io.Flushable;

/**
 * Provides an abstraction layer for outgoing data over a socket.
 * <p>
 * None of the methods in this interface can throw an {@code IOException}. If the underlying socket implementation encounters an IO error, it should either try to handle the error, or propagate the
 * error using a {@link java.io.UncheckedIOException}. In this case, the error will usually be propagated further to the caller during (in-)direct write calls, or cause {@link #close()} to be called
 * during internal write calls (this applies to any thrown exceptions).
 * 
 * @since 1.2.1
 */
public interface WritableSocket extends AutoCloseable, Flushable {


	/**
	 * Writes the given <b>data</b> to this {@link WritableSocket}.
	 * 
	 * @param data The data
	 * @see #write(byte[], int, int)
	 */
	public default void write(byte[] data){
		this.write(data, 0, data.length);
	}

	/**
	 * Writes the given <b>data</b> to this {@link WritableSocket}.
	 * 
	 * @param data The data
	 * @param offset The index to start writing from
	 * @param length The number of bytes to write, starting at <b>offset</b>
	 * @see #write(byte[])
	 */
	public void write(byte[] data, int offset, int length);

	/**
	 * Flushes any remaining data in this {@link WritableSocket}'s write buffer.
	 */
	@Override
	public void flush();

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
	 */
	@Override
	public void close();
}
