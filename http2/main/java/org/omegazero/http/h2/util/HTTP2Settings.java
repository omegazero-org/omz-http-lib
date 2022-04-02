/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2.util;

import java.util.Arrays;

import static org.omegazero.http.h2.util.HTTP2Constants.*;

/**
 * An instance of this class stores <i>HTTP/2</i> settings for a connection.
 * 
 * @since 1.2.1
 */
public class HTTP2Settings {

	private static final int[] DEFAULT_SETTINGS = new int[SETTINGS_COUNT];


	private final int[] data;

	/**
	 * Creates a new {@link HTTP2Settings} with {@linkplain #getDefaultSettingsData() default settings}.
	 */
	public HTTP2Settings() {
		this(getDefaultSettingsData());
	}

	/**
	 * Creates a new {@link HTTP2Settings} with the given settings. The index of each element is the <i>HTTP/2</i> setting ID.
	 * 
	 * @param data The settings
	 * @throws IllegalArgumentException If the array does not have exactly {@link org.omegazero.http.h2.util.HTTP2Constants#SETTINGS_COUNT} elements
	 */
	public HTTP2Settings(int[] data) {
		if(data.length != SETTINGS_COUNT)
			throw new IllegalArgumentException("settings array must have exactly " + SETTINGS_COUNT + " elements");
		this.data = data;
	}

	/**
	 * Creates a new {@link HTTP2Settings} with the given settings.
	 * 
	 * @param settings The settings
	 */
	public HTTP2Settings(HTTP2Settings settings) {
		this.data = Arrays.copyOf(settings.data, settings.data.length);
	}


	/**
	 * Returns the setting identified by the given <b>setting</b> number defined in {@link org.omegazero.http.h2.util.HTTP2Constants HTTPConstants}.
	 * 
	 * @param setting The setting number
	 * @return The value
	 * @throws IllegalArgumentException If the given setting number is invalid
	 */
	public int get(int setting) {
		if(setting < 0 || setting >= SETTINGS_COUNT)
			throw new IllegalArgumentException("Invalid setting number: " + setting);
		return this.data[setting];
	}

	/**
	 * Sets the setting identified by the given <b>setting</b> number defined in {@link org.omegazero.http.h2.util.HTTP2Constants HTTPConstants} to the given <b>value</b>.
	 * 
	 * @param setting The setting number
	 * @param value The value
	 * @throws IllegalArgumentException If the given setting number is invalid
	 */
	public void set(int setting, int value) {
		if(setting < 0 || setting >= SETTINGS_COUNT)
			throw new IllegalArgumentException("Invalid setting number: " + setting);
		this.data[setting] = value;
	}


	/**
	 * Returns the default value for the given <b>setting</b> number defined in {@link org.omegazero.http.h2.util.HTTP2Constants HTTPConstants}.
	 * 
	 * @param setting The setting number
	 * @return The default value
	 * @throws IllegalArgumentException If the given setting number is invalid
	 */
	public static int getDefault(int setting) {
		if(setting < 0 || setting >= SETTINGS_COUNT)
			throw new IllegalArgumentException("Invalid setting number: " + setting);
		return DEFAULT_SETTINGS[setting];
	}

	/**
	 * Returns an array containing the default {@link HTTP2Settings}.
	 * 
	 * @return The settings
	 */
	public static int[] getDefaultSettingsData() {
		return Arrays.copyOf(DEFAULT_SETTINGS, DEFAULT_SETTINGS.length);
	}


	static{
		DEFAULT_SETTINGS[SETTINGS_HEADER_TABLE_SIZE] = 4096;
		DEFAULT_SETTINGS[SETTINGS_ENABLE_PUSH] = 1;
		DEFAULT_SETTINGS[SETTINGS_MAX_CONCURRENT_STREAMS] = Integer.MAX_VALUE;
		DEFAULT_SETTINGS[SETTINGS_INITIAL_WINDOW_SIZE] = 65535;
		DEFAULT_SETTINGS[SETTINGS_MAX_FRAME_SIZE] = SETTINGS_MAX_FRAME_SIZE_MIN;
		DEFAULT_SETTINGS[SETTINGS_MAX_HEADER_LIST_SIZE] = Integer.MAX_VALUE;
	}
}
