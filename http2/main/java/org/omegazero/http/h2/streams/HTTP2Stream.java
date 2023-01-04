/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2.streams;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.util.ArrayUtil;
import org.omegazero.http.h2.HTTP2ConnectionError;
import org.omegazero.http.h2.util.FrameUtil;
import org.omegazero.http.util.WritableSocket;

import static org.omegazero.http.h2.util.HTTP2Constants.*;

/**
 * Represents a <i>HTTP/2</i> stream where frames can be sent or received.
 * 
 * @since 1.2.1
 */
public abstract class HTTP2Stream {

	private static final Logger logger = Logger.create();


	protected final int streamId;
	protected final WritableSocket connection;

	protected Object windowSizeLock = new Object();
	protected int receiverWindowSize;
	protected int localWindowSize;

	/**
	 * Creates a {@link HTTP2Stream} instance.
	 * 
	 * @param streamId The stream ID
	 * @param connection The underlying connection
	 */
	public HTTP2Stream(int streamId, WritableSocket connection) {
		this.streamId = streamId;
		this.connection = connection;
	}


	/**
	 * This method is called if a frame is received on this <i>HTTP/2</i> stream.
	 * 
	 * @param type The frame type number
	 * @param flags The frame flags
	 * @param data The frame payload
	 * @throws HTTP2ConnectionError If a <i>HTTP/2</i> connection error occurs, for example because the frame payload is invalid
	 */
	public void receiveFrame(int type, int flags, byte[] data) throws HTTP2ConnectionError {
		if(type == FRAME_TYPE_WINDOW_UPDATE){
			if(data.length != 4)
				throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
			int v = FrameUtil.readInt32BE(data, 0);
			if(v < 1)
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, this.streamId != 0);
			synchronized(this.windowSizeLock){
				int nws = this.receiverWindowSize + v;
				if(nws < 0) // overflow
					throw new HTTP2ConnectionError(STATUS_FLOW_CONTROL_ERROR, this.streamId != 0);
				if(logger.debug())
					logger.trace(this.connection.getRemoteName(), ": Stream ", this.streamId, " received window update: ", this.receiverWindowSize, " -> ", nws);
				this.receiverWindowSize = nws;
			}
			this.windowUpdate();
		}else if(type >= 0 && type < FRAME_TYPES)
			throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR);
	}

	/**
	 * Writes a frame with the given properties to the {@link WritableSocket} passed in the constructor.
	 * 
	 * @param type The frame type
	 * @param flags The frame flags
	 * @param data The frame payload
	 */
	public void writeFrame(int type, int flags, byte[] data){
		this.writeFrame(type, flags, data, 0, data.length);
	}

	/**
	 * Writes a frame with the given properties to the {@link WritableSocket} passed in the constructor.
	 * 
	 * @param type The frame type
	 * @param flags The frame flags
	 * @param data The frame payload
	 * @param offset The index in <b>data</b> to start reading from
	 * @param length The total amount of bytes in <b>data</b> to write
	 * @throws IndexOutOfBoundsException If <b>offset</b> or <b>length</b> is invalid
	 */
	public void writeFrame(int type, int flags, byte[] data, int offset, int length){
		writeFrame(this.connection, this.streamId, type, flags, data, offset, length);
	}

	/**
	 * Writes a {@linkplain org.omegazero.http.h2.util.HTTP2Constants#FRAME_TYPE_WINDOW_UPDATE window update} frame on this stream and updates internal window size information.
	 * 
	 * @param increment The window size increment
	 */
	public void sendWindowSizeUpdate(int increment){
		if(increment <= 0)
			throw new IllegalArgumentException("Invalid window size increment: " + increment);
		synchronized(this.windowSizeLock){
			int nws = this.localWindowSize + increment;
			if(nws < 0)
				nws = Integer.MAX_VALUE;
			this.localWindowSize = nws;
		}
		this.writeFrame(FRAME_TYPE_WINDOW_UPDATE, 0, FrameUtil.int32BE(increment));
	}


	/**
	 * This method is called when a {@linkplain org.omegazero.http.h2.util.HTTP2Constants#FRAME_TYPE_WINDOW_UPDATE window update} frame is
	 * {@linkplain #receiveFrame(int, int, byte[]) received}. The {@link #receiverWindowSize} is updated before this method is called.
	 */
	protected void windowUpdate() {
	}


	/**
	 * Returns the ID of this {@link HTTP2Stream}.
	 * 
	 * @return The stream ID
	 */
	public int getStreamId() {
		return this.streamId;
	}

	/**
	 * Returns the {@link WritableSocket} configured in the constructor.
	 * 
	 * @return The {@code WritableSocket}
	 */
	public WritableSocket getConnection() {
		return this.connection;
	}


	/**
	 * Returns {@code true} if the given frame type is a flow-controlled frame type.
	 * 
	 * @param type The frame type
	 * @return {@code true} if the given frame type is a flow-controlled frame type
	 */
	public static boolean isFlowControlledFrameType(int type) {
		return type == FRAME_TYPE_DATA;
	}

	/**
	 * Writes a frame with the given properties to the given {@link WritableSocket}.
	 * 
	 * @param connection The {@code WritableSocket} to write the encoded frame to
	 * @param streamId The stream ID to write the frame on
	 * @param type The frame type
	 * @param flags The frame flags
	 * @param data The frame payload
	 * @param offset The offset in <b>data</b>
	 * @param length The number of bytes in <b>data</b> to write
	 */
	public static void writeFrame(WritableSocket connection, int streamId, int type, int flags, byte[] data, int offset, int length){
		ArrayUtil.checkBounds(data, offset, length);
		if(logger.debug())
			logger.trace("local -> ", connection.getRemoteName(), " HTTP2 frame: stream=", streamId, " type=", type, " flags=", flags, " length=", length);
		byte[] frameHeader = new byte[FRAME_HEADER_SIZE];
		frameHeader[0] = (byte) (length >> 16);
		frameHeader[1] = (byte) (length >> 8);
		frameHeader[2] = (byte) length;
		frameHeader[3] = (byte) type;
		frameHeader[4] = (byte) flags;
		FrameUtil.writeInt32BE(frameHeader, 5, streamId);
		synchronized(connection){
			connection.write(frameHeader);
			connection.write(data, offset, length);
			connection.flush();
		}
	}
}
