/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

import java.util.Objects;

import org.omegazero.http.util.HTTPResponder;

/**
 * Represents a HTTP request, a specific type of {@link HTTPMessage}.
 * 
 * @since 1.2.1
 */
public class HTTPRequest extends HTTPMessage {

	private static final long serialVersionUID = 1L;


	protected String method;
	protected String scheme;
	protected String authority;
	protected String path;

	protected transient HTTPResponder httpResponder;

	/**
	 * Creates a new {@link HTTPRequest}.
	 * <p>
	 * Note that no deep copy of the <b>headers</b> will be created, meaning changes made to the given {@link HTTPHeaderContainer} will reflect on this {@link HTTPRequest}.
	 * 
	 * @param method The request method
	 * @param scheme The request URL scheme (e.g. "http")
	 * @param authority The request URL authority or, if not provided, the value of the "Host" header (e.g. "example.com"). May be <code>null</code> if neither is provided
	 * @param path The request URL path component
	 * @param version The HTTP version
	 * @param headers The HTTP headers, or <code>null</code> to create an empty set of headers
	 */
	public HTTPRequest(String method, String scheme, String authority, String path, String version, HTTPHeaderContainer headers) {
		super(version, headers);
		this.method = Objects.requireNonNull(method);
		this.scheme = Objects.requireNonNull(scheme);
		this.authority = authority;
		this.path = Objects.requireNonNull(path);
	}

	/**
	 * Copies the given {@link HTTPRequest}.
	 * 
	 * @param request The {@code HTTPRequest} to copy from
	 * @see HTTPMessage#HTTPMessage(HTTPMessage)
	 */
	public HTTPRequest(HTTPRequest request) {
		super(request);
		this.method = request.method;
		this.scheme = request.scheme;
		this.authority = request.authority;
		this.path = request.path;
		this.httpResponder = request.httpResponder;
	}


	/**
	 * Generates the request URI of this {@link HTTPRequest}. This always contains the URL scheme and authority component, and the path component if the path is not equal to
	 * "{@code *}".
	 * 
	 * @return The request URI of this {@code HTTPRequest}
	 */
	public String requestURI() {
		return this.scheme + "://" + this.authority + ("*".equals(this.path) ? "" : this.path);
	}

	/**
	 * Generates a HTTP/1-style request line of the form <i>[{@linkplain #getMethod() method}] [{@linkplain #requestURI() requestURI}] HTTP/[version number]</i>.
	 * 
	 * @return A request line
	 */
	public String requestLine() {
		return this.method + " " + this.requestURI() + " " + this.httpVersion;
	}


	/**
	 * Returns the HTTP request method of this {@link HTTPRequest}.
	 * 
	 * @return The HTTP request method
	 * @see #setMethod(String)
	 */
	public String getMethod() {
		return this.method;
	}

	/**
	 * Returns the HTTP request scheme (URI component) of this {@link HTTPRequest}.
	 * 
	 * @return The HTTP request scheme
	 * @see #setScheme(String)
	 */
	public String getScheme() {
		return this.scheme;
	}

	/**
	 * Returns the HTTP request authority (URI component) of this {@link HTTPRequest}, or <code>null</code> if this {@link HTTPMessage} does not contain an authority
	 * component.
	 * 
	 * @return The HTTP request authority
	 * @see #setAuthority(String)
	 */
	public String getAuthority() {
		return this.authority;
	}

	/**
	 * Returns the HTTP request path (URI component) of this {@link HTTPRequest}.
	 * 
	 * @return The HTTP request path
	 * @see #setPath(String)
	 */
	public String getPath() {
		return this.path;
	}

	/**
	 * Sets the HTTP request method of this {@link HTTPRequest}.
	 * 
	 * @param method The new HTTP request method
	 * @throws IllegalStateException If this {@code HTTPMessage} is {@linkplain #lock() locked}
	 * @see #getMethod()
	 */
	public void setMethod(String method) {
		this.checkLocked();
		this.method = method;
	}

	/**
	 * Sets the HTTP scheme (URI component) of this {@link HTTPRequest}.
	 * 
	 * @param scheme The new HTTP request scheme
	 * @throws IllegalStateException If this {@code HTTPMessage} is {@linkplain #lock() locked}
	 * @see #getScheme()
	 */
	public void setScheme(String scheme) {
		this.checkLocked();
		this.scheme = scheme;
	}

	/**
	 * Sets the HTTP authority (URI component) of this {@link HTTPRequest}.
	 * 
	 * @param authority The new HTTP request authority
	 * @throws IllegalStateException If this {@code HTTPMessage} is {@linkplain #lock() locked}
	 * @see #getAuthority()
	 */
	public void setAuthority(String authority) {
		this.checkLocked();
		this.authority = authority;
	}

	/**
	 * Sets the HTTP path (URI component) of this {@link HTTPRequest}.
	 * 
	 * @param path The new HTTP request path
	 * @throws IllegalStateException If this {@code HTTPMessage} is {@linkplain #lock() locked}
	 * @see #getPath()
	 */
	public void setPath(String path) {
		this.checkLocked();
		this.path = path;
	}


	/**
	 * Returns the {@link HTTPResponder} set using {@link #setHttpResponder(HTTPResponder)} used in {@link #respond(HTTPResponseData)} and similar methods.
	 * 
	 * @return The {@link HTTPResponder}
	 */
	public HTTPResponder getHttpResponder() {
		return this.httpResponder;
	}

	/**
	 * Sets a {@link HTTPResponder} used in {@link #respond(HTTPResponseData)} and similar methods.
	 * 
	 * @param httpResponder The {@link HTTPResponder}
	 * @throws IllegalStateException If this {@code HTTPMessage} is {@linkplain #lock() locked}
	 */
	public void setHttpResponder(HTTPResponder httpResponder) {
		this.checkLocked();
		this.httpResponder = httpResponder;
	}


	/**
	 * Returns <code>true</code> if this {@link HTTPRequest} has a {@linkplain #getOther() response}.
	 * 
	 * @return <code>true</code> if this {@code HTTPRequest} has a response
	 */
	public boolean hasResponse() {
		return super.other != null;
	}

	@Override
	public HTTPResponse getOther() {
		return (HTTPResponse) super.getOther();
	}


	/**
	 * Responds to this {@code HTTPRequest} using an {@link HTTPResponder} set for this object.
	 * 
	 * @param response The response
	 * @throws IllegalStateException If no {@code HTTPResponder} is set
	 * @see #setHttpResponder(HTTPResponder)
	 * @see HTTPResponder#respond(HTTPRequest, HTTPResponseData)
	 */
	public void respond(HTTPResponseData response) {
		if(this.httpResponder == null)
			throw new IllegalStateException("No HTTPResponder is set");
		this.httpResponder.respond(this, response);
	}

	/**
	 * Responds to this {@code HTTPRequest} using an {@link HTTPResponder} set for this object.
	 * 
	 * @param status The status code of the response
	 * @param data The data to send in the response
	 * @param headers Headers to send in the response
	 * @throws IllegalStateException If no {@code HTTPResponder} is set
	 * @see #setHttpResponder(HTTPResponder)
	 * @see HTTPResponder#respond(HTTPRequest, int, byte[], String...)
	 */
	public void respond(int status, byte[] data, String... headers) {
		if(this.httpResponder == null)
			throw new IllegalStateException("No HTTPResponder is set");
		this.httpResponder.respond(this, status, data, headers);
	}
}
