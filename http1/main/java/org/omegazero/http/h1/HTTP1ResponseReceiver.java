/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h1;

import org.omegazero.http.common.HTTPResponse;
import org.omegazero.http.common.InvalidHTTPMessageException;
import org.omegazero.http.gen.HTTPResponseSupplier;
import org.omegazero.http.util.HTTPValidator;

/**
 * A special {@link HTTP1MessageReceiver} used for receiving {@link HTTPResponse}s.
 * 
 * @since 1.2.1
 */
public class HTTP1ResponseReceiver extends HTTP1MessageReceiver {


	private int lResponseStatus;

	/**
	 * Creates a new {@link HTTP1ResponseReceiver} with the {@linkplain HTTP1MessageReceiver#DEFAULT_MAX_HEADER_SIZE default maximum message size}.
	 */
	public HTTP1ResponseReceiver() {
		super();
	}

	/**
	 * Creates a new {@link HTTP1ResponseReceiver}.
	 * 
	 * @param maxHeaderSize The maximum size in bytes of a single HTTP message
	 */
	public HTTP1ResponseReceiver(int maxHeaderSize) {
		super(maxHeaderSize);
	}


	@Override
	protected void receiveStartLine(String[] startLine) throws InvalidHTTPMessageException {
		if(!(startLine.length >= 2))
			throw new InvalidHTTPMessageException("Invalid response start line");

		super.receiveVersion(startLine[0]);

		int status = HTTPValidator.parseStatus(startLine[1]);
		if(status < 0)
			throw new InvalidHTTPMessageException("Invalid response status");

		this.lResponseStatus = status;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see #get(HTTPResponseSupplier)
	 */
	@Override
	public HTTPResponse get() {
		return this.get(HTTPResponseSupplier.DEFAULT);
	}

	/**
	 * Returns the {@link HTTPResponse} after successfully receiving it. See {@link #get()}.
	 * 
	 * @param <T> The specific {@code HTTPResponse} type this method creates
	 * @param supplier A {@link HTTPResponseSupplier} for creating the {@link HTTPResponse}
	 * @return The {@link HTTPResponse}
	 * @throws IllegalStateException If no {@code HTTPMessage} was received yet
	 */
	public <T extends HTTPResponse> T get(HTTPResponseSupplier<T> supplier) {
		if(!super.isStartLineReceived())
			throw new IllegalStateException("No valid message received");
		return msgInit(supplier.get(this.lResponseStatus, super.getVersion(), super.lHeaders));
	}

	@Override
	public void reset() {
		super.reset();
		this.lResponseStatus = 0;
	}
}
