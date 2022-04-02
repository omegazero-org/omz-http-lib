/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2.util;

/**
 * Contains utility methods for managing <i>HTTP/2</i> frames.
 * 
 * @since 1.2.1
 */
public final class FrameUtil {


	private FrameUtil() {
	}


	/**
	 * Reads a 16-bit integer in network byte order (big endian) from the given <b>data</b>, starting at <b>offset</b>.
	 * 
	 * @param data The data
	 * @param offset The offset of the integer
	 * @return The 16-bit integer
	 * @see #writeInt16BE(byte[], int, int)
	 * @see #readInt32BE(byte[], int)
	 */
	public static int readInt16BE(byte[] data, int offset) {
		return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
	}

	/**
	 * Writes a 16-bit integer in network byte order (big endian) to the given <b>data</b>, starting at <b>offset</b>.
	 * 
	 * @param data The data to write to
	 * @param offset The offset of the integer
	 * @param value The value to write
	 * @see #readInt16BE(byte[], int)
	 * @see #writeInt32BE(byte[], int, int)
	 */
	public static void writeInt16BE(byte[] data, int offset, int value) {
		data[offset] = (byte) (value >> 8);
		data[offset + 1] = (byte) value;
	}

	/**
	 * Reads a 32-bit integer in network byte order (big endian) from the given <b>data</b>, starting at <b>offset</b>.
	 * 
	 * @param data The data
	 * @param offset The offset of the integer
	 * @return The 32-bit integer
	 * @see #writeInt32BE(byte[], int, int)
	 * @see #readInt16BE(byte[], int)
	 */
	public static int readInt32BE(byte[] data, int offset) {
		return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16) | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
	}

	/**
	 * Writes a 32-bit integer in network byte order (big endian) to the given <b>data</b>, starting at <b>offset</b>.
	 * 
	 * @param data The data to write to
	 * @param offset The offset of the integer
	 * @param value The value to write
	 * @see #readInt32BE(byte[], int)
	 * @see #writeInt16BE(byte[], int, int)
	 */
	public static void writeInt32BE(byte[] data, int offset, int value) {
		data[offset] = (byte) (value >> 24);
		data[offset + 1] = (byte) (value >> 16);
		data[offset + 2] = (byte) (value >> 8);
		data[offset + 3] = (byte) value;
	}

	/**
	 * Creates a new 4-byte array and {@linkplain #writeInt32BE(byte[], int, int) writes} the given <b>value</b> to it in network byte order.
	 * 
	 * @param value The value to store in the new array
	 * @return The array containing the value
	 */
	public static byte[] int32BE(int value) {
		byte[] data = new byte[4];
		writeInt32BE(data, 0, value);
		return data;
	}
}
