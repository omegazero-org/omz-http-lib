/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2;

import java.io.IOException;

import org.omegazero.common.util.ReflectionUtil;
import org.omegazero.http.h2.util.HTTP2Constants;

/**
 * Represents a <i>HTTP/2</i> connection or stream error.
 * 
 * @since 1.2.1
 * @apiNote Despite the name, this class is an {@code Exception} (specifically an {@link IOException}) not an {@code Error}. The name was chosen to align more closely with the
 * terms used in the specification.
 */
public class HTTP2ConnectionError extends IOException {

	private static final long serialVersionUID = 1L;

	private static final String[] STATUS_NAMES;
	private static final String[] STATUS_NAMES_NUM;


	private final int status;
	private final boolean streamError;

	/**
	 * Creates a new {@link HTTP2ConnectionError}, as a connection error.
	 * 
	 * @param status The status code
	 */
	public HTTP2ConnectionError(int status) {
		this(status, false);
	}

	/**
	 * Creates a new {@link HTTP2ConnectionError}.
	 * 
	 * @param status The status code
	 * @param streamError Whether this instance represents a stream or connection error
	 */
	public HTTP2ConnectionError(int status, boolean streamError) {
		this(status, streamError, null);
	}

	/**
	 * Creates a new {@link HTTP2ConnectionError}, as a connection error with the given message.
	 * 
	 * @param status The status code
	 * @param msg The error message
	 */
	public HTTP2ConnectionError(int status, String msg) {
		this(status, false, msg);
	}

	/**
	 * Creates a new {@link HTTP2ConnectionError} with the given message.
	 * 
	 * @param status The status code
	 * @param streamError Whether this instance represents a stream or connection error
	 * @param msg The error message
	 */
	public HTTP2ConnectionError(int status, boolean streamError, String msg) {
		super(getExceptionMessage(status, streamError, msg));
		this.status = status;
		this.streamError = streamError;
	}


	/**
	 * Returns the status code passed in the constructor.
	 * 
	 * @return The status code
	 */
	public int getStatus() {
		return this.status;
	}

	/**
	 * Returns whether this instance represents a stream or connection error, as configured in the constructor.
	 * 
	 * @return {@code true} if this instance represents a stream error
	 */
	public boolean isStreamError() {
		return this.streamError;
	}


	/**
	 * Returns a string of the format {@code NAME [NUMBER]}, where {@code NAME} is the string name of the given status code, and {@code NUMBER} is the given status code number.
	 * 
	 * @param status The status code
	 * @return The string
	 */
	public static String getStatusCodeName(int status) {
		if(status < 0 || status >= STATUS_NAMES_NUM.length)
			return "<invalid> [" + status + "]";
		else if(STATUS_NAMES_NUM[status] != null)
			return STATUS_NAMES_NUM[status];
		String m = STATUS_NAMES[status] + " [" + status + "]";
		STATUS_NAMES_NUM[status] = m;
		return m;
	}

	private static String getExceptionMessage(int status, boolean streamError, String msg) {
		String fmsg = (streamError ? "Stream" : "Connection") + " error: " + getStatusCodeName(status);
		if(msg != null)
			fmsg += " (" + msg + ")";
		return fmsg;
	}

	static{
		try{
			STATUS_NAMES = ReflectionUtil.getIntegerFieldNames(HTTP2Constants.class, "STATUS_", 0, 14, false);
			STATUS_NAMES_NUM = new String[STATUS_NAMES.length];
		}catch(IllegalAccessException e){
			throw new RuntimeException(e);
		}
	}
}
