/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h1;

import java.util.Arrays;
import java.util.function.Consumer;

import org.omegazero.common.util.ArrayUtil;
import org.omegazero.http.common.HTTPMessage;
import org.omegazero.http.common.HTTPRequest;
import org.omegazero.http.common.HTTPResponse;
import org.omegazero.http.common.InvalidHTTPMessageException;

/**
 * Used for parsing HTTP/1 message bodies, for example with the <i>chunked</i> transfer encoding.
 * <p>
 * Each {@code MessageBodyDechunker} is initialized with a single {@link HTTPMessage} of which the body is parsed. Data received for this {@code HTTPMessage} is then passed to
 * {@link #addData(byte[])}, which parses the data and, if a full chunk was received, calls the callback passed in the constructor. If the end of the body is reached, this
 * callback is called with a byte array of length 0.
 * <p>
 * If <i>chunked</i> transfer encoding is used, a buffer size must be given. This buffer is used for temporarily storing the data chunks. If a single chunk is larger than this
 * buffer, it will be passed to the callback as multiple chunks.
 * <p>
 * This class is not thread-safe.
 * 
 * @since 1.2.1
 * @apiNote Ported from the native HTTP/1 implementation of <i>omz-proxy</i> with minor changes
 */
public class MessageBodyDechunker {

	private static final byte[] EOL = new byte[] { 0xd, 0xa };

