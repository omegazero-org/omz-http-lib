/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h1;

import java.util.Arrays;

import org.omegazero.http.common.HTTPRequestData;
import org.omegazero.http.common.HTTPResponseData;
import org.omegazero.http.common.InvalidHTTPMessageException;

/**
 * HTTP/1 utility functions.
 * 
 * @since 1.2.1
 */
public final class HTTP1Util {

	public static final String EOL_STR = "\r\n";

	static final byte[] HEADER_END = new byte[] { 0x0d, 0x0a, 0x0d, 0x0a };
	static final byte[] EOL = new byte[] { 0xd, 0xa };


	private HTTP1Util() {
	}


	/**
	 * Wraps the given <b>data</b> in a HTTP/1 data chunk used in chunked transfer encoding.
	 * <p>
	 * The resulting byte array has this format:
	 * 
	 * <pre>
	 * <code>
	 * &lt;length of data as hexadecimal number&gt;&#92;r&#92;n
	 * &lt;given data&gt;&#92;r&#92;n
	 * </code>
	 * </pre>
	 * 
	 * @param data The data to wrap
	 * @return The data as a chunk
	 */
	public static byte[] toChunk(byte[] data) {
		byte[] hexlen = Integer.toString(data.length, 16).getBytes();
		int chunkFrameSize = data.length + hexlen.length + EOL.length * 2;
		byte[] chunk = new byte[chunkFrameSize];
		int i = 0;
		System.arraycopy(hexlen, 0, chunk, i, hexlen.length);
		i += hexlen.length;
		System.arraycopy(EOL, 0, chunk, i, EOL.length);
		i += EOL.length;
		System.arraycopy(data, 0, chunk, i, data.length);
		i += data.length;
		System.arraycopy(EOL, 0, chunk, i, EOL.length);
		i += EOL.length;
		return chunk;
	}

	/**
	 * Parses a complete HTTP request header block using a {@link HTTP1RequestReceiver} from the given <b>data</b>, starting at <b>offset</b>.
	 * 
	 * @param data The data
	 * @param offset The index to start reading at
	 * @param secure See {@link HTTP1RequestReceiver#HTTP1RequestReceiver(boolean)}
	 * @return A {@link HTTPRequestData} containing the HTTP header and any trailing body data
	 * @throws InvalidHTTPMessageException If the given data contains an invalid or incomplete HTTP request header
	 */
	public static HTTPRequestData parseHTTPRequest(byte[] data, int offset, boolean secure) throws InvalidHTTPMessageException {
		HTTP1RequestReceiver requestReceiver = new HTTP1RequestReceiver(secure);
		int dataStart = requestReceiver.receive(data, offset);
		if(dataStart < 0)
			throw new InvalidHTTPMessageException("Incomplete header block");
		return new HTTPRequestData(requestReceiver.get(), Arrays.copyOfRange(data, dataStart, data.length));
	}

	/**
	 * Parses a complete HTTP response header block using a {@link HTTP1ResponseReceiver} from the given <b>data</b>, starting at <b>offset</b>.
	 * 
	 * @param data The data
	 * @param offset The index to start reading at
	 * @return A {@link HTTPResponseData} containing the HTTP header and any trailing body data
	 * @throws InvalidHTTPMessageException If the given data contains an invalid or incomplete HTTP response header
	 */
	public static HTTPResponseData parseHTTPResponse(byte[] data, int offset) throws InvalidHTTPMessageException {
		HTTP1ResponseReceiver responseReceiver = new HTTP1ResponseReceiver();
		int dataStart = responseReceiver.receive(data, offset);
		if(dataStart < 0)
			throw new InvalidHTTPMessageException("Incomplete header block");
		return new HTTPResponseData(responseReceiver.get(), Arrays.copyOfRange(data, dataStart, data.length));
	}
}
