/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h1;

import org.omegazero.common.util.ArrayUtil;
import org.omegazero.http.common.HTTPHeaderContainer;
import org.omegazero.http.common.HTTPMessage;
import org.omegazero.http.common.InvalidHTTPMessageException;
import org.omegazero.http.util.HTTPValidator;

/**
 * This class is used for receiving and parsing HTTP headers.
 * <p>
 * A usual usage example is as follows:
 * <ol>
 * <li>An application receives HTTP header data (either for a request or response, depending on the type of this {@code HTTP1MessageReceiver})</li>
 * <li>The application passes this data as (possibly multiple) byte arrays to {@link #receive(byte[], int)}</li>
 * <li>Upon receiving a non-negative integer from {@link #receive(byte[], int)}, indicating a full HTTP header block was received, the application retrieves the parsed message
 * using {@link #get()} (or possible variants of it in subclasses)</li>
 * <li>The application calls {@link #reset()} to prepare for the receipt of the next HTTP message (if applicable), and processes the current HTTP message and body</li>
 * </ol>
 * 
 * @since 1.2.1
 */
public abstract class HTTP1MessageReceiver {

	private static final String[] HTTP_VERSIONS = { "HTTP/1.0", "HTTP/1.1" };

	/**
	 * The default value for {@code maxHeaderSize}, if not passed in the {@linkplain #HTTP1MessageReceiver(int) constructor}.
	 */
	public static final int DEFAULT_MAX_HEADER_SIZE = 8192;


	private final int maxHeaderSize;

	private byte[] headerBuffer;
	private int headerBufferIndex;
	private int headerSize = 0;
	private int lVersion = -1;
	protected HTTPHeaderContainer lHeaders;

	/**
	 * Creates a new {@link HTTP1MessageReceiver} with the {@linkplain HTTP1MessageReceiver#DEFAULT_MAX_HEADER_SIZE default maximum message size}.
	 */
	public HTTP1MessageReceiver() {
		this(DEFAULT_MAX_HEADER_SIZE);
	}

	/**
	 * Creates a new {@link HTTP1MessageReceiver}.
	 * 
	 * @param maxHeaderSize The maximum size in bytes of a single HTTP message. See {@link #receive(byte[], int)}
	 */
	public HTTP1MessageReceiver(int maxHeaderSize) {
		this.maxHeaderSize = maxHeaderSize;
	}


	/**
	 * Called when a HTTP/1 start line is received.
	 * 
	 * @param startLine The start line components
	 * @throws InvalidHTTPMessageException If the start line is malformed
	 */
	protected abstract void receiveStartLine(String[] startLine) throws InvalidHTTPMessageException;

	/**
	 * Called when a HTTP header is received.
	 * 
	 * @param key The header name
	 * @param value The header value
	 * @throws InvalidHTTPMessageException If the header has an invalid value or otherwise invalid
	 */
	protected void receiveHeader(String key, String value) throws InvalidHTTPMessageException {
		this.lHeaders.addHeader(key, value);
	}

	/**
	 * Called when the version string in the start line is received.
	 * 
	 * @param versionStr The version string
	 * @throws InvalidHTTPMessageException If the version string is invalid
	 */
	protected final void receiveVersion(String versionStr) throws InvalidHTTPMessageException {
		if(versionStr.equals(HTTP_VERSIONS[0]))
			this.lVersion = 0;
		else if(versionStr.equals(HTTP_VERSIONS[1]))
			this.lVersion = 1;
		else
			throw new InvalidHTTPMessageException("Invalid version string");
	}

	/**
	 * Returns the string representation of the {@linkplain #receiveVersion(String) received} version.
	 * 
	 * @return The version string
	 * @throws IllegalStateException If no version was received
	 */
	protected final String getVersion() {
		if(this.lVersion < 0)
			throw new IllegalStateException("No version available");
		return HTTP_VERSIONS[this.lVersion];
	}


	/**
	 * Returns the {@link HTTPMessage} after successfully receiving it ({@link #receive(byte[], int)} returns a non-negative integer).
	 * <p>
	 * A call to this method should be followed by a call to {@link #reset()} to allow the next HTTP message to be received.
	 * <p>
	 * It is unspecified whether multiple calls to this method return the same {@code HTTPMessage} instance.
	 * 
	 * @return The {@link HTTPMessage}
	 * @throws IllegalStateException If no {@code HTTPMessage} was received yet
	 * @implSpec {@link #msgInit(HTTPMessage)} must be called on a new {@code HTTPMessage} before returning it
	 */
	public abstract HTTPMessage get();


