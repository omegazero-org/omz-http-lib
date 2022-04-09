/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2.streams;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;

import org.omegazero.common.logging.Logger;
import org.omegazero.common.util.ArrayUtil;
import org.omegazero.http.common.HTTPHeaderContainer;
import org.omegazero.http.common.HTTPMessage;
import org.omegazero.http.common.HTTPMessageData;
import org.omegazero.http.common.HTTPMessageTrailers;
import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.common.HTTPResponse;
import org.omegazero.http.gen.HTTPRequestSupplier;
import org.omegazero.http.gen.HTTPResponseSupplier;
import org.omegazero.http.h2.HTTP2ConnectionError;
import org.omegazero.http.h2.hpack.HPackContext;
import org.omegazero.http.h2.util.FrameUtil;
import org.omegazero.http.h2.util.HTTP2Settings;
import org.omegazero.http.h2.util.HTTP2Util;
import org.omegazero.http.h2.util.StreamCallback;
import org.omegazero.http.util.HTTPValidator;
import org.omegazero.http.util.WritableSocket;

import static org.omegazero.http.h2.util.HTTP2Constants.*;

/**
 * Represents any {@link HTTP2Stream} where HTTP messages are exchanged (streams with IDs higher than 0).
 * 
 * @since 1.2.1
 */
public class MessageStream extends HTTP2Stream {

	private static final Logger logger = Logger.create();

	public static final String HTTP2_VERSION_NAME = "HTTP/2";

	public static final String ATTACHMENT_KEY_STREAM_ID = "streamId";

	public static final int STATE_IDLE = 0;
	public static final int STATE_OPEN = 1;
	public static final int STATE_RESERVED_LOCAL = 2;
	public static final int STATE_RESERVED = 3;
	public static final int STATE_HALF_CLOSED_LOCAL = 4;
	public static final int STATE_HALF_CLOSED = 5;
	public static final int STATE_CLOSED = 6;


	private final ControlStream controlStream;
	private final HTTP2Settings remoteSettings;
	private final HTTP2Settings localSettings;
	private final HPackContext hpack;

	private HTTPRequestSupplier<?> requestSupplier = HTTPRequest::new;
	private HTTPResponseSupplier<?> responseSupplier = HTTPResponse::new;

	private int state = STATE_IDLE;
	private long closeTime;
	private boolean closeOutgoing;

	private boolean headersReceiving = false;
	private boolean headersEndStream = false;
	private ByteArrayOutputStream headersBuf = new ByteArrayOutputStream();
	private int promisedStreamId = -1;

	private HTTPMessage receivedMessage;
	private StreamCallback<HTTPRequest> onPushPromise;
	private StreamCallback<HTTPMessageData> onMessage;
	private StreamCallback<HTTPMessageData> onData;
	private StreamCallback<HTTPMessageTrailers> onTrailers;
	private Runnable onDataFlushed;
	private Consumer<Integer> onClosed;

	private boolean receiveData = true;
	private final java.util.Deque<QueuedDataFrame> dataBacklog = new java.util.LinkedList<>();

	/**
	 * Creates a {@link MessageStream} instance.
	 * <p>
	 * The local and remote settings are retrieved from the given control stream.
	 * 
	 * @param streamId The stream ID
	 * @param connection The underlying connection
	 * @param controlStream The control stream of the connection
	 * @param hpack The {@link HPackContext} used for the connection
	 */
	public MessageStream(int streamId, WritableSocket connection, ControlStream controlStream, HPackContext hpack) {
		this(streamId, connection, controlStream, controlStream.getLocalSettings(), controlStream.getRemoteSettings(), hpack);
	}

