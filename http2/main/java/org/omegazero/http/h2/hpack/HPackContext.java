/*
 * Copyright (C) 2022 omegazero.org, user94729
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.omegazero.http.h2.hpack;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.omegazero.common.util.ArrayUtil;
import org.omegazero.http.common.HTTPHeaderContainer;

/**
 * A {@code HPackContext} contains one encoder and decoder for use on a HTTP/2 connection.
 * <p>
 * Creating a {@code HPackContext} also requires a {@link Session}. A HPack session is useful when forwarding HTTP/2 messages because it shares the list of headers that are not
 * allowed to be compressed. By default, creating a {@code HPackContext} also creates a new session.
 * <p>
 * This class is not thread-safe. The {@code Session} class is thread-safe.
 * 
 * @since 1.2.1
 * @see <i>RFC 7541</i>
 */
public class HPackContext implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final int staticTableBase;
	private static final TableEntry[] staticTable;
	private static final Map<String, Integer> staticTableLookup;


	private final Session session;
	private final boolean useHuffmanEncoding;
	private int encoderDynamicTableMaxSizeSettings = -1; // received in SETTINGS
	private int decoderDynamicTableMaxSizeSettings; // sent in SETTINGS

	private int encoderDynamicTableMaxSizeCurrent = -1; // same as initialEncoderDynamicTableMaxSize, may be changed in sent HPACK block
	private int decoderDynamicTableMaxSizeCurrent; // same as initialDecoderDynamicTableMaxSize, may be changed in received HPACK block
	private final List<TableEntry> encoderDynamicTable = new ArrayList<>();
	private final List<TableEntry> decoderDynamicTable = new ArrayList<>();

	private boolean encoderDynamicTableMaxSizeUpdated = true;

	/**
	 * Creates a new {@link HPackContext} with a new {@code Session}.
	 * 
	 * @param useHuffmanEncoding Whether to use Huffman Encoding
	 * @param decoderDynamicTableMaxSize The maximum table size of the decoder, as sent in a <i>SETTINGS</i> frame
	 */
	public HPackContext(boolean useHuffmanEncoding, int decoderDynamicTableMaxSize) {
		this(new Session(), useHuffmanEncoding, decoderDynamicTableMaxSize);
	}

	/**
	 * Creates a new {@link HPackContext}.
	 * 
	 * @param session The {@link Session}
	 * @param useHuffmanEncoding Whether to use Huffman Encoding
	 * @param decoderDynamicTableMaxSize The maximum table size of the decoder, as sent in a <i>SETTINGS</i> frame
	 */
	public HPackContext(Session session, boolean useHuffmanEncoding, int decoderDynamicTableMaxSize) {
		if(session == null)
			throw new NullPointerException("session is null");
		if(decoderDynamicTableMaxSize < 0)
			throw new IllegalArgumentException("decoderDynamicTableMaxSize is negative");
		this.session = session;
		this.useHuffmanEncoding = useHuffmanEncoding;
		this.decoderDynamicTableMaxSizeSettings = decoderDynamicTableMaxSize;
		this.decoderDynamicTableMaxSizeCurrent = decoderDynamicTableMaxSize;
	}


	/**
	 * Decodes the given full header block data according to <i>RFC 7541</i>.
	 * 
	 * @param data The full header data
	 * @return A {@link HTTPHeaderContainer} containing the decoded header data, or <code>null</code> if a decoding error occurred
	 */
	public HTTPHeaderContainer decodeHeaderBlock(byte[] data) {
		HTTPHeaderContainer headers = new HTTPHeaderContainer();
		// cookie headers are encoded specially in HTTP/2: instead of "cookie: c1=v1; c2=v2", each cookie is sent in its own header
		// individual cookie headers are converted back to the first (old) format with a single header (as recommended by rfc7540, 8.1.2.5)
		StringBuilder cookieValues = new StringBuilder();

		IntRef tmp = new IntRef();
		for(int i = 0; i < data.length;){
			if((data[i] & 0x80) != 0){ // indexed full header (6.1)
				int index = readInteger32(data, i, 7, tmp);
				if(index < 0)
					return null;
				TableEntry e = this.getDecoderTableEntry(index);
				if(e == null || e.value == null)
					return null;
				i += tmp.getAndReset() + 1;
				if(!e.name.equals("cookie"))
					headers.addHeader(e.name, e.value);
				else
					cookieValues.append(';').append(' ').append(e.value);
			}else if((data[i] & 0xe0) == 0x20){ // table size update (6.3)
				int size = readInteger32(data, i, 5, tmp);
				if(size < 0)
					return null;
				i += tmp.getAndReset() + 1;
				if(size < 0 || size > this.decoderDynamicTableMaxSizeSettings)
					return null;
				this.decoderDynamicTableMaxSizeCurrent = size;
				this.removeDecoderDynamicTableEntries(0);
			}else{ // not indexed
				boolean addToIndex = (data[i] & 0x40) != 0; // 6.2.1
				boolean neverIndex = !addToIndex && (data[i] & 0x10) != 0; // 6.2.3
				int index = readInteger32(data, i, addToIndex ? 6 : 4, tmp);
				if(index < 0)
					return null;
				i += tmp.getAndReset() + 1;

				String name;
				int namelen = 0;
				if(index > 0){
					TableEntry e = this.getDecoderTableEntry(index);
					if(e == null)
						return null;
					namelen = e.namelen;
					name = e.name;
				}else{
					byte[] namedata = readString(data, i, tmp);
					if(namedata == null)
						return null;
					namelen = namedata.length;
					name = new String(namedata, StandardCharsets.UTF_8);
					i += tmp.getAndReset() + 1;
				}

				byte[] valuedata = readString(data, i, tmp);
				if(valuedata == null)
					return null;
				String value = new String(valuedata, StandardCharsets.UTF_8);
				i += tmp.getAndReset() + 1;
				if(!name.equals("cookie"))
					headers.addHeader(name, value);
				else
					cookieValues.append(';').append(' ').append(value);
				if(addToIndex){
					TableEntry ne = new TableEntry(namelen, valuedata.length, name, value);
					this.removeDecoderDynamicTableEntries(ne.getSize());
					this.decoderDynamicTable.add(0, ne);
				}else if(neverIndex)
					this.session.addNeverIndex(name);
			}
		}
		if(cookieValues.length() > 2)
			headers.addHeader("cookie", cookieValues.substring(2));
		return headers;
	}


	/**
	 * Encodes the given header name-value pairs according to <i>RFC 7541</i> with a single new {@link EncoderContext}.
	 * 
	 * @param headers The header pairs
	 * @return The encoded data
	 * @see #encodeHeader(EncoderContext, String, String)
	 */
	public byte[] encodeHeaders(Map<String, String> headers) {
		EncoderContext context = new EncoderContext(headers.size());
		for(Map.Entry<String, String> header : headers.entrySet()){
			this.encodeHeader(context, header.getKey(), header.getValue());
		}
		return context.getEncodedData();
	}

	/**
	 * Encodes the given header in the {@code EncoderContext} according to <i>RFC 7541</i>.
	 * 
	 * @param context The {@code EncoderContext} to encode this header in
	 * @param name The name of the header
	 * @param value The value of the header
	 * @throws IllegalStateException If {@link #setEncoderDynamicTableMaxSizeSettings(int)} was never called
	 * @see #encodeHeaders(Map)
	 */
	public void encodeHeader(EncoderContext context, String name, String value) {
		this.requireEncoderDynamicTableMaxSizeInitialized();
		if(!this.encoderDynamicTableMaxSizeUpdated){
			int dtMaxSize = this.getEncoderDynamicTableMaxSizeCurrent();
			if(dtMaxSize < this.encoderDynamicTableMaxSizeSettings){
				context.bufReserveAdditional(5);
				context.buf[context.buflen] = (byte) 0x20;
				context.buflen += writeInteger(context.buf, context.buflen, 5, dtMaxSize) + 1;
			}
			this.encoderDynamicTableMaxSizeUpdated = true;
		}

		IntRef tableIndex = new IntRef();
		boolean doIndex = !this.session.isNeverIndex(name);
		String indexName = name;
		String indexValue = value;
		int namelen = -1;
		int valuelen = -1;
		TableEntry e = this.findEncoderTableEntry(name, value, tableIndex);
		if(e != null){
			if(e.value != null && value.equals(e.value)){
				doIndex = false;
				context.bufReserveAdditional(5);
				context.buf[context.buflen] = (byte) 0x80;
				context.buflen += writeInteger(context.buf, context.buflen, 7, tableIndex.getAndReset()) + 1;
			}else{
				indexName = e.name;
				namelen = e.namelen;

				byte[] valuedata = value.getBytes(StandardCharsets.UTF_8);
				valuelen = valuedata.length;
				if(this.useHuffmanEncoding)
					valuedata = HPackHuffmanCoding.encode(valuedata);

				context.bufReserveAdditional(11 + valuedata.length);
				context.buf[context.buflen] = (byte) (doIndex ? 0x40 : 0x10);
				context.buflen += writeInteger(context.buf, context.buflen, doIndex ? 6 : 4, tableIndex.getAndReset()) + 1;
				context.buflen += writeString0(context.buf, context.buflen, valuedata, this.useHuffmanEncoding) + 1;
			}
		}else{
			byte[] namedata = name.getBytes(StandardCharsets.UTF_8);
			namelen = namedata.length;
			if(this.useHuffmanEncoding)
				namedata = HPackHuffmanCoding.encode(namedata);

			byte[] valuedata = value.getBytes(StandardCharsets.UTF_8);
			valuelen = valuedata.length;
			if(this.useHuffmanEncoding)
				valuedata = HPackHuffmanCoding.encode(valuedata);

			context.bufReserveAdditional(13 + namedata.length + valuedata.length);
			context.buf[context.buflen] = (byte) (doIndex ? 0x40 : 0x10);
			context.buflen++;
			context.buflen += writeString0(context.buf, context.buflen, namedata, this.useHuffmanEncoding) + 1;
			context.buflen += writeString0(context.buf, context.buflen, valuedata, this.useHuffmanEncoding) + 1;
		}
		if(doIndex){
			TableEntry ne = new TableEntry(namelen, valuelen, indexName, indexValue);
			this.removeEncoderDynamicTableEntries(ne.getSize());
			this.encoderDynamicTable.add(0, ne);
		}
	}


	private TableEntry getDecoderTableEntry(int index) {
		if(index < staticTableBase)
			return null;
		int dynamicStart = staticTableBase + staticTable.length;
		if(index < dynamicStart)
			return staticTable[index - staticTableBase];
		else if(index < dynamicStart + this.decoderDynamicTable.size())
			return this.decoderDynamicTable.get(index - dynamicStart);
		else
			return null;
	}

	private TableEntry findEncoderTableEntry(String name, String value, IntRef index) {
		Integer staticTableIndex = staticTableLookup.get(lookupKey(name, value));
		if(staticTableIndex != null){ // found exact match in static
			index.v = staticTableIndex + staticTableBase;
			return staticTable[staticTableIndex];
		}

		TableEntry found = null;
		int foundIndex = -1;
		for(int i = 0; i < this.encoderDynamicTable.size(); i++){
			TableEntry e = this.encoderDynamicTable.get(i);
			if(name.equals(e.name)){
				found = e;
				foundIndex = i;
				if(value.equals(e.value))
					break;
			}
		}

		if(found == null || !value.equals(found.value)){ // no exact match in dynamic, search key match in static
			staticTableIndex = staticTableLookup.get(lookupKey(name, null));
			if(staticTableIndex != null){ // found key match in static
				index.v = staticTableIndex + staticTableBase;
				return staticTable[staticTableIndex];
			}
		}

		if(found != null)
			index.v = staticTableBase + staticTable.length + foundIndex;
		return found;
	}

	private void removeEncoderDynamicTableEntries(int newEntrySize) {
		assert this.getEncoderDynamicTableMaxSizeCurrent() >= 0;
		removeDynamicTableEntries(this.encoderDynamicTable, this.getEncoderDynamicTableMaxSizeCurrent(), newEntrySize);
	}

	private void removeDecoderDynamicTableEntries(int newEntrySize) {
		removeDynamicTableEntries(this.decoderDynamicTable, this.getDecoderDynamicTableMaxSizeCurrent(), newEntrySize);
	}

	private void requireEncoderDynamicTableMaxSizeInitialized() {
		if(this.encoderDynamicTableMaxSizeSettings < 0)
			throw new IllegalStateException("encoderDynamicTableMaxSize is not initialized. Call setEncoderDynamicTableMaxSizeSettings() prior to calling this method");
		else
			assert this.encoderDynamicTableMaxSizeCurrent <= this.encoderDynamicTableMaxSizeSettings;
	}


	/**
	 * Returns {@code true} if Huffman Encoding was enabled in the constructor.
	 * 
	 * @return Whether Huffman Encoding is enabled
	 */
	public boolean isUseHuffmanEncoding() {
		return this.useHuffmanEncoding;
	}

	/**
	 * Returns the maximum size of the dynamic table of the encoder, as received in a <i>SETTINGS</i> frame.
	 * 
	 * @return The maximum encoder dynamic table size
	 * @throws IllegalStateException If {@link #setEncoderDynamicTableMaxSizeSettings(int)} was never called
	 * @see #setEncoderDynamicTableMaxSizeSettings(int)
	 * @see #getEncoderDynamicTableMaxSizeCurrent()
	 */
	public int getEncoderDynamicTableMaxSizeSettings() {
		this.requireEncoderDynamicTableMaxSizeInitialized();
		return this.encoderDynamicTableMaxSizeSettings;
	}

	/**
	 * Sets the maximum size of the dynamic table of the encoder, as received in a <i>SETTINGS</i> frame.
	 * <p>
	 * This method must be called before encoding headers using this {@code HPackContext}.
	 * 
	 * @param dynamicTableMaxSize The maximum encoder dynamic table size
	 * @see #getEncoderDynamicTableMaxSizeSettings()
	 * @see #setEncoderDynamicTableMaxSizeCurrent(int)
	 */
	public void setEncoderDynamicTableMaxSizeSettings(int dynamicTableMaxSize) {
		if(dynamicTableMaxSize < 0)
			throw new IllegalArgumentException("dynamicTableMaxSize is negative");
		this.encoderDynamicTableMaxSizeSettings = dynamicTableMaxSize;
		if(this.encoderDynamicTableMaxSizeCurrent > dynamicTableMaxSize)
			this.setEncoderDynamicTableMaxSizeCurrent(dynamicTableMaxSize);
	}

	/**
	 * Returns the current maximum size of the dynamic table of the encoder, as received in a <i>SETTINGS</i> frame, possibly lowered using
	 * {@link #setEncoderDynamicTableMaxSizeCurrent(int)}.
	 * 
	 * @return The maximum encoder dynamic table size
	 * @see #setEncoderDynamicTableMaxSizeCurrent(int)
	 * @see #getEncoderDynamicTableMaxSizeSettings()
	 */
	public int getEncoderDynamicTableMaxSizeCurrent() {
		return this.encoderDynamicTableMaxSizeCurrent >= 0 ? this.encoderDynamicTableMaxSizeCurrent : this.encoderDynamicTableMaxSizeSettings;
	}

	/**
	 * Sets the current maximum size of the dynamic table of the encoder.
	 * <p>
	 * If {@link #setEncoderDynamicTableMaxSizeSettings(int)} was called with the value received in a <i>SETTINGS</i> frame, this must be lower than or equal to that value. If
	 * lower, a <i>Dynamic Table Size Update</i> will be sent in the next encoded <i>HPACK</i> headers.
	 * <p>
	 * This method may be called before a call to {@link #setEncoderDynamicTableMaxSizeSettings(int)} to set a maximum value before a <i>SETTINGS</i> frame was received. If the
	 * <i>SETTINGS</i> maximum is lower than this value, the maximum set by this method will be lowered to the received value, otherwise, it will be unchanged.
	 * <p>
	 * The given <b>dynamicTableMaxSize</b> may be {@code -1}, to always use the value set by {@link #setEncoderDynamicTableMaxSizeSettings(int)} as the maximum dynamic table size.
	 * This is the default behavior.
	 * 
	 * @param dynamicTableMaxSize The new maximum encoder dynamic table size
	 * @see #getEncoderDynamicTableMaxSizeCurrent()
	 * @see #setEncoderDynamicTableMaxSizeSettings(int)
	 */
	public void setEncoderDynamicTableMaxSizeCurrent(int dynamicTableMaxSize) {
		if(dynamicTableMaxSize < -1)
			throw new IllegalArgumentException("dynamicTableMaxSize is invalid: " + dynamicTableMaxSize);
		if(this.encoderDynamicTableMaxSizeSettings >= 0 && dynamicTableMaxSize > this.encoderDynamicTableMaxSizeSettings)
			throw new IllegalArgumentException(
					"new max table size is larger than max table size in settings: " + dynamicTableMaxSize + " > " + this.encoderDynamicTableMaxSizeSettings);
		this.encoderDynamicTableMaxSizeCurrent = dynamicTableMaxSize;
		this.encoderDynamicTableMaxSizeUpdated = false;
		this.removeEncoderDynamicTableEntries(0);
	}

	/**
	 * Returns the current size of the dynamic table of the encoder.
	 * 
	 * @return The current encoder dynamic table size
	 */
	public int getEncoderDynamicTableSize() {
		return getDynamicTableSize(this.encoderDynamicTable);
	}

	/**
	 * Returns the maximum size of the dynamic table of the decoder, as sent in a <i>SETTINGS</i> frame.
	 * 
	 * @return The maximum decoder dynamic table size
	 * @see #setDecoderDynamicTableMaxSizeSettings(int)
	 * @see #getDecoderDynamicTableMaxSizeCurrent()
	 */
	public int getDecoderDynamicTableMaxSizeSettings() {
		return this.decoderDynamicTableMaxSizeSettings;
	}

	/**
	 * Sets the maximum size of the dynamic table of the decoder, as sent in a <i>SETTINGS</i> frame.
	 * 
	 * @param dynamicTableMaxSize The maximum decoder dynamic table size
	 * @see #getDecoderDynamicTableMaxSizeSettings()
	 */
	public void setDecoderDynamicTableMaxSizeSettings(int dynamicTableMaxSize) {
		if(dynamicTableMaxSize < 0)
			throw new IllegalArgumentException("dynamicTableMaxSize is negative");
		this.decoderDynamicTableMaxSizeSettings = dynamicTableMaxSize;
		this.decoderDynamicTableMaxSizeCurrent = dynamicTableMaxSize;
	}

	/**
	 * Returns the current maximum size of the dynamic table of the decoder. This value is lower or equal to a value sent in a <i>SETTINGS</i> frame (set by
	 * {@link #setDecoderDynamicTableMaxSizeSettings(int)}), possibly modified by a received <i>Dynamic Table Size Update</i>.
	 * 
	 * @return The maximum decoder dynamic table size
	 * @see #getDecoderDynamicTableMaxSizeSettings()
	 */
	public int getDecoderDynamicTableMaxSizeCurrent() {
		return this.decoderDynamicTableMaxSizeCurrent;
	}

	/**
	 * Returns the current size of the dynamic table of the decoder.
	 * 
	 * @return The current decoder dynamic table size
	 */
	public int getDecoderDynamicTableSize() {
		return getDynamicTableSize(this.decoderDynamicTable);
	}

	/**
	 * Returns the {@code Session} configured in the constructor.
	 * 
	 * @return The {@code Session}
	 */
	public Session getSession() {
		return this.session;
	}


	private static void removeDynamicTableEntries(List<TableEntry> table, int maxSize, int newEntrySize) {
		int size = getDynamicTableSize(table);
		while(size > maxSize - newEntrySize && table.size() > 0){
			TableEntry e = table.remove(table.size() - 1);
			size -= e.getSize();
		}
	}

	private static int getDynamicTableSize(Iterable<TableEntry> c) {
		int size = 0;
		for(TableEntry e : c){
			size += e.getSize();
		}
		return size;
	}

	private static String lookupKey(TableEntry e) {
		return lookupKey(e.name, e.value);
	}

	private static String lookupKey(String name, String value) {
		return name + ": " + value;
	}


	/**
	 * Encodes an integer into a byte array according to <i>RFC 7541, section 5.1</i>.
	 * 
	 * @param dest The destination array
	 * @param offset The offset in the array where to start writing to
	 * @param n The prefix size N
	 * @param value The integer value to write
	 * @return The number of additional bytes written. A value of <code>0</code> means only the byte at index <b>offset</b> was modified
	 * @throws IndexOutOfBoundsException The full value could not be written because the array is too short; all bytes starting at <b>offset</b> may or may not have been modified
	 * in that case
	 * @see #readInteger(byte[], int, int, IntRef)
	 */
	public static int writeInteger(byte[] dest, int offset, int n, long value) {
		int pow_1 = (1 << n) - 1;
		if(value < pow_1){
			dest[offset] = (byte) ((dest[offset] & ~pow_1) | value);
			return 0;
		}else{
			dest[offset] |= pow_1;
			value -= pow_1;
			int i = 0;
			while(value >= 128){
				i++;
				if(offset + i + 1 >= dest.length)
					throw new IndexOutOfBoundsException("offset=" + offset + " ilen=" + i + " destlen=" + dest.length);
				dest[offset + i] = (byte) ((value & 0x7f) | 0x80);
				value >>= 7;
			}
			i++;
			dest[offset + i] = (byte) value;
			return i;
		}
	}

	/**
	 * Decodes an integer from a byte array according to <i>RFC 7541, section 5.1</i>.
	 * 
	 * @param data The byte array
	 * @param offset The offset where to start reading from
	 * @param n The prefix size N
	 * @param read Returns the number of additional bytes read, <b>by adding it to the current value</b>; if unchanged means only the byte at <b>offset</b> was read. Only valid if
	 * the return value of this function is positive. May be <code>null</code>
	 * @return The decoded integer, or a negative value if an error occurred
	 * @see #writeInteger(byte[], int, int, long)
	 * @see #readInteger32(byte[], int, int, IntRef)
	 */
	public static long readInteger(byte[] data, int offset, int n, IntRef read) {
		if(offset >= data.length)
			return -1;
		int pow_1 = (1 << n) - 1;
		long num = data[offset] & pow_1;
		if(num < pow_1)
			return num;
		int i = 0;
		int off = 0;
		byte temp;
		do{
			i++;
			if(offset + i >= data.length)
				return -1;
			temp = data[offset + i];
			num += (temp & 0x7f) << off;
			off += 7;
			if(off >= 64)
				return -1;
		}while((temp & 0x80) != 0);
		if(read != null)
			read.v += i;
		return num;
	}

	/**
	 * Equivalent to {@link #readInteger(byte[], int, int, IntRef)}, except that <code>-1</code> will be returned if the decoded integer is higher than a signed 32-bit value can
	 * represent (larger than <code>Integer.MAX_VALUE</code>).
	 * 
	 * @param data The byte array
	 * @param offset The offset where to start reading from
	 * @param n The prefix size N
	 * @param read Returns the number of additional bytes read
	 * @return The decoded integer, or a negative value if an error occurred or the decoded integer is larger than 31 bits
	 */
	public static int readInteger32(byte[] data, int offset, int n, IntRef read) {
		long val = readInteger(data, offset, n, read);
		if(val > Integer.MAX_VALUE)
			return -1;
		return (int) val;
	}

	/**
	 * Encodes string data into the given byte array according to <i>RFC 7541, section 5.2</i>.
	 * 
	 * @param dest The destination array
	 * @param offset The offset in the array where to start writing to
	 * @param strdata The encoded string data to write
	 * @param huffman Whether the string should be encoded using huffman coding
	 * @return The number of additional bytes written. A value of <code>0</code> means only the byte at index <b>offset</b> was modified
	 * @throws IndexOutOfBoundsException The full string could not be written because the array is too short; all bytes starting at <b>offset</b> may or may not have been modified
	 * in that case
	 */
	public static int writeString(byte[] dest, int offset, byte[] strdata, boolean huffman) {
		if(huffman)
			strdata = HPackHuffmanCoding.encode(strdata);
		return writeString0(dest, offset, strdata, huffman);
	}

	private static int writeString0(byte[] dest, int offset, byte[] strdata, boolean huffman) {
		if(huffman)
			dest[offset] = (byte) 0x80;
		else
			dest[offset] = 0;
		int i = writeInteger(dest, offset, 7, strdata.length);
		if(i < 0 || offset + i + strdata.length >= dest.length)
			throw new IndexOutOfBoundsException("offset=" + offset + " ilen=" + i + " strlen=" + strdata.length + " destlen=" + dest.length);
		System.arraycopy(strdata, 0, dest, offset + i + 1, strdata.length);
		return strdata.length + i;
	}

	/**
	 * Decodes a UTF-8 string from the given byte array according to <i>RFC 7541, section 5.2</i>.
	 * 
	 * @param data The byte array
	 * @param offset The offset where to start reading from
	 * @param read Returns the number of additional bytes read, <b>by adding it to the current value</b>; if unchanged means only the byte at <b>offset</b> was read and the string
	 * is empty. Only valid if the return value of this function is not <code>null</code>. May be <code>null</code>
	 * @return The decoded string data, or <code>null</code> if the array is truncated or a decoding error occurred
	 */
	public static byte[] readString(byte[] data, int offset, IntRef read) {
		if(offset >= data.length)
			return null;
		IntRef lenlen = new IntRef();
		boolean huffman = (data[offset] & 0x80) != 0;
		int strlen = readInteger32(data, offset, 7, lenlen);
		if(strlen < 0 || offset + lenlen.v + strlen >= data.length)
			return null;
		byte[] strdata = new byte[strlen];
		System.arraycopy(data, offset + lenlen.v + 1, strdata, 0, strlen);
		if(huffman){
			strdata = HPackHuffmanCoding.decode(strdata);
			if(strdata == null)
				return null;
		}
		if(read != null)
			read.v += lenlen.v + strlen;
		return strdata;
	}


	/**
	 * An <code>EncoderContext</code> is used for encoding a single header block. Used in {@link HPackContext#encodeHeader(EncoderContext, String, String)}.
	 */
	public static class EncoderContext {

		byte[] buf;
		int buflen = 0;

		public EncoderContext() {
			this(4, null);
		}

		public EncoderContext(int headerCount) {
			this(headerCount, null);
		}

		public EncoderContext(int headerCount, byte[] initBuf) {
			int pl = headerCount * 8;
			if(initBuf != null){
				this.buf = Arrays.copyOf(initBuf, Math.max(pl, initBuf.length));
				this.buflen = initBuf.length;
			}else
				this.buf = new byte[pl];
		}


		/**
		 * Returns the data encoded using this {@link EncoderContext}.
		 * 
		 * @return The encoded data
		 */
		public byte[] getEncodedData() {
			return Arrays.copyOf(this.buf, this.buflen);
		}


		protected void bufReserveAdditional(int count) {
			if(this.buflen + count >= this.buf.length)
				this.buf = Arrays.copyOf(this.buf, Math.max(this.buf.length * 2, this.buflen + count));
		}
	}

	/**
	 * @see HPackContext
	 */
	public static class Session implements Serializable {

		private static final long serialVersionUID = 1L;


		private final Set<String> neverIndex = new HashSet<>();

		public synchronized boolean addNeverIndex(String headerName) {
			return this.neverIndex.add(headerName);
		}

		public synchronized boolean isNeverIndex(String headerName) {
			return this.neverIndex.contains(headerName);
		}
	}

	public static class IntRef { // welp
		public int v = 0;

		public int getAndReset() {
			int val = this.v;
			this.v = 0;
			return val;
		}
	}


	private static class TableEntry implements Serializable {

		private static final long serialVersionUID = 1L;


		public final int namelen;
		public final int len;
		public String name;
		public String value;

		public TableEntry(String name, String value) {
			this.namelen = name.getBytes(StandardCharsets.UTF_8).length;
			this.len = this.namelen + (value != null ? value.getBytes(StandardCharsets.UTF_8).length : 0);
			this.name = name;
			this.value = value;
		}

		public TableEntry(int namelen, int valuelen, String name, String value) {
			if(namelen < 0 || valuelen < 0)
				throw new IllegalArgumentException();
			this.namelen = namelen;
			this.len = this.namelen + valuelen;
			this.name = name;
			this.value = value;
		}


		public int getSize() {
			return this.len + 32;
		}


		@Override
		public String toString() {
			return "TableEntry[" + this.name + ": " + this.value + " (" + this.len + "B)]";
		}
	}


	static{
		String[] lines;
		try{
			lines = new String(ArrayUtil.readInputStreamToByteArray(HPackHuffmanCoding.class.getResourceAsStream("hpack_static_table"))).split("\n");
		}catch(IOException e){
			throw new java.io.UncheckedIOException(e);
		}

		String initLine = null;
		for(String s : lines){
			if(s.startsWith("INIT")){
				initLine = s;
				break;
			}
		}
		if(initLine == null)
			throw new RuntimeException("No INIT line");
		String[] initLineParts = initLine.split(":");
		staticTableBase = Integer.parseInt(initLineParts[1]);
		staticTable = new TableEntry[Integer.parseInt(initLineParts[2])];
		staticTableLookup = new HashMap<>();

		for(String s : lines){
			if(s.startsWith("#"))
				continue;
			String[] parts = s.split(" ", 3);
			if(parts.length < 2)
				continue;
			int index = Integer.parseInt(parts[0]);
			int arrayIndex = index - staticTableBase;
			if(arrayIndex < 0 || arrayIndex >= staticTable.length)
				throw new RuntimeException("Index out of range: " + index + " (" + arrayIndex + ")");
			TableEntry e = new TableEntry(parts[1], parts.length > 2 ? parts[2] : null);
			staticTable[arrayIndex] = e;
			staticTableLookup.put(lookupKey(e), arrayIndex);
			if(e.value != null)
				staticTableLookup.putIfAbsent(lookupKey(e.name, null), arrayIndex);
		}
	}
}
