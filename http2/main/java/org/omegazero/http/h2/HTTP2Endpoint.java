/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2;

import java.io.IOException;
import java.util.Arrays;
import java.util.Deque;
import java.util.Map;

import org.omegazero.common.logging.Logger;
import org.omegazero.http.h2.hpack.HPackContext;
import org.omegazero.http.h2.streams.ControlStream;
import org.omegazero.http.h2.streams.HTTP2Stream;
import org.omegazero.http.h2.streams.MessageStream;
import org.omegazero.http.h2.util.FrameUtil;
import org.omegazero.http.h2.util.HTTP2Constants;
import org.omegazero.http.h2.util.HTTP2Settings;
import org.omegazero.http.h2.util.HTTP2Util;
import org.omegazero.http.util.WritableSocket;

/**
 * Represents any <i>HTTP/2</i> endpoint (client or server).
 * <p>
 * A {@code HTTP2Endpoint} is initialized with a {@link WritableSocket} representing the underlying connection where frames are exchanged. Incoming data on this connection must be
 * passed to {@link #processData(byte[])}.
 * 
 * @since 1.2.1
 */
public abstract class HTTP2Endpoint {

	private static final Logger logger = Logger.create();


	/**
	 * The underlying connection.
	 */
	protected final WritableSocket connection;
	/**
	 * The local settings.
	 */
	protected final HTTP2Settings settings;
	/**
	 * The {@code HPackContext} used for this {@code HTTP2Endpoint}.
	 */
	protected final HPackContext hpack;

	private final byte[] frameBuffer;
	private int frameBufferSize = 0;
	private int frameExpectedSize = 0;

	/**
	 * Contains active and recently closed streams of this {@code HTTP2Endpoint}.
	 */
	protected Map<Integer, HTTP2Stream> streams = new java.util.concurrent.ConcurrentHashMap<>();
	/**
	 * Contains closed streams which will soon be removed from {@link #streams}.
	 */
	protected Deque<MessageStream> closeWaitStreams = new java.util.LinkedList<>();
	/**
	 * The stream ID of the latest stream that was processed.
	 */
	protected int highestStreamId = 0;

	/**
	 * The time in nanoseconds after which closed streams in {@link #closeWaitTimeout} are removed from {@link #streams}.
	 */
	protected long closeWaitTimeout = 5000000000L;

	/**
	 * Creates a new {@link HTTP2Endpoint} instance.
	 * 
	 * @param connection The underlying connection
	 * @param settings The local settings
	 * @param hpackSession The <i>HPACK</i> session to use
	 * @param useHuffmanEncoding Whether to enable <i>HPACK</i> Huffman Coding
	 */
	public HTTP2Endpoint(WritableSocket connection, HTTP2Settings settings, HPackContext.Session hpackSession, boolean useHuffmanEncoding) {
		this(connection, settings, new HPackContext(hpackSession, useHuffmanEncoding, settings.get(HTTP2Constants.SETTINGS_HEADER_TABLE_SIZE)));
	}

	/**
	 * Creates a new {@link HTTP2Endpoint} instance.
	 * 
	 * @param connection The underlying connection
	 * @param settings The local settings
	 * @param hpack The {@link HPackContext} to use
	 */
	public HTTP2Endpoint(WritableSocket connection, HTTP2Settings settings, HPackContext hpack) {
		this.connection = connection;
		this.settings = settings;
		this.hpack = hpack;

		this.frameBuffer = new byte[settings.get(HTTP2Constants.SETTINGS_MAX_FRAME_SIZE) + HTTP2Constants.FRAME_HEADER_SIZE];
	}


	/**
	 * Processes a frame received on a previously nonexistent stream and creates a new {@code HTTP2Stream} for it, if appropriate. If {@code null} is returned (no stream was
	 * created), and the frame type is not <i>PRIORITY</i>, it will be treated as a connection error of type <i>PROTOCOL_ERROR</i>.
	 * 
	 * @param streamId The stream ID of the new stream
	 * @param type The frame type
	 * @param flags The frame flags
	 * @param payload The frame payload
	 * @return The new stream, or {@code null} if no stream is appropriate
	 * @throws HTTP2ConnectionError If the received frame is invalid or unexpected
	 * @throws IOException If an IO error occurs
	 */
	protected abstract HTTP2Stream newStreamForFrame(int streamId, int type, int flags, byte[] payload) throws IOException;


