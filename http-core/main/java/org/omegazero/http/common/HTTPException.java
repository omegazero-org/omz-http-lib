/*
 * Copyright (C) 2023 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.common;

import java.io.IOException;

/**
 * An exception thrown when any kind of HTTP protocol error is encountered.
 *
 * @since 1.4.1
 */
public class HTTPException extends IOException {

	private static final long serialVersionUID = 1L;

	/**
	 * Creates a new {@link HTTPException} with no detail message.
	 */
	public HTTPException(){
		super();
	}

	/**
	 * Creates a new {@link HTTPException} with the given detail message.
	 *
	 * @param msg The detail message
	 */
	public HTTPException(String msg){
		super(msg);
	}

	/**
	 * Creates a new {@link HTTPException} with the given detail message and cause.
	 *
	 * @param msg The detail message
	 * @param cause The cause
	 */
	public HTTPException(String msg, Throwable cause){
		super(msg, cause);
	}
}