	/**
	 * Parses the given <b>data</b> containing a full or partial HTTP header block.
	 * <p>
	 * If a full and valid HTTP header was received, this method returns a non-negative integer and the received {@link HTTPMessage} may be retrieved using {@link #get()}. The
	 * returned integer indicates the index at which the HTTP message body starts in the given array, which may be equal to the length of the array (if the given array
	 * contains no body data).
	 * <p>
	 * If the given data is invalid or the total amount of data received exceeds the maximum configured in the constructor, an {@link InvalidHTTPMessageException} is thrown.
	 * 
	 * @param data The data
	 * @param offset The index to start reading at
	 * @return The index at which the body data starts, if a full HTTP header block was received, or {@code -1} if no full message was received yet
	 * @throws InvalidHTTPMessageException If the given <b>data</b> could not be identified as valid HTTP data, or the total amount of data received exceeds the limit. In this
	 * case, this method should no longer be called until {@link #reset()} is called
	 */
	public int receive(byte[] data, int offset) throws InvalidHTTPMessageException {
		int index = offset;
		while(index < data.length){
			int end = ArrayUtil.indexOf(data, HTTP1Util.EOL, index);
			if(end < 0){
				if(this.headerBuffer == null)
					this.headerBuffer = new byte[maxHeaderSize];
				int remaining = data.length - index;
				if(remaining > this.headerBuffer.length - this.headerBufferIndex)
					throw new InvalidHTTPMessageException("HTTP message is too large (buf start)");
				System.arraycopy(data, index, this.headerBuffer, this.headerBufferIndex, remaining);
				this.headerBufferIndex += remaining;
				break;
			}
			byte[] p_data;
			int p_index, p_end;
			if(this.headerBufferIndex > 0){
				assert index == offset;
				int remaining = end - index;
				if(remaining > this.headerBuffer.length - this.headerBufferIndex)
					throw new InvalidHTTPMessageException("HTTP message is too large (buf end)");
				System.arraycopy(data, index, this.headerBuffer, this.headerBufferIndex, remaining);
				p_data = this.headerBuffer;
				p_index = 0;
				p_end = this.headerBufferIndex + remaining;
				this.headerBufferIndex = 0;
			}else{
				p_data = data;
				p_index = index;
				p_end = end;
			}
			int p_endL = p_end + HTTP1Util.EOL.length;
			this.headerSize += p_endL - p_index;
			if(this.headerSize > this.maxHeaderSize)
				throw new InvalidHTTPMessageException("HTTP message is too large");
			if(!this.isStartLineReceived()){
				assert index == offset : "Unexpected state: start line not received but index = " + index + ", offset = " + offset;
				int len = p_end - p_index;
				if(!HTTPValidator.bytesInRange(p_data, p_index, len, 32, 126))
					throw new InvalidHTTPMessageException("Invalid characters in start line");
				this.receiveStartLine(new String(p_data, p_index, len).split(" "));
			}else if(p_end == p_index){
				return p_endL;
			}else{
				if(this.lHeaders == null)
					this.lHeaders = new HTTPHeaderContainer();
				int len = p_end - p_index;
				int sep = ArrayUtil.indexOf(p_data, (byte) ':', p_index, len);
				if(sep < 0)
					throw new InvalidHTTPMessageException("Invalid header line");
				if(!HTTPValidator.bytesInRange(p_data, p_index, len, 32, 126))
					throw new InvalidHTTPMessageException("Invalid characters in header line");
				this.receiveHeader(new String(p_data, p_index, sep - p_index).toLowerCase(), new String(p_data, sep + 1, p_end - (sep + 1)).trim());
			}
			index = end + HTTP1Util.EOL.length;
		}
		return -1;
	}

	/**
	 * Resets this {@link HTTP1MessageReceiver} to allow receipt of additional {@link HTTPMessage}s.
	 */
	public void reset() {
		this.headerSize = 0;
		this.lVersion = -1;
		this.lHeaders = null;
	}


	/**
	 * Returns the total amount of bytes received through {@link #receive(byte[], int)}.
	 * 
	 * @return The number of bytes
	 */
	public int getHeaderSize() {
		return this.headerSize;
	}

	/**
	 * Returns <code>true</code> if a valid HTTP start line was received in a call to {@link #receive(byte[], int)} and is available.
	 * 
	 * @return <code>true</code> if a HTTP start line was received
	 */
	public boolean isStartLineReceived() {
		return this.lVersion >= 0;
	}


	protected static <T extends HTTPMessage> T msgInit(T msg) {
		msg.setChunkedTransfer("chunked".equals(msg.getHeader("transfer-encoding")));
		return msg;
	}
}
