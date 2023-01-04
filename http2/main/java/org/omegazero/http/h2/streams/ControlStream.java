/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2.streams;

import java.util.Arrays;
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.util.ReflectionUtil;
import org.omegazero.http.h2.HTTP2ConnectionError;
import org.omegazero.http.h2.util.FrameUtil;
import org.omegazero.http.h2.util.HTTP2Constants;
import org.omegazero.http.h2.util.HTTP2Settings;
import org.omegazero.http.util.WritableSocket;

import static org.omegazero.http.h2.util.HTTP2Constants.*;

/**
 * Represents the connection {@link HTTP2Stream} where frames are exchanged that affect the entire HTTP/2 connection (stream with ID equal to 0).
 * 
 * @since 1.2.1
 */
public class ControlStream extends HTTP2Stream {

	private static final Logger logger = Logger.create();

	private static final String[] SETTINGS_NAMES;


	private final HTTP2Settings localSettings;
	private final HTTP2Settings remoteSettings = new HTTP2Settings();

	private Consumer<HTTP2Settings> onSettingsUpdate;
	private Runnable onWindowUpdate;
	private boolean settingsReceived = false;

	/**
	 * Creates a {@link ControlStream} instance.
	 * 
	 * @param connection The underlying connection
	 * @param localSettings The local {@link HTTP2Settings}
	 */
	public ControlStream(WritableSocket connection, HTTP2Settings localSettings) {
		super(0, connection);
		this.localSettings = localSettings;

		super.receiverWindowSize = this.remoteSettings.get(SETTINGS_INITIAL_WINDOW_SIZE);
		super.localWindowSize = localSettings.get(SETTINGS_INITIAL_WINDOW_SIZE);
	}


	/**
	 * Sends a <i>{@linkplain org.omegazero.http.h2.util.HTTP2Constants#FRAME_TYPE_GOAWAY GOAWAY}</i> frame with the given stream ID and error code on this control stream.
	 * 
	 * @param highestStreamId The last processed stream ID
	 * @param errorCode The error code
	 */
	public void sendGoaway(int highestStreamId, int errorCode){
		byte[] payload = new byte[8];
		FrameUtil.writeInt32BE(payload, 0, highestStreamId);
		FrameUtil.writeInt32BE(payload, 4, errorCode);
		super.writeFrame(FRAME_TYPE_GOAWAY, 0, payload);
	}

	/**
	 * Sends a <i>{@linkplain org.omegazero.http.h2.util.HTTP2Constants#FRAME_TYPE_SETTINGS SETTINGS}</i> frame with the given {@link HTTP2Settings} data on this control stream.
	 * 
	 * @param settings The settings
	 */
	public void writeSettings(HTTP2Settings settings){
		byte[] buf = new byte[SETTINGS_COUNT * 6];
		int buflen = 0;
		for(int i = 1; i < SETTINGS_COUNT; i++){
			if(settings.get(i) != HTTP2Settings.getDefault(i)){
				FrameUtil.writeInt16BE(buf, buflen, i);
				FrameUtil.writeInt32BE(buf, buflen + 2, settings.get(i));
				buflen += 6;
			}
		}
		super.writeFrame(FRAME_TYPE_SETTINGS, 0, Arrays.copyOf(buf, buflen));
	}


