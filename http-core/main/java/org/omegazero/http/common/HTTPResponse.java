/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

/**
 * Represents a HTTP response, a specific type of {@link HTTPMessage}.
 * 
 * @since 1.2.1
 */
public class HTTPResponse extends HTTPMessage {

	private static final long serialVersionUID = 1L;


	/**
	 * The HTTP response status.
	 */
	protected int status;

	/**
	 * Creates a new {@link HTTPResponse}.
	 * <p>
	 * Note that no deep copy of the <b>headers</b> will be created, meaning changes made to the given {@link HTTPHeaderContainer} will reflect on this {@link HTTPResponse}.
	 * 
	 * @param status The HTTP response status code
	 * @param version The HTTP version
	 * @param headers The HTTP headers, or <code>null</code> to create an empty set of headers
	 */
	public HTTPResponse(int status, String version, HTTPHeaderContainer headers) {
		super(version, headers);
		if(status <= 0)
			throw new IllegalArgumentException("Invalid status code: " + status);
		this.status = status;
	}

	/**
	 * Copies the given {@link HTTPResponse}.
	 * 
	 * @param response The {@code HTTPResponse} to copy from
	 * @see HTTPMessage#HTTPMessage(HTTPMessage)
	 */
	public HTTPResponse(HTTPResponse response) {
		super(response);
		this.status = response.status;
	}


	/**
	 * Generates a HTTP/1-style response line of the form <i>HTTP/[version] [{@linkplain #getStatus() status}]</i>.
	 * 
	 * @return A response line
	 */
	public String responseLine() {
		return this.httpVersion + " " + this.status;
	}


	/**
	 * Returns the HTTP response status code of this {@link HTTPResponse}.
	 * 
	 * @return The HTTP response status code
	 * @see #setStatus(int)
	 */
	public int getStatus() {
		return this.status;
	}

	/**
	 * Sets the HTTP response status code of this {@link HTTPResponse}.
	 * 
	 * @param status The new HTTP response status code
	 * @throws IllegalStateException If this {@code HTTPMessage} is {@linkplain #lock() locked}
	 * @see #getStatus()
	 */
	public void setStatus(int status) {
		this.checkLocked();
		this.status = status;
	}


	/**
	 * Determines whether this {@link HTTPResponse} is an intermediate message, meaning it is followed up by another {@code HTTPResponse} to complete the HTTP exchange or it
	 * has some other special meaning.
	 * <p>
	 * This applies to responses with 1xx response codes.
	 * 
	 * @return <code>true</code> if this {@code HTTPResponse} is an intermediate message
	 */
	public boolean isIntermediateMessage() {
		return this.status >= 100 && this.status <= 199;
	}

	/**
	 * Determines whether this {@link HTTPResponse} has a response body if initiated by the given <b>request</b>. This is {@code true} if this response's
	 * {@linkplain #getStatus() status} has a response body, and the request that initiated this response did not use the <i>HEAD</i> method, or the <i>CONNECT</i> method with
	 * a 2xx response status.
	 * <p>
	 * A call to this method is equivalent to a call to
	 * 
	 * <pre>
	 * <code>
	 * response.{@link #hasResponseBody(HTTPRequest) hasResponseBody}(response.{@link #getOther() getOther}());
	 * </code>
	 * </pre>
	 * 
	 * @return <code>true</code> if this {@code HTTPResponse} should contain a response body
	 * @see #hasResponseBody(HTTPRequest)
	 * @see #hasResponseBody(int)
	 */
	public boolean hasResponseBody() {
		return this.hasResponseBody(this.getOther());
	}

	/**
	 * Determines whether this {@link HTTPResponse} has a response body if initiated by the given <b>request</b>. This is {@code true} if this response's
	 * {@linkplain #getStatus() status} has a response body, and the given <b>request</b> did not use the <i>HEAD</i> method, or the <i>CONNECT</i> method with a 2xx response
	 * status.
	 * 
	 * @param request The request to check with. May be <code>null</code>, in which case only the response status is checked
	 * @return <code>true</code> if this {@code HTTPResponse} should contain a response body if initiated by the given <b>request</b>
	 * @see #hasResponseBody()
	 * @see #hasResponseBody(int)
	 */
	public boolean hasResponseBody(HTTPRequest request) {
		// rfc 7230 section 3.3.3
		if(request != null){
			if(request.getMethod().equals("HEAD"))
				return false;
			if(request.getMethod().equals("CONNECT") && this.status >= 200 && this.status <= 299)
				return false;
		}
		return hasResponseBody(this.status);
	}


	@Override
	public HTTPRequest getOther() {
		return (HTTPRequest) super.getOther();
	}


	/**
	 * Determines whether responses with the given <b>status</b> code should have a response body. Responses with statuses 1xx, 204, and 304 do not have a response body.
	 * 
	 * @param status A HTTP status code
	 * @return <code>true</code> if a response with the given status code should have a response body
	 * @see #hasResponseBody()
	 */
	public static boolean hasResponseBody(int status) {
		return !((status >= 100 && status <= 199) || status == 204 || status == 304);
	}
}
