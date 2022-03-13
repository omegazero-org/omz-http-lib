/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h1;

import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.common.InvalidHTTPMessageException;
import org.omegazero.http.gen.HTTPRequestSupplier;
import org.omegazero.http.util.HTTPValidator;

/**
 * A special {@link HTTP1MessageReceiver} used for receiving {@link HTTPRequest}s.
 * 
 * @since 1.2.1
 */
public class HTTP1RequestReceiver extends HTTP1MessageReceiver {


	private final String scheme;

	private String lRequestMethod;
	private String lRequestHost;
	private String lRequestPath;

	/**
	 * Creates a new {@link HTTP1RequestReceiver} with the {@linkplain HTTP1MessageReceiver#DEFAULT_MAX_HEADER_SIZE default maximum message size}.
	 * 
	 * @param secure Whether the data is being received over a secure channel. This determines whether the scheme attribute of requests will be set to <i>http</i> or
	 * <i>https</i>
	 */
	public HTTP1RequestReceiver(boolean secure) {
		this(DEFAULT_MAX_HEADER_SIZE, secure);
	}

	/**
	 * Creates a new {@link HTTP1RequestReceiver}.
	 * 
	 * @param maxHeaderSize The maximum size in bytes of a single HTTP message
	 * @param secure Whether the data is being received over a secure channel. This determines whether the scheme attribute of requests will be set to <i>http</i> or
	 * <i>https</i>
	 */
	public HTTP1RequestReceiver(int maxHeaderSize, boolean secure) {
		super(maxHeaderSize);
		this.scheme = secure ? "https" : "http";
	}


	@Override
	protected void receiveStartLine(String[] startLine) throws InvalidHTTPMessageException {
		if(!(startLine.length == 3 && HTTPValidator.validMethod(startLine[0])))
			throw new InvalidHTTPMessageException("Invalid request start line");

		super.receiveVersion(startLine[2]);

		String requestURI = startLine[1];
		String host = null;
		if(requestURI.charAt(0) != '/' && !requestURI.equals("*")){
			// assuming absolute URI with net_path and abs_path
			int authStart = requestURI.indexOf("://");
			if(authStart < 0)
				throw new InvalidHTTPMessageException("Invalid request URI");
			authStart += 3;
			int pathStart = requestURI.indexOf('/', authStart);
			if(pathStart < 0)
				throw new InvalidHTTPMessageException("Invalid request URI");
			host = requestURI.substring(authStart, pathStart);
			requestURI = requestURI.substring(pathStart);
		}
		if(!HTTPValidator.validPath(requestURI))
			throw new InvalidHTTPMessageException("Invalid request path");

		this.lRequestMethod = startLine[0];
		this.lRequestHost = host;
		this.lRequestPath = requestURI;
	}

	@Override
	protected void receiveHeader(String key, String value) throws InvalidHTTPMessageException {
		if(key.equals("host")){
			if(this.lRequestHost == null)
				this.lRequestHost = value;
		}else
			super.receiveHeader(key, value);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see #get(HTTPRequestSupplier)
	 */
	@Override
	public HTTPRequest get() {
		return this.get(HTTPRequestSupplier.DEFAULT);
	}

	/**
	 * Returns the {@link HTTPRequest} after successfully receiving it. See {@link #get()}.
	 * 
	 * @param <T> The specific {@code HTTPRequest} type this method creates
	 * @param supplier A {@link HTTPRequestSupplier} for creating the {@link HTTPRequest}
	 * @return The {@link HTTPRequest}
	 * @throws IllegalStateException If no {@code HTTPMessage} was received yet
	 */
	public <T extends HTTPRequest> T get(HTTPRequestSupplier<T> supplier) {
		if(!super.isStartLineReceived())
			throw new IllegalStateException("No valid message received");
		return msgInit(supplier.get(this.lRequestMethod, this.scheme, this.lRequestHost, this.lRequestPath, super.getVersion(), super.lHeaders));
	}

	@Override
	public void reset() {
		super.reset();
		this.lRequestMethod = null;
		this.lRequestHost = null;
		this.lRequestPath = null;
	}
}