	@Override
	public void receiveFrame(int type, int flags, byte[] data) throws HTTP2ConnectionError {
		if(type == FRAME_TYPE_SETTINGS){
			if((flags & FRAME_FLAG_ANY_ACK) != 0){
				if(data.length > 0)
					throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
				return;
			}
			int settingsCount = data.length / 6;
			if(settingsCount * 6 != data.length)
				throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
			for(int i = 0; i < settingsCount; i++){
				int setting = FrameUtil.readInt16BE(data, i * 6);
				int value = FrameUtil.readInt32BE(data, i * 6 + 2);
				if(setting < 0 || setting >= SETTINGS_COUNT)
					continue;
				if(setting == SETTINGS_ENABLE_PUSH && !(value == 0 || value == 1))
					throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "ENABLE_PUSH is invalid");
				if(setting == SETTINGS_MAX_FRAME_SIZE && (value < SETTINGS_MAX_FRAME_SIZE_MIN || value > SETTINGS_MAX_FRAME_SIZE_MAX))
					throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "MAX_FRAME_SIZE is invalid");
				if(setting == SETTINGS_INITIAL_WINDOW_SIZE && value < 0)
					throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "INITIAL_WINDOW_SIZE is invalid");
				if(logger.debug())
					logger.trace(super.connection.getRemoteName(), ": SETTINGS: ", SETTINGS_NAMES[setting], " [", setting, "] = ", value);
				this.remoteSettings.set(setting, value);
			}
			this.settingsReceived = true;
			logger.debug(super.connection.getRemoteName(), ": Successfully received and processed SETTINGS frame, containing ", settingsCount, " settings");
			super.writeFrame(FRAME_TYPE_SETTINGS, FRAME_FLAG_ANY_ACK, new byte[0]);
			this.onSettingsUpdate.accept(this.remoteSettings);
		}else if(type == FRAME_TYPE_PING){
			if((flags & FRAME_FLAG_ANY_ACK) != 0)
				return;
			if(data.length != 8)
				throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
			logger.trace(super.connection.getRemoteName(), ": Received PING request");
			super.writeFrame(FRAME_TYPE_PING, FRAME_FLAG_ANY_ACK, data);
		}else if(type == FRAME_TYPE_GOAWAY){
			if(data.length < 8)
				throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
			int lastStreamId = FrameUtil.readInt32BE(data, 0) & 0x7fffffff;
			int status = FrameUtil.readInt32BE(data, 4);
			logger.debug(super.connection.getRemoteName(), ": Received GOAWAY frame with status code ", HTTP2ConnectionError.getStatusCodeName(status), " and lastStreamId=",
					lastStreamId);
		}else
			super.receiveFrame(type, flags, data);
	}

	@Override
	protected void windowUpdate() {
		super.windowUpdate();
		if(this.onWindowUpdate != null)
			this.onWindowUpdate.run();
	}


	/**
	 * Sets the callback that is called when a <i>SETTINGS</i> frame is received from the remote endpoint.
	 * 
	 * @param onSettingsUpdate The callback
	 */
	public void setOnSettingsUpdate(Consumer<HTTP2Settings> onSettingsUpdate) {
		this.onSettingsUpdate = onSettingsUpdate;
	}

	/**
	 * Sets the callback that is called when a <i>WINDOW_UPDATE</i> frame is received from the remote endpoint.
	 * 
	 * @param onWindowUpdate The callback
	 */
	public void setOnWindowUpdate(Runnable onWindowUpdate) {
		this.onWindowUpdate = onWindowUpdate;
	}

	/**
	 * Returns {@code true} if a <i>SETTINGS</i> frame was received.
	 * 
	 * @return {@code true} if a <i>SETTINGS</i> frame was received
	 */
	public boolean isSettingsReceived() {
		return this.settingsReceived;
	}

	/**
	 * Returns the local {@link HTTP2Settings}.
	 * 
	 * @return The local {@link HTTP2Settings}
	 */
	public HTTP2Settings getLocalSettings() {
		return this.localSettings;
	}

	/**
	 * Returns the {@link HTTP2Settings} sent by the remote endpoint.
	 * 
	 * @return The remote {@link HTTP2Settings}
	 */
	public HTTP2Settings getRemoteSettings() {
		return this.remoteSettings;
	}


	/**
	 * Returns the connection window size of the remote endpoint.
	 * 
	 * @return The remote connection window size
	 */
	public int getReceiverWindowSize() {
		return super.receiverWindowSize;
	}

	/**
	 * Returns the connection window size of the local endpoint.
	 * 
	 * @return The local connection window size
	 */
	public int getLocalWindowSize() {
		return super.localWindowSize;
	}

	/**
	 * Reduces the remote's connection window by the given <b>size</b> because a flow-controlled packet is being sent.
	 * 
	 * @param size The size of the payload of the flow-controlled packet
	 */
	public void consumeReceiverConnectionWindow(int size) {
		synchronized(super.windowSizeLock){
			if(size > super.receiverWindowSize)
				throw new IllegalStateException("size is larger than receiver window size");
			super.receiverWindowSize -= size;
		}
	}

	/**
	 * Reduces the local connection window by the given <b>size</b> because a flow-controlled packet was received.
	 * 
	 * @param size The size of the payload of the flow-controlled packet
	 */
	public void consumeLocalConnectionWindow(int size) {
		synchronized(super.windowSizeLock){
			if(size > super.localWindowSize)
				throw new IllegalStateException("size is larger than local window size");
			super.localWindowSize -= size;
		}
	}


	static{
		try{
			SETTINGS_NAMES = ReflectionUtil.getIntegerFieldNames(HTTP2Constants.class, "SETTINGS_", 0, SETTINGS_COUNT, false);
		}catch(IllegalAccessException e){
			throw new RuntimeException(e);
		}
	}
}