	/**
	 * Creates a {@link MessageStream} instance.
	 * 
	 * @param streamId The stream ID
	 * @param connection The underlying connection
	 * @param controlStream The control stream of the connection
	 * @param localSettings The local {@link HTTP2Settings}
	 * @param remoteSettings The remote {@link HTTP2Settings}
	 * @param hpack The {@link HPackContext} used for the connection
	 */
	public MessageStream(int streamId, WritableSocket connection, ControlStream controlStream, HTTP2Settings localSettings, HTTP2Settings remoteSettings, HPackContext hpack) {
		super(streamId, connection);

		super.receiverWindowSize = remoteSettings.get(SETTINGS_INITIAL_WINDOW_SIZE);
		super.localWindowSize = localSettings.get(SETTINGS_INITIAL_WINDOW_SIZE);

		this.controlStream = controlStream;
		this.remoteSettings = remoteSettings;
		this.localSettings = localSettings;
		this.hpack = hpack;
	}


	/**
	 * Sends the given {@link HTTPRequest} on this stream by encoding the data with the configured {@link HPackContext} and sending it in one <i>PUSH_PROMISE</i> and zero or more
	 * <i>CONTINUATION</i> frames.
	 * 
	 * @param promisedStreamId The promised stream ID where the promise response is going to be sent on
	 * @param request The request to send
	 * @throws IOException If an IO error occurs
	 * @throws IllegalStateException If this stream is not in {@code STATE_HALF_CLOSED}
	 * @see #preparePush(boolean)
	 */
	public synchronized void sendPushPromise(int promisedStreamId, HTTPRequest request) throws IOException {
		if(this.state != STATE_HALF_CLOSED)
			throw new IllegalStateException("Stream is not expecting a push promise");

		this.writeMessage(FRAME_TYPE_PUSH_PROMISE, FrameUtil.int32BE(promisedStreamId), request, false);
	}

	/**
	 * Sends the given {@link HTTPMessage} on this stream by encoding the data with the configured {@link HPackContext} and sending it in one <i>HEADERS</i> and zero or more
	 * <i>CONTINUATION</i> frames.
	 * 
	 * @param message The message to send
	 * @param endStream Whether the message should end the stream and no payload data will be sent
	 * @throws IOException If an IO error occurs
	 * @throws IllegalStateException If this stream is not in {@code STATE_IDLE} (request) or {@code STATE_HALF_CLOSED} (response)
	 */
	public synchronized void sendHTTPMessage(HTTPMessage message, boolean endStream) throws IOException {
		if(this.state == STATE_IDLE)
			this.state = STATE_OPEN;
		else if(this.state != STATE_HALF_CLOSED)
			throw new IllegalStateException("Stream is not expecting a HTTP message");

		this.writeMessage(FRAME_TYPE_HEADERS, null, message, endStream);
	}

	/**
	 * Sends the given <b>trailers</b> on this stream by encoding the data with the configured {@link HPackContext} and sending it in one <i>HEADERS</i> and zero or more
	 * <i>CONTINUATION</i> frames. This will end the stream.
	 * 
	 * @param trailers The trailers to send
	 * @throws IOException If an IO error occurs
	 * @throws IllegalStateException If the stream is not in {@code STATE_OPEN} (request) or {@code STATE_HALF_CLOSED} (response)
	 */
	public synchronized void sendTrailers(HTTPMessageTrailers trailers) throws IOException {
		if(this.state != STATE_OPEN && this.state != STATE_HALF_CLOSED)
			throw new IllegalStateException("Stream is not expecting trailers");

		HPackContext.EncoderContext context = new HPackContext.EncoderContext(trailers.headerNameCount());
		for(Map.Entry<String, String> header : trailers.headers()){
			this.hpack.encodeHeader(context, header.getKey().toLowerCase(), header.getValue());
		}
		this.writeHeaders(FRAME_TYPE_HEADERS, context, true);
	}

