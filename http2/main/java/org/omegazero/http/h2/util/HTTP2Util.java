/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2.util;

import java.util.Arrays;

import org.omegazero.common.util.PropertyUtil;

/**
 * <i>HTTP/2</i> utility functions.
 * 
 * @since 1.2.1
 */
public final class HTTP2Util {

	private static final byte[] CLIENT_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);

	/**
	 * Whether the full stack trace of a <i>HTTP/2</i> error should be printed instead of just the error message.
	 * <p>
	 * System property <code>org.omegazero.http.h2.printStackTraces</code><br>
	 * If this property is not defined and <i>omz-net-lib</i> is available, this value will be the value of <code>org.omegazero.net.common.NetCommon.PRINT_STACK_TRACES</code>. If
	 * neither is the case, this value defaults to {@code false}.
	 */
	public static final boolean PRINT_STACK_TRACES;


	private HTTP2Util() {
	}


	/**
	 * Checks whether the given <b>data</b> is a valid <i>HTTP/2</i> client connection preface.
	 * <p>
	 * A call to this method is equivalent to a call to
	 * 
	 * <pre>
	 * <code>{@link #isValidClientPreface(byte[], int) isValidClientPreface}(data, 0)</code>
	 * </pre>
	 * 
	 * @param data The data
	 * @return {@code true} if the given data is a valid client connection preface
	 */
	public static boolean isValidClientPreface(byte[] data) {
		return isValidClientPreface(data, 0);
	}

	/**
	 * Checks whether the given <b>data</b>, starting at <b>offset</b>, is a valid <i>HTTP/2</i> client connection preface.
	 * 
	 * @param data The data
	 * @param offset The offset
	 * @return {@code true} if the given data is a valid client connection preface
	 * @see <i>RFC 7540, section 3.5</i>
	 */
	public static boolean isValidClientPreface(byte[] data, int offset) {
		if(offset < 0 || offset + CLIENT_PREFACE.length > data.length)
			return false;
		for(int i = 0; i < CLIENT_PREFACE.length; i++){
			if(data[offset + i] != CLIENT_PREFACE[i])
				return false;
		}
		return true;
	}

	/**
	 * Returns a copy of the byte array containing the <i>HTTP/2</i> client connection preface.
	 * 
	 * @return The client connection preface
	 * @see <i>RFC 7540, section 3.5</i>
	 */
	public static byte[] getClientPreface() {
		return Arrays.copyOf(CLIENT_PREFACE, CLIENT_PREFACE.length);
	}

	/**
	 * Returns the length of the <i>HTTP/2</i> client connection preface.
	 * 
	 * @return The client connection preface length in bytes
	 */
	public static int getClientPrefaceLength() {
		return CLIENT_PREFACE.length;
	}


	static{
		boolean pstVal;
		final String pstPropertyKey = "org.omegazero.http.h2.printStackTraces";
		if(PropertyUtil.getString(pstPropertyKey, null) != null){
			pstVal = PropertyUtil.getBoolean(pstPropertyKey, false);
		}else{
			try{
				Class<?> netClass = Class.forName("org.omegazero.net.common.NetCommon");
				java.lang.reflect.Field pst = netClass.getField("PRINT_STACK_TRACES");
				pstVal = pst.getBoolean(null);
			}catch(ReflectiveOperationException e){
				pstVal = false;
			}
		}
		PRINT_STACK_TRACES = pstVal;
	}
}
