/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h1;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;

import org.omegazero.http.common.HTTPMessage;
import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.common.HTTPResponse;
import org.omegazero.http.util.WritableSocket;

/**
 * This class is used for transmitting HTTP headers.
 * <p>
 * {@link HTTPRequest}s and {@link HTTPResponse}s are supported by this class.
 * 
 * @since 1.2.1
 */
public class HTTP1MessageTransmitter {


	protected final WritableSocket socket;

	/**
	 * Creates a new {@link HTTP1MessageTransmitter}.
	 */
	public HTTP1MessageTransmitter() {
		this(null);
	}

	/**
	 * Creates a new {@link HTTP1MessageTransmitter}.
	 * 
	 * @param socket The socket to write encoded {@link HTTPMessage}s to. This is only required is {@link #send(HTTPMessage)} is used
	 */
	public HTTP1MessageTransmitter(WritableSocket socket) {
		this.socket = socket;
	}


	/**
	 * Encodes the given {@link HTTPMessage} using {@link #generate(HTTPMessage)} and {@linkplain WritableSocket#write(byte[]) writes} it to the {@link WritableSocket} passed
	 * in the constructor.
	 * 
	 * @param msg The {@code HTTPMessage}
	 * @see #generate(HTTPMessage)
	 * @see #HTTP1MessageTransmitter(WritableSocket)
	 */
	public void send(HTTPMessage msg){
		if(this.socket == null)
			throw new IllegalStateException("No socket configured");
		this.socket.write(this.generate(msg));
	}

	/**
	 * Encodes the given {@link HTTPMessage} into a byte array. This includes the request or response line (depending on the type of {@code HTTPMessage}), the HTTP headers and
	 * the terminating {@code CR LF}.
	 * 
	 * @param msg The {@code HTTPMessage}
	 * @return The encoded data
	 */
	public byte[] generate(HTTPMessage msg) {
		StringBuilder sb = new StringBuilder();
		sb.append(this.getStartLine(msg)).append(HTTP1Util.EOL_STR);
		this.addHeaders(msg, sb);
		for(java.util.Map.Entry<String, String> header : msg.headers()){
			appendHeader(sb, header.getKey(), header.getValue());
		}
		sb.append(HTTP1Util.EOL_STR);

		CharsetEncoder utf8Encoder = java.nio.charset.StandardCharsets.UTF_8.newEncoder();
		ByteBuffer bytes;
		try{
			bytes = utf8Encoder.encode(CharBuffer.wrap(sb));
		}catch(CharacterCodingException e){
			throw new RuntimeException(e);
		}
		if(bytes.limit() != bytes.capacity()){
			byte[] b = new byte[bytes.limit()];
			bytes.get(b);
			return b;
		}else
			return bytes.array();
	}


	/**
	 * Returns the HTTP/1 start line of the given {@link HTTPMessage}.
	 * 
	 * @param msg The {@code HTTPMessage}
	 * @return The start line
	 */
	protected String getStartLine(HTTPMessage msg) {
		if(msg instanceof HTTPRequest){
			HTTPRequest req = (HTTPRequest) msg;
			return req.getMethod() + " " + req.getPath() + " " + req.getHttpVersion();
		}else if(msg instanceof HTTPResponse){
			HTTPResponse res = (HTTPResponse) msg;
			return res.getHttpVersion() + " " + res.getStatus();
		}else
			throw new UnsupportedOperationException("Unsupported HTTPMessage type: " + msg.getClass().getName());
	}

	/**
	 * Adds any headers that are not part of the default {@link HTTPMessage} header map. For example, {@link HTTPRequest}s have an additional <i>Host</i> header with the value
	 * of {@link HTTPRequest#getAuthority()}.
	 * <p>
	 * Headers are added to the given {@code StringBuilder} using {@link #appendHeader(StringBuilder, String, String)}.
	 * 
	 * @param msg The {@code HTTPMessage}
	 * @param sb The string builder
	 */
	protected void addHeaders(HTTPMessage msg, StringBuilder sb) {
		if(msg instanceof HTTPRequest)
			appendHeader(sb, "host", ((HTTPRequest) msg).getAuthority());
	}


	/**
	 * Returns the {@link WritableSocket} passed in the constructor.
	 * 
	 * @return The {@code WritableSocket}
	 */
	public WritableSocket getSocket() {
		return this.socket;
	}


	/**
	 * Appends a HTTP/1-style header to the given {@code StringBuilder}.
	 * 
	 * @param sb The string builder
	 * @param name The header name
	 * @param value The header value
	 */
	public static void appendHeader(StringBuilder sb, String name, String value) {
		sb.append(name).append(": ").append(value).append(HTTP1Util.EOL_STR);
	}
}