	private void writeMessage(int type, byte[] prependData, HTTPMessage message, boolean endStream) throws IOException {
		HPackContext.EncoderContext context = new HPackContext.EncoderContext(message.headerNameCount(), prependData);
		if(message instanceof HTTPRequest){
			HTTPRequest request = (HTTPRequest) message;
			this.hpack.encodeHeader(context, ":method", request.getMethod());
			this.hpack.encodeHeader(context, ":scheme", request.getScheme());
			this.hpack.encodeHeader(context, ":authority", request.getAuthority());
			this.hpack.encodeHeader(context, ":path", request.getPath());
		}else if(message instanceof HTTPResponse){
			this.hpack.encodeHeader(context, ":status", Integer.toString(((HTTPResponse) message).getStatus()));
		}else
			throw new UnsupportedOperationException("Unsupported HTTPMessage type: " + message.getClass());
		for(Map.Entry<String, String> header : message.headers()){
			this.hpack.encodeHeader(context, header.getKey().toLowerCase(), header.getValue());
		}

		this.writeHeaders(type, context, endStream);
	}

	private void writeHeaders(int type, HPackContext.EncoderContext context, boolean endStream) throws IOException {
		int maxFrameSize = this.remoteSettings.get(SETTINGS_MAX_FRAME_SIZE);
		int index = 0;
		byte[] data = context.getEncodedData();
		if(data.length <= maxFrameSize){
			int flags = FRAME_FLAG_ANY_END_HEADERS;
			if(endStream)
				flags |= FRAME_FLAG_ANY_END_STREAM;
			this.writeFrame(type, flags, data);
		}else{
			boolean start = true;
			do{
				int nextSize = Math.min(data.length - index, maxFrameSize);
				byte[] frameData = Arrays.copyOfRange(data, index, index + nextSize);
				index += nextSize;
				if(start){
					int flags = 0;
					if(endStream)
						flags |= FRAME_FLAG_ANY_END_STREAM;
					this.writeFrame(type, flags, frameData);
					start = false;
				}else
					this.writeFrame(FRAME_TYPE_CONTINUATION, index == data.length ? FRAME_FLAG_ANY_END_HEADERS : 0, frameData);
			}while(index < data.length);
		}

		if(endStream)
			this.sentES();
	}

	/**
	 * Sends the given <b>data</b> on this stream in one or more <i>DATA</i> frames. If the receiver window size if not large enough to receive all data, it will be buffered and
	 * {@link #hasDataBacklog()} will start returning <code>true</code>, until a <i>WINDOW_UPDATE</i> frame is received.
	 * 
	 * @param data The data to send
	 * @param endStream Whether this is the last data packet sent on this stream
	 * @return {@code true} If all data could be written because the receiver window size is large enough
	 * @throws IOException If an IO error occurs
	 * @throws IllegalStateException If the stream is not in {@code STATE_OPEN} (request) or {@code STATE_HALF_CLOSED} (response)
	 */
	public synchronized boolean sendData(byte[] data, boolean endStream) throws IOException {
		if(this.state != STATE_OPEN && this.state != STATE_HALF_CLOSED)
			throw new IllegalStateException("Stream is not expecting data");

		boolean flushed = false;
		int maxFrameSize = this.remoteSettings.get(SETTINGS_MAX_FRAME_SIZE);
		int index = 0;
		if(data.length <= maxFrameSize){
			flushed = this.writeDataFrame(endStream, data);
		}else{
			do{
				int nextSize = Math.min(data.length - index, maxFrameSize);
				synchronized(this.windowSizeLock){
					if(super.receiverWindowSize > 0 && super.receiverWindowSize < nextSize)
						nextSize = super.receiverWindowSize;
				}
				byte[] frameData = Arrays.copyOfRange(data, index, index + nextSize);
				index += nextSize;
				flushed = this.writeDataFrame(endStream && index == data.length, frameData);
			}while(index < data.length);
		}

		return flushed;
	}

