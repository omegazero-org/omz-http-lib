/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

/**
 * An object of this class contains a single {@link HTTPMessage} and its body data (or a part of it).
 * 
 * @since 1.2.1
 */
public class HTTPMessageData implements java.io.Serializable {

	private static final long serialVersionUID = 1L;


	protected final HTTPMessage httpMessage;
	protected final boolean lastPacket;

	protected byte[] data;

	/**
	 * Creates a new {@link HTTPMessageData} instance.
	 * 
	 * @param httpMessage The HTTP message
	 * @param data The full or partial body of the <b>httpMessage</b>
	 */
	public HTTPMessageData(HTTPMessage httpMessage, byte[] data) {
		this(httpMessage, false, data);
	}

	/**
	 * Creates a new {@link HTTPMessageData} instance.
	 * 
	 * @param httpMessage The HTTP message
	 * @param lastPacket Whether this {@code HTTPMessageData} represents the last body data part
	 * @param data The full or partial body of the <b>httpMessage</b>
	 */
	public HTTPMessageData(HTTPMessage httpMessage, boolean lastPacket, byte[] data) {
		this.httpMessage = httpMessage;
		this.lastPacket = lastPacket;
		this.data = data;
	}


	/**
	 * Returns the {@link HTTPMessage} stored in this {@link HTTPMessageData} object.
	 * 
	 * @return The {@link HTTPMessage}
	 */
	public HTTPMessage getHttpMessage() {
		return this.httpMessage;
	}

	/**
	 * Returns the data stored in this {@link HTTPMessageData} object.
	 * 
	 * @return The data
	 */
	public byte[] getData() {
		return this.data;
	}

	/**
	 * Replaces the stored data with the given <b>data</b>.
	 * <p>
	 * If the {@link HTTPMessage} body of this object is not {@linkplain HTTPMessage#isChunkedTransfer() chunked}, the given byte array must have the same length as before.
	 * 
	 * @param data The new data
	 * @throws UnsupportedOperationException If the {@link HTTPMessage} body of this object is not {@linkplain HTTPMessage#isChunkedTransfer() chunked} and the given byte
	 * array does not have the same length as the original byte array
	 */
	public void setData(byte[] data) {
		if(!this.httpMessage.isChunkedTransfer() && this.data.length != data.length)
			throw new UnsupportedOperationException("HTTP message body chunk size must be the same size if transfer is not chunked");
		this.data = data;
	}

	/**
	 * Returns whether this {@link HTTPMessageData} represents the last body data part of the HTTP message.
	 * 
	 * @return <code>true</code> if this is the last body data part
	 */
	public boolean isLastPacket() {
		return this.lastPacket;
	}
}
