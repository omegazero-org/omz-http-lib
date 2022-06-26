/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http;

/**
 * This class contains metadata about <i>omz-http-lib</i> (for example the {@linkplain #VERSION version string}).
 */
public class HTTPLib {

	/**
	 * The version string of <i>omz-http-lib</i>.
	 * <p>
	 * This value is set by the CI build pipeline based on the event that triggered the build. Otherwise, this string is always <code>"$BUILDVERSION"</code>.
	 * <p>
	 * {@link #getVersion()} should be used to retrieve the value to prevent compile-time string inlining.
	 */
	public static final String VERSION = "$BUILDVERSION";


	/**
	 * Returns the {@linkplain #VERSION version string}.
	 * 
	 * @return The version string
	 * @since 1.2.2
	 */
	public static String getVersion() {
		return VERSION;
	}
}