	private boolean writeDataFrame(boolean eos, byte[] data) throws IOException {
		assert Thread.holdsLock(this);
		synchronized(this.windowSizeLock){
			int recWindow = this.getReceiverFlowControlWindowSize();
			if(super.connection.isWritable() && this.dataBacklog.size() == 0 && recWindow > 0){
				if(recWindow < data.length){
					this.writeFrame(FRAME_TYPE_DATA, 0, data, 0, recWindow);
					this.dataBacklog.add(new QueuedDataFrame(eos, data, recWindow));
					return false;
				}else{
					this.writeFrame(FRAME_TYPE_DATA, eos ? FRAME_FLAG_ANY_END_STREAM : 0, data);
					if(eos)
						this.sentES();
					return true;
				}
			}else{
				this.dataBacklog.add(new QueuedDataFrame(eos, data));
				return false;
			}
		}
	}

	/**
	 * Checks whether the remote endpoint can receive the given amount of flow-controlled data.
	 * 
	 * @param size The size of a flow-controlled frame payload
	 * @return {@code true} if the receiver can receive the amount of data given
	 */
	private boolean canAcceptFlowControlledData(int size) {
		return this.getReceiverFlowControlWindowSize() >= size;
	}

	private int getReceiverFlowControlWindowSize() {
		return Math.min(this.controlStream.getReceiverWindowSize(), this.receiverWindowSize);
	}


