/*
 * Copyright (C) 2023 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

import java.util.Objects;
import java.util.function.Consumer;

import org.omegazero.http.common.HTTPRequest;

/**
 * A {@code HTTPMessageStream} with several basic methods implemented.
 *
 * @since 1.4.1
 */
public abstract class AbstractHTTPMessageStream implements HTTPMessageStream {

	protected final HTTPRequest request;

	protected boolean closed = false;

	protected Runnable onWritable;
	protected Consumer<Exception> onError;

	/**
	 * Creates a new {@code AbstractHTTPMessageStream}.
	 *
	 * @param request The request
	 */
	public AbstractHTTPMessageStream(HTTPRequest request){
		this.request = Objects.requireNonNull(request);
	}


	/**
	 * Calls the {@code onWritable} callback.
	 */
	public void callOnWritable(){
		if(this.onWritable != null)
			this.onWritable.run();
	}

	/**
	 * Calls the {@code onError} callback.
	 *
	 * @param err The object to pass to the callback
	 */
	public void callOnError(Exception err){
		if(this.onError != null)
			this.onError.accept(err);
	}


	@Override
	public HTTPRequest getRequest(){
		return this.request;
	}

	@Override
	public void onWritable(Runnable onWritable){
		this.onWritable = onWritable;
	}

	@Override
	public void onError(Consumer<Exception> onError){
		this.onError = onError;
	}

	@Override
	public boolean isClosed(){
		return this.closed;
	}
}
