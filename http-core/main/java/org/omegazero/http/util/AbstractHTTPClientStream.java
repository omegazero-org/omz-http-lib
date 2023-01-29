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
import org.omegazero.http.common.HTTPResponse;
import org.omegazero.http.common.HTTPResponseData;

/**
 * A {@code HTTPClientStream} with several basic methods implemented.
 *
 * @since 1.4.1
 */
public abstract class AbstractHTTPClientStream extends AbstractHTTPMessageStream implements HTTPClientStream {

	protected final HTTPClient client;

	protected HTTPResponse response;

	protected Consumer<HTTPResponse> onResponse;
	protected Consumer<HTTPResponseData> onResponseData;
	protected Consumer<HTTPMessageTrailers> onResponseEnded;

	/**
	 * Creates a new {@code AbstractHTTPClientStream}.
	 *
	 * @param request The request
	 * @param client The client
	 */
	public AbstractHTTPClientStream(HTTPRequest request, HTTPClient client){
		super(request);
		this.client = Objects.requireNonNull(client);
	}


	/**
	 * Calls the {@code onResponse} callback and stores the response, if {@link HTTPResponse#isIntermediateMessage()} is {@code false}.
	 *
	 * @param response The response
	 */
	public void responseReceived(HTTPResponse response){
		if(!response.isIntermediateMessage())
			this.response = response;
		if(this.onResponse != null)
			this.onResponse.accept(response);
	}

	/**
	 * Calls the {@code onResponseData} callback.
	 *
	 * @param data The object to pass to the callback
	 */
	public void callOnResponseData(HTTPResponseData data){
		if(this.onResponseData != null)
			this.onResponseData.accept(data);
	}

	/**
	 * Calls the {@code onResponseEnded} callback and sets {@link #closed} to {@code true}.
	 *
	 * @param trailers Optional trailers to pass to the callback
	 */
	public void callOnResponseEnded(HTTPMessageTrailers trailers){
		if(this.onResponseEnded != null)
			this.onResponseEnded.accept(trailers);
		super.closed = true;
	}


	@Override
	public HTTPClient getClient(){
		return this.client;
	}

	@Override
	public HTTPResponse getResponse(){
		return this.response;
	}

	@Override
	public void onResponse(Consumer<HTTPResponse> onResponse){
		this.onResponse = onResponse;
	}

	@Override
	public void onResponseData(Consumer<HTTPResponseData> onResponseData){
		this.onResponseData = onResponseData;
	}

	@Override
	public void onResponseEnded(Consumer<HTTPMessageTrailers> onResponseEnded){
		this.onResponseEnded = onResponseEnded;
	}
}