	/**
	 * Data received on the underlying connection of this {@link HTTP2Endpoint} is passed to this method for further processing.
	 * 
	 * @param data The received data
	 */
	public void processData(byte[] data) {
		int index = 0;
		while(index < data.length){
			index = this.processData0(data, index);
			if(index < 0)
				break;
		}
	}

	protected final int processData0(byte[] data, int index) {
		int len = this.assembleFrame(data, index);
		if(len < 0){
			this.sendConnectionError(HTTP2Constants.STATUS_FRAME_SIZE_ERROR);
			return -1;
		}else
			index += len;
		if(!this.connection.isConnected())
			return -1;
		return index;
	}


	private int assembleFrame(byte[] data, int offset) {
		if(this.frameBufferSize == 0){
			if(data.length - offset < HTTP2Constants.FRAME_HEADER_SIZE)
				return -1;
			int length = ((data[offset] & 0xff) << 16) | ((data[offset + 1] & 0xff) << 8) | (data[offset + 2] & 0xff);
			if(length > this.settings.get(HTTP2Constants.SETTINGS_MAX_FRAME_SIZE))
				return -1;
			this.frameExpectedSize = length + HTTP2Constants.FRAME_HEADER_SIZE;
		}
		int remaining = Math.min(data.length - offset, this.frameExpectedSize - this.frameBufferSize);
		System.arraycopy(data, offset, this.frameBuffer, this.frameBufferSize, remaining);
		this.frameBufferSize += remaining;
		if(this.frameBufferSize == this.frameExpectedSize){
			this.processFrame();
			this.frameBufferSize = 0;
		}
		return remaining;
	}

	private void processFrame() {
		this.purgeClosedStreams();
		int type = this.frameBuffer[3];
		int flags = this.frameBuffer[4];
		int streamId = FrameUtil.readInt32BE(this.frameBuffer, 5) & 0x7fffffff;
		byte[] payload = Arrays.copyOfRange(this.frameBuffer, HTTP2Constants.FRAME_HEADER_SIZE, this.frameExpectedSize);
		if(logger.debug())
			logger.trace(this.connection.getRemoteName(), " -> local HTTP2 frame: stream=", streamId, " type=", type, " flags=", flags, " length=", payload.length);
		HTTP2Stream stream = this.streams.get(streamId);
		try{
			ControlStream controlStream = this.getControlStream();
			if(stream == null){
				if(streamId < this.highestStreamId /* closed stream */ && type != HTTP2Constants.FRAME_TYPE_PRIORITY)
					throw new HTTP2ConnectionError(HTTP2Constants.STATUS_PROTOCOL_ERROR);
				stream = this.newStreamForFrame(streamId, type, flags, payload);
				if(stream == null){
					if(type != HTTP2Constants.FRAME_TYPE_PRIORITY)
						throw new HTTP2ConnectionError(HTTP2Constants.STATUS_PROTOCOL_ERROR);
					else
						return;
				}
				this.highestStreamId = streamId;
				this.streams.put(streamId, stream);
			}
			if(!controlStream.isSettingsReceived() && type != HTTP2Constants.FRAME_TYPE_SETTINGS)
				throw new HTTP2ConnectionError(HTTP2Constants.STATUS_PROTOCOL_ERROR);
			stream.receiveFrame(type, flags, payload);
			if(HTTP2Stream.isFlowControlledFrameType(type) && payload.length > 0){
				controlStream.consumeLocalConnectionWindow(payload.length);
				if(controlStream.getLocalWindowSize() < 0x1000000)
					controlStream.sendWindowSizeUpdate(0x1000000);
			}
		}catch(Exception e){
			if(e instanceof HTTP2ConnectionError){
				HTTP2ConnectionError h2e = (HTTP2ConnectionError) e;
				if(logger.debug())
					logger.debug(this.connection.getRemoteName(), ": Error in stream ", (stream != null ? stream.getStreamId() : "(none)"), ": ",
							HTTP2Util.PRINT_STACK_TRACES ? e : e.toString());
				if(!this.connection.isWritable()){
					logger.warn("Attempted to notify peer of HTTP/2 error but connection is not writable; destroying socket [DoS mitigation]");
					this.sendConnectionError(HTTP2Constants.STATUS_ENHANCE_YOUR_CALM);
				}else if(h2e.isStreamError() && (stream instanceof MessageStream)){
					try{
						((MessageStream) stream).rst(h2e.getStatus());
					}catch(IOException e2){
						logger.debug(this.connection.getRemoteName(), ": Error while sending RST frame to stream ", stream.getStreamId(), ": ",
								HTTP2Util.PRINT_STACK_TRACES ? e2 : e2.toString());
					}
				}else
					this.sendConnectionError(h2e.getStatus());
			}else{
				logger.warn(this.connection.getRemoteName(), ": Error while processing frame: ", HTTP2Util.PRINT_STACK_TRACES ? e : e.toString());
				this.sendConnectionError(HTTP2Constants.STATUS_INTERNAL_ERROR);
			}
		}
	}