	private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];


	private final Consumer<byte[]> onData;
	private final long totalSize;
	private final byte[] chunkBuffer;
	private int chunkBufferIndex = 0;
	private long receivedData = 0;
	private boolean ended = false;
	private int lastChunkRemaining = 0;
	private int lastChunkSize = 0;
	private byte[] partialChunkHeader;

	/**
	 * Creates a new {@link MessageBodyDechunker} with a default buffer size of 16KiB.
	 * 
	 * @param msg The {@link HTTPMessage}
	 * @param onData Callback for receiving the parsed data
	 * @throws InvalidHTTPMessageException If an error occurs while parsing the message
	 * @throws UnsupportedOperationException If the <i>Transfer-Encoding</i> of the message is not supported
	 * @see MessageBodyDechunker
	 */
	public MessageBodyDechunker(HTTPMessage msg, Consumer<byte[]> onData) throws InvalidHTTPMessageException {
		this(msg, onData, 16384);
	}

	/**
	 * Creates a new {@link MessageBodyDechunker}.
	 * 
	 * @param msg The {@link HTTPMessage}
	 * @param onData Callback for receiving the parsed data
	 * @param chunkBufferSize The buffer size for storing chunk data temporarily
	 * @throws InvalidHTTPMessageException If an error occurs while parsing the message
	 * @throws UnsupportedOperationException If the <i>Transfer-Encoding</i> of the message is not supported
	 * @see MessageBodyDechunker
	 */
	public MessageBodyDechunker(HTTPMessage msg, Consumer<byte[]> onData, int chunkBufferSize) throws InvalidHTTPMessageException {
		if(chunkBufferSize <= 0)
			throw new IllegalArgumentException("chunkBufferSize must be positive");
		this.onData = onData;

		String transferEncoding = msg.getHeader("transfer-encoding");
		String contentLength = msg.getHeader("content-length");
		if(msg instanceof HTTPResponse && !((HTTPResponse) msg).hasResponseBody()){
			this.totalSize = 0;
			this.chunkBuffer = null;
		}else if("chunked".equals(transferEncoding)){
			this.totalSize = -1;
			this.chunkBuffer = new byte[chunkBufferSize];
		}else if(transferEncoding != null){
			throw new UnsupportedOperationException("Unsupported transfer encoding: " + transferEncoding);
		}else if(contentLength != null){
			long ts;
			try{
				ts = Long.parseLong(contentLength);
			}catch(NumberFormatException e){
				throw new InvalidHTTPMessageException("Invalid Content-Length header value");
			}
			if(ts < 0)
				throw new InvalidHTTPMessageException("Content-Length is negative");
			this.totalSize = ts;
			this.chunkBuffer = null;
		}else if(msg instanceof HTTPRequest){
			this.totalSize = 0;
			this.chunkBuffer = null;
		}else{
			this.totalSize = -1;
			this.chunkBuffer = null;
		}
	}


	/**
	 * Parses the given data.
	 * 
	 * @param data The data
	 * @throws InvalidHTTPMessageException If an error occurs while parsing the data, because it is malformed
	 */
	public void addData(byte[] data) throws InvalidHTTPMessageException {
		if(this.chunkBuffer != null){
			int index = 0;
			while(index < data.length){
				if(this.lastChunkRemaining == 0){
					boolean splitLineEnd = false;
					int chunkHeaderEnd;
					if(this.partialChunkHeader != null && this.partialChunkHeader[this.partialChunkHeader.length - 1] == '\r'){
						chunkHeaderEnd = 0;
						splitLineEnd = true;
					}else
						chunkHeaderEnd = ArrayUtil.indexOf(data, EOL, index);
					if(chunkHeaderEnd < 0){
						if(data.length - index < 10){
							this.partialChunkHeader = Arrays.copyOfRange(data, index, data.length);
							index = data.length;
							continue;
						}else
							throw new InvalidHTTPMessageException("No chunk size in chunked response");
					}
					int chunkLen;
					try{
						int lenEnd = chunkHeaderEnd;
						for(int j = index; j < lenEnd; j++){
							if(data[j] == ';'){
								lenEnd = j;
								break;
							}
						}
						String chunkLenStr = new String(data, index, lenEnd - index);
						if(this.partialChunkHeader != null)
							chunkLenStr = new String(this.partialChunkHeader, 0, this.partialChunkHeader.length - (splitLineEnd ? 1 : 0)) + chunkLenStr;
						this.partialChunkHeader = null;
						chunkLen = Integer.parseInt(chunkLenStr, 16);
						if(chunkLen < 0)
							throw new InvalidHTTPMessageException("Chunk size is negative");
					}catch(NumberFormatException e){
						throw new InvalidHTTPMessageException("Invalid chunk size", e);
					}
					chunkHeaderEnd += EOL.length;
					if(splitLineEnd)
						chunkHeaderEnd--;
					int datasize = data.length - chunkHeaderEnd;
					if(datasize >= chunkLen + EOL.length){
						if(chunkLen > 0){
							byte[] chunkdata = Arrays.copyOfRange(data, chunkHeaderEnd, chunkHeaderEnd + chunkLen);
							this.newData(chunkdata);
						}else
							this.end();
						index = chunkHeaderEnd + chunkLen + EOL.length;
					}else{
						int write = Math.min(datasize, chunkLen);
						this.writeToChunkBuffer(data, chunkHeaderEnd, write);
						this.lastChunkSize = chunkLen;
						this.lastChunkRemaining = chunkLen + EOL.length - datasize;
						index = data.length;
					}
				}else{
					if(index > 0)
						throw new InvalidHTTPMessageException("End of incomplete chunk can only be at start of packet");
					if(this.lastChunkRemaining <= data.length){
						int write = this.lastChunkRemaining - EOL.length;
						if(write > 0)
							this.writeToChunkBuffer(data, 0, write);
						if(this.chunkBufferIndex > 0)
							this.newData(Arrays.copyOf(this.chunkBuffer, this.chunkBufferIndex));
						else if(this.lastChunkSize == 0)
							this.end();
						index += this.lastChunkRemaining;
						this.chunkBufferIndex = 0;
						this.lastChunkRemaining = 0;
					}else{
						this.writeToChunkBuffer(data, 0, data.length);
						this.lastChunkRemaining -= data.length;
						index = data.length;
					}
				}
			}
		}else{
			if(this.totalSize >= 0 && data.length > this.totalSize - this.receivedData)
				throw new InvalidHTTPMessageException("Received more data than expected");
			this.receivedData += data.length;
			if(data.length > 0)
				this.newData(data);
			if(this.totalSize >= 0 && this.receivedData >= this.totalSize)
				this.end();
		}
	}

	private void writeToChunkBuffer(byte[] src, int srcIndex, int len) throws InvalidHTTPMessageException {
		int written = 0;
		while(written < len){
			int write = Math.min(len - written, this.chunkBuffer.length - this.chunkBufferIndex);
			System.arraycopy(src, srcIndex, this.chunkBuffer, this.chunkBufferIndex, write);
			this.chunkBufferIndex += write;
			if(this.chunkBufferIndex >= this.chunkBuffer.length){
				this.chunkBufferIndex = 0;
				this.newData(Arrays.copyOf(this.chunkBuffer, this.chunkBuffer.length));
			}
			written += write;
			srcIndex += write;
		}
	}

	private void newData(byte[] data) throws InvalidHTTPMessageException {
		if(this.ended)
			throw new InvalidHTTPMessageException("Data after end");
		if(data.length == 0)
			this.ended = true;
		this.onData.accept(data);
	}


	/**
	 * Forces an EOF in the current input stream of this {@link MessageBodyDechunker}. This may be used if an underlying connection closed.
	 */
	public void end() {
		if(!this.ended){
			try{
				this.newData(EMPTY_BYTE_ARRAY);
			}catch(InvalidHTTPMessageException e){ // this should not happen
				throw new AssertionError(e);
			}
		}
	}

	/**
	 * Returns <code>true</code> if all expected data was received. This is always true if the message body does not have a predetermined size.
	 * 
	 * @return <code>true</code> if all expected data was received
	 */
	public boolean hasReceivedAllData() {
		return this.totalSize < 0 || this.receivedData >= this.totalSize;
	}

	/**
	 * Returns <code>true</code> if the input stream of this {@link MessageBodyDechunker} has ended, either due to a call to {@link #end()} or because
	 * {@linkplain #hasReceivedAllData() all data was received}.
	 * 
	 * @return <code>true</code> if the stream has ended
	 */
	public boolean hasEnded() {
		return this.ended;
	}
}
