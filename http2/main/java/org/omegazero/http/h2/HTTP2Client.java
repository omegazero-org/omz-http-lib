/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2;

import java.io.IOException;

import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.h2.hpack.HPackContext;
import org.omegazero.http.h2.hpack.HPackContext.Session;
import org.omegazero.http.h2.streams.ControlStream;
import org.omegazero.http.h2.streams.HTTP2Stream;
import org.omegazero.http.h2.streams.MessageStream;
import org.omegazero.http.h2.util.HTTP2Constants;
import org.omegazero.http.h2.util.HTTP2Settings;
import org.omegazero.http.h2.util.HTTP2Util;
import org.omegazero.http.util.WritableSocket;

/**
 * An instance of this class represents a <i>HTTP/2</i> client, a special type of {@link HTTP2Endpoint}.
 * 
 * @since 1.2.1
 */
public class HTTP2Client extends HTTP2Endpoint {


	private int nextStreamId = 1;

	/**
	 * Creates a new {@link HTTP2Client} instance.
	 * 
	 * @param connection The underlying connection
	 * @param settings The local settings
	 * @param hpackSession The <i>HPACK</i> session to use
	 * @param useHuffmanEncoding Whether to enable <i>HPACK</i> Huffman Coding
	 */
	public HTTP2Client(WritableSocket connection, HTTP2Settings settings, Session hpackSession, boolean useHuffmanEncoding) {
		super(connection, settings, hpackSession, useHuffmanEncoding);
	}

	/**
	 * Creates a new {@link HTTP2Client} instance.
	 * 
	 * @param connection The underlying connection
	 * @param settings The local settings
	 * @param hpack The {@link HPackContext} to use
	 */
	public HTTP2Client(WritableSocket connection, HTTP2Settings settings, HPackContext hpack) {
		super(connection, settings, hpack);
	}


	/**
	 * Starts the <i>HTTP/2</i> client by sending the client connection preface and <i>SETTINGS</i> frame.
	 */
	public void start(){
		super.connection.write(HTTP2Util.getClientPreface());
		ControlStream cs = new ControlStream(super.connection, super.settings);
		super.registerStream(cs);
		cs.setOnSettingsUpdate(this::onSettingsUpdate);
		cs.setOnWindowUpdate(super::handleConnectionWindowUpdate);
		cs.writeSettings(super.settings);
	}

	/**
	 * Creates a new {@link MessageStream} to send a request with. The caller will need to call
	 * {@link MessageStream#sendHTTPMessage(org.omegazero.http.common.HTTPMessage, boolean)} on the returned stream to start the message exchange.
	 * 
	 * @return The created {@code MessageStream}, or {@code null} if no {@code MessageStream} could be created
	 * @see #onMessageStreamClosed(MessageStream)
	 */
	public MessageStream createRequestStream() {
		if(this.nextStreamId < 0) // overflow
			return null;
		ControlStream cs = super.getControlStream();
		MessageStream mstream = new MessageStream(this.nextStreamId, super.connection, cs, super.hpack);
		super.registerStream(mstream);
		this.nextStreamId += 2;
		return mstream;
	}

	/**
	 * Creates a new {@link MessageStream} that expects to receive a promise response by the server. The stream ID of the {@link HTTPRequest} is the promised stream ID by the
	 * server and must be an even number.
	 * 
	 * @param promisedRequest The promise request sent by the server
	 * @return The created {@code MessageStream}
	 * @see #onMessageStreamClosed(MessageStream)
	 */
	public MessageStream handlePushPromise(HTTPRequest promisedRequest) {
		if(!promisedRequest.hasAttachment(MessageStream.ATTACHMENT_KEY_STREAM_ID))
			throw new IllegalArgumentException("promisedRequest is not a HTTP/2 request (missing stream ID attachment)");
		int pushStreamId = (int) promisedRequest.getAttachment(MessageStream.ATTACHMENT_KEY_STREAM_ID);
		if((pushStreamId & 1) != 0)
			throw new IllegalArgumentException("promisedRequest stream ID is not an even number");
		ControlStream cs = super.getControlStream();
		MessageStream mstream = new MessageStream(pushStreamId, super.connection, cs, super.hpack);
		mstream.preparePush(true);
		super.highestStreamId = pushStreamId;
		super.registerStream(mstream);
		return mstream;
	}

	/**
	 * This method must be called by the application after a message stream returned by {@link #createRequestStream()} or {@link #handlePushPromise(HTTPRequest)}
	 * closed to remove it from internal storage. Calls {@link HTTP2Endpoint#streamClosed(MessageStream)}.
	 * 
	 * @param messageStream The closed message stream
	 * @throws IllegalStateException If the stream is not closed
	 */
	public void onMessageStreamClosed(MessageStream messageStream) {
		super.streamClosed(messageStream);
	}

	/**
	 * Closes this client. Prior to closing the underlying connection, all active message streams are canceled and a <i>GOAWAY</i> frame is sent with no error.
	 * 
	 * @throws IOException If an IO error occurs
	 */
	public void close(){
		try(/* java 8 compatibility */ WritableSocket conn = super.connection){
			for(HTTP2Stream s : super.getStreams()){
				if(s instanceof MessageStream && !((MessageStream) s).isClosed())
					((MessageStream) s).rst(HTTP2Constants.STATUS_CANCEL);
			}
			if(conn.isConnected())
				super.getControlStream().sendGoaway(super.highestStreamId, HTTP2Constants.STATUS_NO_ERROR);
		}
	}


	/**
	 * This method is called when a <i>SETTINGS</i> frame is received from the server.
	 * 
	 * @param settings The new remote settings
	 */
	protected void onSettingsUpdate(HTTP2Settings settings) {
		super.hpack.setEncoderDynamicTableMaxSizeSettings(settings.get(HTTP2Constants.SETTINGS_HEADER_TABLE_SIZE));
	}


	@Override
	protected HTTP2Stream newStreamForFrame(int streamId, int type, int flags, byte[] payload){
		return null;
	}
}