	private void sendConnectionError(int status) {
		try{
			this.getControlStream().sendGoaway(this.highestStreamId, status);
			this.connection.close();
		}catch(IOException e){
			logger.debug(this.connection.getRemoteName(), ": Error while closing connection after connection error: ", HTTP2Util.PRINT_STACK_TRACES ? e : e.toString());
		}
	}

	private void purgeClosedStreams() {
		if(this.closeWaitStreams.size() > 0){
			synchronized(this.closeWaitStreams){
				long time = System.nanoTime();
				MessageStream ms;
				long timediff;
				while((ms = this.closeWaitStreams.peekFirst()) != null && (timediff = time - ms.getCloseTime()) > this.closeWaitTimeout){
					this.closeWaitStreams.removeFirst();
					int streamId = ms.getStreamId();
					this.streams.remove(streamId);
					if(logger.debug())
						logger.trace(this.connection.getRemoteName(), ": Stream ", streamId, " deleted after ", timediff / 1000000L, "ms");
				}
			}
		}
	}

	/**
	 * Returns the connection control stream (stream with ID 0).
	 * 
	 * @return The control stream
	 */
	protected ControlStream getControlStream() {
		return (ControlStream) this.streams.get(0);
	}

	/**
	 * Checks whether the remote endpoint may cause a new stream to be created according to the local <i>MAX_CONCURRENT_STREAMS</i> setting.
	 * 
	 * @throws HTTP2ConnectionError If no new stream may be created
	 */
	protected void checkRemoteCreateStream() throws HTTP2ConnectionError {
		if(this.streams.size() + 1 > this.settings.get(HTTP2Constants.SETTINGS_MAX_CONCURRENT_STREAMS))
			throw new HTTP2ConnectionError(HTTP2Constants.STATUS_ENHANCE_YOUR_CALM);
	}

	/**
	 * Registers a locally created stream.
	 * 
	 * @param stream The stream
	 */
	protected void registerStream(HTTP2Stream stream) {
		this.streams.put(stream.getStreamId(), stream);
	}

	/**
	 * Calls {@link MessageStream#windowUpdate()} for each active {@code MessageStream}.
	 * <p>
	 * This method should be called by the {@code ControlStream} {@link ControlStream#setOnWindowUpdate(Runnable) onWindowUpdate} callback, and if the underlying connection is
	 * {@linkplain WritableSocket#isWritable() writable} again, after being unwritable previously.
	 */
	protected void handleConnectionWindowUpdate() {
		for(HTTP2Stream s : this.streams.values()){
			if(s instanceof MessageStream)
				((MessageStream) s).windowUpdate();
		}
	}


	/**
	 * Returns the underlying connection as a {@link WritableSocket}.
	 * 
	 * @return The connection
	 */
	public WritableSocket getConnection() {
		return this.connection;
	}
}
