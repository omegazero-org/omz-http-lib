/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.netutil;

import org.omegazero.http.util.WritableSocket;
import org.omegazero.net.socket.SocketConnection;

/**
 * A {@link WritableSocket} that wraps a {@link SocketConnection}.
 */
public class SocketConnectionWritable implements WritableSocket {


	private final SocketConnection connection;

	/**
	 * Creates a new {@link SocketConnectionWritable}.
	 * 
	 * @param connection The connection to write to
	 */
	public SocketConnectionWritable(SocketConnection connection) {
		this.connection = connection;
	}


	@Override
	public void write(byte[] data, int offset, int length) {
		this.connection.writeQueue(data, offset, length);
	}

	@Override
	public void flush() {
		this.connection.flush();
	}

	@Override
	public boolean isConnected() {
		return this.connection.isConnected();
	}

	@Override
	public boolean isWritable() {
		return this.connection.isWritable();
	}

	@Override
	public String getRemoteName() {
		return this.connection.getRemoteAddress().toString();
	}

	@Override
	public void close() {
		this.connection.destroy();
	}


	/**
	 * Returns the {@link SocketConnection} passed in the constructor.
	 * 
	 * @return The {@code SocketConnection}
	 */
	public SocketConnection getConnection() {
		return this.connection;
	}
}
