/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2.util;

import java.io.IOException;

/**
 * A functional interface similar to a {@link java.util.function.Consumer Consumer}, except that the functional method may throw an {@link IOException}.
 * <p>
 * This interface is used as a callback in <i>HTTP/2</i> stream implementations.
 * 
 * @param <T> The type of the input parameter
 * @since 1.2.1
 */
@FunctionalInterface
public interface StreamCallback<T> {

	/**
	 * The callback method.
	 * 
	 * @param t The input parameter
	 * @throws IOException If an IO error occurs
	 */
	public void accept(T t) throws IOException;
}