	@Override
	public synchronized void receiveFrame(int type, int flags, byte[] data) throws IOException {
		if(type != FRAME_TYPE_PRIORITY && this.isClosed() && !this.isCloseOutgoing())
			throw new HTTP2ConnectionError(STATUS_STREAM_CLOSED, true);
		if(HTTP2Stream.isFlowControlledFrameType(type)){
			synchronized(this.windowSizeLock){
				if(data.length > super.localWindowSize)
					throw new HTTP2ConnectionError(STATUS_FLOW_CONTROL_ERROR, true);
				super.localWindowSize -= data.length;
			}
		}
		if(this.headersReceiving && type != FRAME_TYPE_CONTINUATION)
			throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "Expected CONTINUATION");
		if(type == FRAME_TYPE_PRIORITY){
			if(data.length != 5)
				throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR, true);
			// currently unsupported
		}else if(type == FRAME_TYPE_HEADERS){
			if(this.state == STATE_IDLE) // incoming request (c->s)
				this.state = STATE_OPEN;
			else if(this.state == STATE_RESERVED) // incoming response after push promise (s->c)
				this.state = STATE_HALF_CLOSED_LOCAL;
			else if(this.isClosed())
				throw new HTTP2ConnectionError(STATUS_STREAM_CLOSED, this.isCloseOutgoing());
			else if(this.state != STATE_HALF_CLOSED_LOCAL && this.state != STATE_OPEN) // incoming response (s->c) or incoming request trailers (c->s)
				throw new HTTP2ConnectionError(STATUS_STREAM_CLOSED, true);
			if(data.length < 1)
				throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
			int index = 0;
			int padding = 0;
			if((flags & FRAME_FLAG_ANY_PADDED) != 0)
				padding = data[index++];
			if((flags & FRAME_FLAG_HEADERS_PRIORITY) != 0){
				if(data.length < 6)
					throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
				index += 5;
			}
			if(padding > data.length - index)
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "padding is too high");
			this.headersEndStream = (flags & FRAME_FLAG_ANY_END_STREAM) != 0;
			boolean eoh = (flags & FRAME_FLAG_ANY_END_HEADERS) != 0;
			byte[] fragment = Arrays.copyOfRange(data, index, data.length - padding);
			if(eoh){
				this.receiveHeaderBlock(fragment, this.headersEndStream);
			}else{
				this.headersReceiving = true;
				this.headersBuf.write(fragment);
			}
		}else if(type == FRAME_TYPE_PUSH_PROMISE){
			if(this.localSettings.get(SETTINGS_ENABLE_PUSH) == 0)
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "PUSH is not enabled");
			if(this.state == STATE_HALF_CLOSED_LOCAL)
				this.state = STATE_RESERVED;
			else if(this.isClosed() && this.closeOutgoing)
				throw new HTTP2ConnectionError(STATUS_CANCEL, true);
			else if(this.state != STATE_RESERVED)
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR);
			if(data.length < 4)
				throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
			int index = 0;
			int padding = 0;
			if((flags & FRAME_FLAG_ANY_PADDED) != 0)
				padding = data[index++];
			int promisedStreamId = FrameUtil.readInt32BE(data, index) & 0x7fffffff;
			if((promisedStreamId & 1) != 0) // the stream id is odd; servers may only open streams with even IDs
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "Invalid promisedStreamId");
			index += 4;
			if(padding > data.length - index)
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "padding is too high");
			this.headersEndStream = false;
			this.promisedStreamId = promisedStreamId;
			boolean eoh = (flags & FRAME_FLAG_ANY_END_HEADERS) != 0;
			byte[] fragment = Arrays.copyOfRange(data, index, data.length - padding);
			if(eoh){
				this.receiveHeaderBlock(fragment, this.headersEndStream);
			}else{
				this.headersReceiving = true;
				this.headersBuf.write(fragment);
			}
		}else if(type == FRAME_TYPE_CONTINUATION){
			if(!this.headersReceiving)
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "Unexpected CONTINUATION");
			if(this.headersBuf.size() + data.length > this.localSettings.get(SETTINGS_MAX_HEADER_LIST_SIZE))
				throw new HTTP2ConnectionError(STATUS_ENHANCE_YOUR_CALM, true, "Exceeded maxHeadersSize");
			this.headersBuf.write(data);
			boolean eoh = (flags & FRAME_FLAG_ANY_END_HEADERS) != 0;
			if(eoh){
				this.receiveHeaderBlock(this.headersBuf.toByteArray(), this.headersEndStream);
				this.headersBuf.reset();
				this.headersReceiving = false;
			}
		}else if(type == FRAME_TYPE_DATA){
			if(this.state != STATE_HALF_CLOSED_LOCAL && this.state != STATE_OPEN)
				throw new HTTP2ConnectionError(STATUS_STREAM_CLOSED, true);
			int index = 0;
			int padding = 0;
			if((flags & FRAME_FLAG_ANY_PADDED) != 0)
				padding = data[index++];
			if(padding > data.length - index)
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, "padding is too high");
			byte[] fdata = Arrays.copyOfRange(data, index, data.length - padding);
			boolean es = (flags & FRAME_FLAG_ANY_END_STREAM) != 0;
			this.receiveData(fdata, es);
			if(this.receiveData && data.length > 0)
				super.sendWindowSizeUpdate(data.length * 2);
		}else if(type == FRAME_TYPE_RST_STREAM){
			if(data.length != 4)
				throw new HTTP2ConnectionError(STATUS_FRAME_SIZE_ERROR);
			int status = FrameUtil.readInt32BE(data, 0);
			logger.debug(this.connection.getRemoteName(), ": Stream ", super.getStreamId(), " closed by RST_STREAM with status ", HTTP2ConnectionError.getStatusCodeName(status));
			this.close(status, false);
		}else
			super.receiveFrame(type, flags, data);
	}

	private void receiveHeaderBlock(byte[] data, boolean endStream) throws IOException {
		boolean pushPromise = this.state == STATE_RESERVED;
		boolean request = this.state == STATE_OPEN || pushPromise;
		if(endStream)
			this.recvESnc();
		HTTPHeaderContainer headers = this.hpack.decodeHeaderBlock(data);
		if(headers == null)
			throw new HTTP2ConnectionError(STATUS_COMPRESSION_ERROR);
		if(this.receivedMessage == null){
			int streamId;
			if(!pushPromise)
				streamId = super.getStreamId();
			else
				streamId = this.promisedStreamId;
			HTTPMessage msg;
			if(request){
				String method = headers.extractHeader(":method");
				String scheme = headers.extractHeader(":scheme");
				String authority = headers.extractHeader(":authority");
				String path = headers.extractHeader(":path");
				if(authority == null)
					authority = headers.extractHeader("host");
				else
					headers.deleteHeader("host");
				if(!HTTPValidator.validMethod(method) || !"https".equals(scheme) || !HTTPValidator.validAuthority(authority) || !HTTPValidator.validPath(path))
					throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, true);
				msg = this.requestSupplier.get(method, scheme, authority, path, HTTP2_VERSION_NAME, headers);
			}else{
				int status = HTTPValidator.parseStatus(headers.extractHeader(":status"));
				if(status < 0)
					throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, true);
				msg = this.responseSupplier.get(status, HTTP2_VERSION_NAME, headers);
			}
			msg.setAttachment(ATTACHMENT_KEY_STREAM_ID, streamId);
			msg.setChunkedTransfer(!msg.headerExists("content-length"));

			if(!pushPromise){
				this.receivedMessage = msg;
				if(this.onMessage == null)
					throw new IllegalStateException("onMessage is null");
				this.onMessage.accept(this.getHMDFromReceivedMessage(endStream, null));
			}else if(this.onPushPromise != null){
				this.onPushPromise.accept((HTTPRequest) msg);
			}else{ // reset promised stream because there is no handler
				logger.warn(this.connection.getRemoteName(), ": Push promises are enabled but no handler is set");
				writeFrame(super.connection, streamId, FRAME_TYPE_RST_STREAM, 0, FrameUtil.int32BE(STATUS_CANCEL), 0, 4);
			}
		}else{
			if(!endStream)
				throw new HTTP2ConnectionError(STATUS_PROTOCOL_ERROR, true);
			if(this.onTrailers != null)
				this.onTrailers.accept(new HTTPMessageTrailers(this.receivedMessage, headers));
			else if(this.onData != null)
				this.onData.accept(this.getHMDFromReceivedMessage(true, new byte[0]));
		}
		if(endStream && this.state == STATE_HALF_CLOSED_LOCAL)
			this.close(STATUS_NO_ERROR, false);
	}

	private void receiveData(byte[] data, boolean endStream) throws IOException {
		if(endStream)
			this.recvESnc();
		if(this.onData != null)
			this.onData.accept(this.getHMDFromReceivedMessage(endStream, data));
		if(endStream && this.state == STATE_HALF_CLOSED_LOCAL)
			this.close(STATUS_NO_ERROR, false);
	}

	private HTTPMessageData getHMDFromReceivedMessage(boolean endStream, byte[] data) {
		if(this.receivedMessage instanceof HTTPRequest)
			return new org.omegazero.http.common.HTTPRequestData((HTTPRequest) this.receivedMessage, endStream, data);
		else if(this.receivedMessage instanceof HTTPResponse)
			return new org.omegazero.http.common.HTTPResponseData((HTTPResponse) this.receivedMessage, endStream, data);
		else
			throw new AssertionError("HTTPMessage type: " + this.receivedMessage.getClass().getName());
	}


	@Override
	public void writeFrame(int type, int flags, byte[] data, int offset, int length) throws IOException {
		ArrayUtil.checkBounds(data, offset, length);
		if(isFlowControlledFrameType(type)){
			if(!this.canAcceptFlowControlledData(length))
				throw new IllegalStateException("Payload in flow-controlled frame is larger than receiver window size");
			synchronized(this.windowSizeLock){
				this.receiverWindowSize -= length;
			}
			this.controlStream.consumeReceiverConnectionWindow(length);
		}
		super.writeFrame(type, flags, data, offset, length);
	}

	@Override
	public synchronized void windowUpdate() {
		if(this.isClosed()){
			if(!this.dataBacklog.isEmpty()){
				synchronized(this.windowSizeLock){
					this.dataBacklog.clear();
				}
			}
			return;
		}
		boolean dataFlushed;
		synchronized(this.windowSizeLock){
			try{
				QueuedDataFrame qf;
				while(super.connection.isWritable() && (qf = this.dataBacklog.peekFirst()) != null){
					int recWindow = this.getReceiverFlowControlWindowSize();
					if(recWindow == 0){
						break;
					}else if(recWindow < qf.remaining()){
						this.writeFrame(FRAME_TYPE_DATA, 0, qf.payload, qf.index, recWindow);
						qf.index += recWindow;
						break;
					}else{
						this.dataBacklog.removeFirst();
						this.writeFrame(FRAME_TYPE_DATA, qf.endStream ? FRAME_FLAG_ANY_END_STREAM : 0, qf.payload, qf.index, qf.remaining());
						if(qf.endStream)
							this.sentES();
					}
				}
			}catch(IOException e){
				logger.debug(this.connection.getRemoteName(), ": windowUpdate: Error while sending pending data: ", HTTP2Util.PRINT_STACK_TRACES ? e : e.toString());
			}
			dataFlushed = this.dataBacklog.size() == 0;
		}
		if(dataFlushed && this.onDataFlushed != null)
			this.onDataFlushed.run();
		super.windowUpdate();
	}


	private synchronized void recvESnc() {
		if(this.state == STATE_OPEN)
			this.state = STATE_HALF_CLOSED;
		else if(this.state != STATE_HALF_CLOSED_LOCAL)
			throw new IllegalStateException();
	}

	private synchronized void sentES() {
		if(this.state == STATE_OPEN)
			this.state = STATE_HALF_CLOSED_LOCAL;
		else if(this.state == STATE_HALF_CLOSED)
			this.close(STATUS_NO_ERROR, true);
		else
			throw new IllegalStateException();
	}

	private void close(int errorCode, boolean outgoing) {
		synchronized(this){
			if(this.isClosed())
				return;
			this.state = STATE_CLOSED;
		}
		this.closeTime = System.nanoTime();
		this.closeOutgoing = outgoing;
		if(this.onClosed == null)
			throw new IllegalStateException("onClosed is null");
		this.onClosed.accept(errorCode);
	}

	/**
	 * Sends the given <b>errorCode</b> in a <i>RST_STREAM</i> frame to close the stream, if the underlying connection is still connected. This also immediately changed the state
	 * to <code>STATE_CLOSED</code> and calls <code>onClose</code>.
	 * 
	 * @param errorCode The status code to close the stream with
	 * @throws IOException If an IO error occurs
	 */
	public void rst(int errorCode) throws IOException {
		this.close(errorCode, true);
		if(super.connection.isConnected())
			this.writeFrame(FRAME_TYPE_RST_STREAM, 0, FrameUtil.int32BE(errorCode));
	}


	/**
	 * Sets the {@link HTTPRequestSupplier} for this message stream.
	 * 
	 * @param requestSupplier The {@code HTTPRequestSupplier}
	 */
	public void setRequestSupplier(HTTPRequestSupplier<?> requestSupplier) {
		this.requestSupplier = requestSupplier;
	}

	/**
	 * Sets the {@link HTTPResponseSupplier} for this message stream.
	 * 
	 * @param responseSupplier The {@code HTTPResponseSupplier}
	 */
	public void setResponseSupplier(HTTPResponseSupplier<?> responseSupplier) {
		this.responseSupplier = responseSupplier;
	}


	public void setOnPushPromise(StreamCallback<HTTPRequest> onPushPromise) {
		this.onPushPromise = onPushPromise;
	}

	public void setOnMessage(StreamCallback<HTTPMessageData> onMessage) {
		this.onMessage = onMessage;
	}

	public void setOnData(StreamCallback<HTTPMessageData> onData) {
		this.onData = onData;
	}

	public void setOnTrailers(StreamCallback<HTTPMessageTrailers> onTrailers) {
		this.onTrailers = onTrailers;
	}

	public void setOnDataFlushed(Runnable onDataFlushed) {
		this.onDataFlushed = onDataFlushed;
	}

	public void setOnClosed(Consumer<Integer> onClosed) {
		this.onClosed = onClosed;
	}


	/**
	 * Sets whether this stream should continue receiving flow-controlled data by sending WINDOW_UPDATE frames.
	 * 
	 * @param receiveData {@code true} to continue receiving flow-controlled data
	 */
	public void setReceiveData(boolean receiveData) {
		if(!this.receiveData && receiveData){ // was re-enabled
			try{
				super.sendWindowSizeUpdate(this.localSettings.get(SETTINGS_INITIAL_WINDOW_SIZE));
			}catch(IOException e){
				logger.debug(this.connection.getRemoteName(), ": setReceiveData: Error while sending window size update: ", HTTP2Util.PRINT_STACK_TRACES ? e : e.toString());
			}
		}
		this.receiveData = receiveData;
	}

	/**
	 * Returns {@code true} if this stream is buffering data because more data was passed to {@link #sendData(byte[], boolean)} than the receiver can receive.
	 * 
	 * @return {@code true} if this stream is buffering data
	 */
	public boolean hasDataBacklog() {
		synchronized(this.windowSizeLock){
			return this.dataBacklog.size() > 0;
		}
	}


	/**
	 * Prepares this stream for the receipt or transmission of a promised server response.
	 * 
	 * @param receive <code>true</code> if this stream is about to receive a pushed response, <code>false</code> otherwise
	 * @throws IllegalStateException If this stream is not in <code>STATE_IDLE</code> or the stream ID is an odd number
	 */
	public void preparePush(boolean receive) {
		if(this.state != STATE_IDLE || (this.streamId & 1) != 0)
			throw new IllegalStateException("Stream cannot be used for server push response");
		this.state = receive ? STATE_HALF_CLOSED_LOCAL : STATE_HALF_CLOSED;
	}

	/**
	 * Returns the state of this stream.
	 * 
	 * @return The stream state
	 */
	public int getState() {
		return this.state;
	}

	/**
	 * Returns {@code true} if this stream is expecting a response (the {@linkplain #getState() state} is {@code STATE_HALF_CLOSED}).
	 * 
	 * @return {@code true} if this stream is expecting a response
	 */
	public boolean isExpectingResponse() {
		return this.state == STATE_HALF_CLOSED;
	}

	/**
	 * Returns {@code true} if this stream is closed (the {@linkplain #getState() state} is {@code STATE_CLOSED}).
	 * 
	 * @return {@code true} if this stream is closed
	 */
	public boolean isClosed() {
		return this.state == STATE_CLOSED;
	}

	/**
	 * Returns the time this stream closed, as returned by {@link System#nanoTime()}.
	 * 
	 * @return The time this stream closed
	 */
	public long getCloseTime() {
		return this.closeTime;
	}

	/**
	 * Returns {@code true} if this stream was closed because a <i>RST_STREAM</i> frame or a frame with the <i>END_STREAM</i> flag was sent, not received.
	 * 
	 * @return {@code true} if this stream was closed by an outgoing frame
	 */
	public boolean isCloseOutgoing() {
		return this.closeOutgoing;
	}


	private static class QueuedDataFrame {

		public final boolean endStream;
		public final byte[] payload;

		public int index;

		public QueuedDataFrame(boolean endStream, byte[] payload) {
			this(endStream, payload, 0);
		}

		public QueuedDataFrame(boolean endStream, byte[] payload, int index) {
			this.endStream = endStream;
			this.payload = payload;
			this.index = index;
		}


		public int remaining() {
			return this.payload.length - this.index;
		}
	}
}
