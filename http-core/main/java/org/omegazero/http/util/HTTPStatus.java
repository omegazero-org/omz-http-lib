/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.util;

/**
 * Contains standard or widely used HTTP status code numbers.
 * 
 * @since 1.2.1
 */
public final class HTTPStatus {

	public static final int STATUS_CONTINUE = 100;
	public static final int STATUS_SWITCHING_PROTOCOLS = 101;
	public static final int STATUS_PROCESSING = 102;
	public static final int STATUS_EARLY_HINTS = 102;
	public static final int STATUS_OK = 200;
	public static final int STATUS_CREATED = 201;
	public static final int STATUS_ACCEPTED = 202;
	public static final int STATUS_NON_AUTHORITATIVE = 203;
	public static final int STATUS_NO_CONTENT = 204;
	public static final int STATUS_RESET_CONTENT = 205;
	public static final int STATUS_PARTIAL_CONTENT = 206;
	public static final int STATUS_MULTIPLE_CHOICES = 300;
	public static final int STATUS_MOVED_PERMANENTLY = 301;
	public static final int STATUS_FOUND = 302;
	public static final int STATUS_SEE_OTHER = 303;
	public static final int STATUS_NOT_MODIFIED = 304;
	public static final int STATUS_TEMPORARY_REDIRECT = 307;
	public static final int STATUS_PERMANENT_REDIRECT = 308;
	public static final int STATUS_BAD_REQUEST = 400;
	public static final int STATUS_UNAUTHORIZED = 401;
	public static final int STATUS_FORBIDDEN = 403;
	public static final int STATUS_NOT_FOUND = 404;
	public static final int STATUS_METHOD_NOT_ALLOWED = 405;
	public static final int STATUS_NOT_ACCEPTABLE = 406;
	public static final int STATUS_PROXY_AUTHENTICATION_REQUIRED = 407;
	public static final int STATUS_REQUEST_TIMEOUT = 408;
	public static final int STATUS_CONFLICT = 409;
	public static final int STATUS_GONE = 410;
	public static final int STATUS_LENGTH_REQUIRED = 411;
	public static final int STATUS_PRECONDITION_REQUIRED = 412;
	public static final int STATUS_PAYLOAD_TOO_LARGE = 413;
	public static final int STATUS_URI_TOO_LONG = 414;
	public static final int STATUS_UNSUPPORTED_MEDIA_TYPE = 415;
	public static final int STATUS_RANGE_NOT_SATISFIABLE = 416;
	public static final int STATUS_EXPECTATION_FAILED = 417;
	public static final int STATUS_IM_A_TEAPOT = 418;
	public static final int STATUS_UPGRADE_REQUIRED = 426;
	public static final int STATUS_PRECONDITION_FAILED = 428;
	public static final int STATUS_TOO_MANY_REQUESTS = 429;
	public static final int STATUS_REQUEST_HEADER_FIELDS_TOO_LARGE = 431;
	public static final int STATUS_UNAVAILABLE_FOR_LEGAL_REASONS = 451;
	public static final int STATUS_INTERNAL_SERVER_ERROR = 500;
	public static final int STATUS_NOT_IMPLEMENTED = 501;
	public static final int STATUS_BAD_GATEWAY = 502;
	public static final int STATUS_SERVICE_UNAVAILABLE = 503;
	public static final int STATUS_GATEWAY_TIMEOUT = 504;
	public static final int STATUS_HTTP_VERSION_NOT_SUPPORTED = 505;
	public static final int STATUS_VARIANT_ALSO_NEGOTIATES = 506;
	public static final int STATUS_LOOP_DETECTED = 508;
	public static final int STATUS_NOT_EXTENDED = 510;
	public static final int STATUS_NETWORK_AUTHENTICATION_REQUIRED = 511;

	private static final String[] STATUS_NAMES = new String[500];


	private HTTPStatus() {
	}


	/**
	 * Returns a user-friendly string representation for each status code defined in this class.
	 * 
	 * @param status The status code to get a string for
	 * @return The string
	 */
	public static String getStatusName(int status) {
		return STATUS_NAMES[status - 100];
	}


	static{
		java.lang.reflect.Field[] fields = HTTPStatus.class.getFields();
		for(java.lang.reflect.Field field : fields){
			String name = field.getName();
			if(name.startsWith("STATUS_") && field.getType() == int.class){
				StringBuilder sb = new StringBuilder(name.length() - 7 /* length of "STATUS_" == 7 */);
				boolean u = true;
				for(int i = 7; i < name.length(); i++){
					char c = name.charAt(i);
					if(c == '_'){
						sb.append(' ');
						u = true;
					}else{
						if(!u)
							c += 32;
						sb.append(c);
						u = false;
					}
				}
				try{
					STATUS_NAMES[field.getInt(null) - 100] = sb.toString();
				}catch(IllegalAccessException e){
					throw new RuntimeException(e);
				}
			}
		}
	}
}
