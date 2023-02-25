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
import org.omegazero.http.common.MessageStreamClosedException;

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
	 * Called when a callback handler throws an exception. Calls the {@code onError} callback and closes this stream with reason <i>INTERNAL_ERROR</i>.
	 *
	 * @param e The exception thrown by the callback handler
	 */
	protected void internalError(Exception e){
		this.callOnError(e);
		this.close(MessageStreamClosedException.CloseReason.INTERNAL_ERROR);
	}

	/**
	 * Calls the {@code onWritable} callback.
	 */
	public void callOnWritable(){
		try{
			if(this.onWritable != null)
				this.onWritable.run();
		}catch(Exception e){
			this.internalError(e);
		}
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
