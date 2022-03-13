/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.gen;

import org.omegazero.http.common.HTTPHeaderContainer;
import org.omegazero.http.common.HTTPResponse;

/**
 * A functional interface for creating {@link HTTPResponse}s.
 * <p>
 * This may be useful to an application using parsers of this library for creating special subtypes of {@code HTTPResponse}s, since the parsers would otherwise only create
 * instances of regular {@link HTTPResponse}s, possibly limiting the application.
 * 
 * @param <T> A specific {@code HTTPResponse} type
 * @since 1.2.1
 * @see HTTPRequestSupplier
 */
@FunctionalInterface
public interface HTTPResponseSupplier<T extends HTTPResponse> {

	/**
	 * The default {@link HTTPResponseSupplier}, which is the {@linkplain HTTPResponse#HTTPResponse(int, String, HTTPHeaderContainer) constructor} of {@link HTTPResponse}.
	 */
	public static final HTTPResponseSupplier<HTTPResponse> DEFAULT = HTTPResponse::new;


	/**
	 * Creates a new {@link HTTPResponse}.
	 * 
	 * @param status The HTTP response status code
	 * @param version The HTTP version
	 * @param headers The HTTP headers
	 * @return The new {@link HTTPResponse}
	 * @see #DEFAULT
	 */
	public T get(int status, String version, HTTPHeaderContainer headers);
}
