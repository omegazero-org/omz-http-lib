/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

import java.util.HashMap;

/**
 * Represents a generic HTTP request or response message, agnostic of the HTTP version used.
 * 
 * @since 1.2.1
 */
public abstract class HTTPMessage extends HTTPHeaderContainer {

	private static final long serialVersionUID = 2L;


	protected final long createdTime = System.currentTimeMillis();


	/**
	 * The HTTP version string.
	 */
	protected String httpVersion;
	/**
	 * Whether the data for this {@code HTTPMessage} is transferred in chunks instead of a blob with a predetermined size. See {@link #isChunkedTransfer()}.
	 */
	protected boolean chunkedTransfer;

	/**
	 * See {@link #getOther()}.
	 */
	protected transient HTTPMessage other;

	/**
	 * See {@link #isLocked()}.
	 */
	protected transient boolean locked = false;

	/**
	 * Creates a new <code>HTTPMessage</code> with the given <b>headers</b>.
	 * <p>
	 * Note that no deep copy of the <b>headers</b> will be created, meaning changes made to the given {@link HTTPHeaderContainer} will reflect on this {@link HTTPMessage}.
	 * 
	 * @param httpVersion The HTTP version string sent in this message
	 * @param headers The HTTP headers, or <code>null</code> to create an empty set of headers
	 */
	protected HTTPMessage(String httpVersion, HTTPHeaderContainer headers) {
		super(headers != null ? headers.headerFields : null);
		this.httpVersion = httpVersion;
	}

	/**
	 * Copies the given {@link HTTPMessage}.
	 * <p>
	 * The following properties are not copied:
	 * <ul>
	 * <li>The {@linkplain #getCreatedTime() creation time}</li>
	 * <li>The {@code HTTPMessage} returned by {@link #getOther()}</li>
	 * <li>The {@linkplain #isLocked() locked state}</li>
	 * </ul>
	 * 
	 * @param msg The {@code HTTPMessage} to copy from
	 * @see HTTPHeaderContainer#HTTPHeaderContainer(HTTPHeaderContainer)
	 */
	public HTTPMessage(HTTPMessage msg) {
		super(msg);
		this.httpVersion = msg.httpVersion;
		this.chunkedTransfer = msg.chunkedTransfer;
		if(msg.attachments != null)
			this.attachments = new HashMap<>(msg.attachments);
	}


	/**
	 * Returns the time this {@link HTTPMessage} object was created, as returned by {@link System#currentTimeMillis()}.
	 * 
	 * @return The creation time of this object in milliseconds
	 */
	public long getCreatedTime() {
		return this.createdTime;
	}


	/**
	 * Returns the HTTP version declared in this {@link HTTPMessage}.
	 * 
	 * @return The HTTP version string
	 * @see #setHttpVersion(String)
	 */
	public String getHttpVersion() {
		return this.httpVersion;
	}

	/**
	 * Sets the HTTP version string of this {@link HTTPMessage}.
	 * 
	 * @param httpVersion The new HTTP version string
	 * @throws IllegalStateException If this {@code HTTPMessage} is {@linkplain #lock() locked}
	 * @see #getHttpVersion()
	 */
	public void setHttpVersion(String httpVersion) {
		this.checkLocked();
		this.httpVersion = httpVersion;
	}

	/**
	 * Returns whether the message body is chunked, as set by {@link #setChunkedTransfer(boolean)} or by the application that created this <code>HTTPMessage</code> object.
	 * <p>
	 * HTTP requests or responses that contain a body may declare their full body size in the <i>Content-Length</i> HTTP header. If the header exists and is valid, the
	 * transfer is considered not chunked, otherwise, body data is transferred in chunks and it may be any size in total.
	 * <p>
	 * If this is <code>false</code>, the length of data set using {@link HTTPMessageData#setData(byte[])} must be the same as the original length.
	 * 
	 * @return <code>true</code> if the message body is chunked
	 */
	public boolean isChunkedTransfer() {
		return this.chunkedTransfer;
	}

	/**
	 * Sets whether the body of this message should be {@linkplain #isChunkedTransfer() chunked}.
	 * 
	 * @param chunkedTransfer Whether the message body should be chunked
	 */
	public void setChunkedTransfer(boolean chunkedTransfer) {
		this.checkLocked();
		this.chunkedTransfer = chunkedTransfer;
	}


	/**
	 * Returns the other {@link HTTPMessage} of the HTTP message exchange this {@code HTTPMessage} is part of. This may be {@code null}.
	 * <p>
	 * For example, for {@link HTTPRequest}s, this would be the {@linkplain HTTPResponse response} of the request, and vice versa.
	 * 
	 * @return The other {@code HTTPMessage} involved in the HTTP message exchange
	 */
	public HTTPMessage getOther() {
		return this.other;
	}

	/**
	 * Sets the {@link HTTPMessage} returned by {@link #getOther()}.
	 * 
	 * @param other The other {@code HTTPMessage}
	 */
	public void setOther(HTTPMessage other) {
		this.checkLocked();
		this.other = other;
	}


	/**
	 * Returns <code>true</code> if this {@link HTTPMessage} is {@linkplain #lock() locked}.
	 * 
	 * @return <code>true</code> if this {@code HTTPMessage} is locked
	 * @see #lock()
	 */
	public boolean isLocked() {
		return this.locked;
	}

	/**
	 * Locks this {@link HTTPMessage}, which will cause any method that changes the state of this object (for example, setters) to throw an <code>IllegalStateException</code>.
	 * <p>
	 * Multiple calls to this method have no additional effect.
	 * 
	 * @see #isLocked()
	 */
	public void lock() {
		this.locked = true;
	}

	@Override
	protected void checkLocked() {
		if(this.locked)
			throw new IllegalStateException("HTTPMessage object is locked may no longer be modified");
	}
}
