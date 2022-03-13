/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.gen;

import org.omegazero.http.common.HTTPHeaderContainer;
import org.omegazero.http.common.HTTPRequest;

/**
 * A functional interface for creating {@link HTTPRequest}s.
 * <p>
 * This may be useful to an application using parsers of this library for creating special subtypes of {@code HTTPRequest}s, since the parsers would otherwise only create
 * instances of regular {@link HTTPRequest}s, possibly limiting the application.
 * 
 * @param <T> A specific {@code HTTPRequest} type
 * @since 1.2.1
 * @see HTTPResponseSupplier
 */
@FunctionalInterface
public interface HTTPRequestSupplier<T extends HTTPRequest> {

	/**
	 * The default {@link HTTPRequestSupplier}, which is the {@linkplain HTTPRequest#HTTPRequest(String, String, String, String, String, HTTPHeaderContainer) constructor} of
	 * {@link HTTPRequest}.
	 */
	public static final HTTPRequestSupplier<HTTPRequest> DEFAULT = HTTPRequest::new;


	/**
	 * Creates a new {@link HTTPRequest}.
	 * 
	 * @param method The request method
	 * @param scheme The request URL scheme
	 * @param authority The request URL authority
	 * @param path The request URL path component
	 * @param version The HTTP version
	 * @param headers The HTTP headers
	 * @return The new {@link HTTPRequest}
	 * @see #DEFAULT
	 */
	public T get(String method, String scheme, String authority, String path, String version, HTTPHeaderContainer headers);
}
