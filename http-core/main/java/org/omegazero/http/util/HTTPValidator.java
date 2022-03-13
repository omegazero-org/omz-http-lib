/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

/**
 * Contains several methods for validating HTTP message components.
 * 
 * @since 1.2.1
 */
public final class HTTPValidator {


	private HTTPValidator() {
	}


	/**
	 * Verifies the given HTTP request method string. This string is considered valid if it is not <code>null</code> and consists solely of 2-10 uppercase letters.
	 * 
	 * @param s The string to check
	 * @return <code>true</code> if the given string is a valid HTTP request method string
	 * @see #validMethod(byte[], int, int)
	 */
	public static boolean validMethod(String s) {
		if(s == null || s.length() < 2 || s.length() > 10)
			return false;
		for(int i = 0; i < s.length(); i++){
			char c = s.charAt(i);
			if(c < 'A' || c > 'Z')
				return false;
		}
		return true;
	}

	/**
	 * Checks that the first <b>length</b> bytes in the given byte array, starting at <b>offset</b>, represent a {@linkplain #validMethod(String) valid} HTTP request method
	 * string.
	 * 
	 * @param bytes The byte array
	 * @param offset The index to start at
	 * @param length The number of bytes to check
	 * @return <code>true</code> if the given bytes represent a valid HTTP request method string
	 * @see #validMethod(String)
	 */
	public static boolean validMethod(byte[] bytes, int offset, int length) {
		if(bytes == null || length < 2 || length > 10)
			return false;
		for(int i = offset; i < offset + length; i++){
			byte b = bytes[offset];
			if(b < 'A' || b > 'Z')
				return false;
		}
		return true;
	}

	/**
	 * Verifies the given HTTP URL authority string. This string is considered valid if it is not <code>null</code> and only consists of printable ASCII characters.
	 * 
	 * @param s The string to check
	 * @return <code>true</code> if the given string is a valid HTTP URL authority string
	 * @see #validString(String)
	 */
	public static boolean validAuthority(String s) {
		return validString(s);
	}

	/**
	 * Verifies the given HTTP URL path string. This string is considered valid if it is not <code>null</code> and either starts with a slash (<code>'/'</code>) and only
	 * contains printable ASCII characters or is exactly equal to <code>'*'</code>.
	 * 
	 * @param s The string to check
	 * @return <code>true</code> if the given string is a valid HTTP URL path string
	 * @see #validString(String)
	 */
	public static boolean validPath(String s) {
		if(s == null || s.length() < 1 || !(s.charAt(0) == '/' || s.equals("*")))
			return false;
		return validString(s);
	}

	/**
	 * Verifies the given HTTP response status string. This string is considered valid if consists of exactly 3 digits ({@code '0'} - {@code '9'}).
	 * 
	 * @param s The string to check
	 * @return <code>true</code> if the given string is a valid HTTP response status string
	 * @see #parseStatus(String)
	 */
	public static boolean validStatus(String s) {
		if(s == null || s.length() != 3)
			return false;
		for(int i = 0; i < 3; i++){
			char c = s.charAt(i);
			if(c < '0' || c > '9')
				return false;
		}
		return true;
	}

	/**
	 * Parses the given HTTP response status string.
	 * 
	 * @param s The string to parse
	 * @return The status number that the given string represents, or {@code -1} if the string is not a valid status code
	 * @see #validStatus(String)
	 */
	public static int parseStatus(String s) {
		if(!validStatus(s))
			return -1;
		int status = 0;
		int mul = 1;
		for(int i = 2; i >= 0; i--){
			status += (s.charAt(i) - '0') * mul;
			mul *= 10;
		}
		return status;
	}


	/**
	 * Checks that the given string is not <code>null</code> and only consists of visible printable ASCII characters (range 33 - 126 inclusive).
	 * 
	 * @param s The string to check
	 * @return <code>true</code> if the given string is valid
	 * @see #validString(byte[], int, int)
	 */
	public static boolean validString(String s) {
		if(s == null)
			return false;
		for(int i = 0; i < s.length(); i++){
			char c = s.charAt(i);
			if(c <= 32 || c >= 127)
				return false;
		}
		return true;
	}

	/**
	 * Checks that the first <b>length</b> bytes in the given byte array, starting at <b>offset</b>, only consist of visible printable ASCII characters (range 33 - 126
	 * inclusive).
	 * 
	 * @param bytes The byte array
	 * @param offset The index to start at
	 * @param length The number of bytes to check
	 * @return <code>true</code> if the given bytes are valid
	 * @see #validString(String)
	 */
	public static boolean validString(byte[] bytes, int offset, int length) {
		if(bytes == null)
			return false;
		for(int i = offset; i < offset + length; i++){
			byte b = bytes[i];
			if(b <= 32 || b >= 127)
				return false;
		}
		return true;
	}

	/**
	 * Checks that all bytes in the given byte array in the specified range starting at <b>offset</b> are within the given range of allowed values (<b>min</b> to <b>max</b>,
	 * inclusive).
	 * 
	 * @param bytes The byte array
	 * @param offset The index to start at
	 * @param length The number of bytes to check
	 * @param min The minimum allowed byte value
	 * @param max The maximum allowed byte value
	 * @return <code>true</code> if the given bytes are within the allowed range
	 */
	public static boolean bytesInRange(byte[] bytes, int offset, int length, int min, int max) {
		if(bytes == null)
			return false;
		for(int i = offset; i < offset + length; i++){
			byte b = bytes[i];
			if(b < min || b > max)
				return false;
		}
		return true;
	}
}
