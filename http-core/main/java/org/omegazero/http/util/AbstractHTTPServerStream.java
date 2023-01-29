/*
 * Copyright (C) 2023 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import java.util.Objects;
import java.util.function.Consumer;

import org.omegazero.http.common.HTTPMessageTrailers;
import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.common.HTTPRequestData;
import org.omegazero.http.common.HTTPResponseData;

/**
 * A {@code HTTPServerStream} with several basic methods implemented.
 *
 * @since 1.4.1
 */
public abstract class AbstractHTTPServerStream extends AbstractHTTPMessageStream implements HTTPServerStream {

	protected final HTTPServer server;

	protected Consumer<HTTPRequestData> onRequestData;
	protected Consumer<HTTPMessageTrailers> onRequestEnded;

	/**
	 * Creates a new {@code AbstractHTTPServerStream}.
	 *
	 * @param request The request
	 * @param server The server
	 */
	public AbstractHTTPServerStream(HTTPRequest request, HTTPServer server){
		super(request);
		this.server = Objects.requireNonNull(server);
	}


	/**
	 * Calls the {@code onRequestData} callback.
	 *
	 * @param data The object to pass to the callback
	 */
	public void callOnRequestData(HTTPRequestData data){
		if(this.onRequestData != null)
			this.onRequestData.accept(data);
	}

	/**
	 * Calls the {@code onRequestEnded} callback.
	 *
	 * @param trailers Optional trailers to pass to the callback
	 */
	public void callOnRequestEnded(HTTPMessageTrailers trailers){
		if(this.onRequestEnded != null)
			this.onRequestEnded.accept(trailers);
	}


	@Override
	public HTTPServer getServer(){
		return this.server;
	}

	@Override
	public void onRequestData(Consumer<HTTPRequestData> onRequestData){
		this.onRequestData = onRequestData;
	}

	@Override
	public void onRequestEnded(Consumer<HTTPMessageTrailers> onRequestEnded){
		this.onRequestEnded = onRequestEnded;
	}

	/**
	 * {@inheritDoc}
	 *
	 * @implNote Calls {@link HTTPServer#respond(HTTPRequest, HTTPResponseData)}
	 */
	@Override
	public void respond(HTTPResponseData response){
		this.server.respond(this.request, response);
	}

	/**
	 * {@inheritDoc}
	 *
	 * @implNote Calls {@link HTTPServer#respond(HTTPRequest, int, byte[], String...)}
	 */
	@Override
	public void respond(int status, byte[] data, String... headers){
		this.server.respond(this.request, status, data, headers);
	}
}
