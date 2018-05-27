/*
 *  Licensed to GIScience Research Group, Heidelberg University (GIScience)
 *
 *   http://www.giscience.uni-hd.de
 *   http://www.heigit.org
 *
 *  under one or more contributor license agreements. See the NOTICE file 
 *  distributed with this work for additional information regarding copyright 
 *  ownership. The GIScience licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in compliance 
 *  with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package heigit.ors.servlet.filters;

import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import heigit.ors.io.ByteArrayOutputStreamEx;

class GZIPResponseStream extends ServletOutputStream { 
	private ByteArrayOutputStreamEx _bufferStream = null;
	private GZIPOutputStream _gzipstream = null;
	private ServletOutputStream _outputStream = null;
	private HttpServletResponse _response = null;
	private boolean _closed = false;

	public GZIPResponseStream(HttpServletResponse response) throws IOException {
		super();
		
		this._response = response;
		this._outputStream = response.getOutputStream();
		_bufferStream = new ByteArrayOutputStreamEx();
		_gzipstream = new GZIPOutputStream(_bufferStream);
	}

	public void close() throws IOException {
		if (_closed) 
			throw new IOException("This output stream has already been closed");
		
		_gzipstream.finish();

		byte[] bytes = _bufferStream.getBuffer();
		int bytesLength = _bufferStream.size();
				
		_response.setContentLength(bytesLength); 
        _response.addHeader("Content-Encoding", ContentEncodingType.GZIP);

        _outputStream.write(bytes, 0, bytesLength);
        _outputStream.close();
		_closed = true;
	}
	
	public boolean isClosed() {
		return _closed;
	}

	public void flush() throws IOException {
		if (_closed) 
			throw new IOException("Cannot flush a closed output stream");
		
		_gzipstream.flush();
	}

	public void write(int b) throws IOException {
		if (_closed) 
			throw new IOException("Cannot write to a closed output stream");
		
		_gzipstream.write((byte)b);
	}

	public void write(byte b[]) throws IOException {
		write(b, 0, b.length);
	}

	public void write(byte b[], int off, int len) throws IOException {
		if (_closed) 
			throw new IOException("Cannot write to a closed output stream");
		
		_gzipstream.write(b, off, len);
	}

	public void reset() {

	}

	@Override
	public boolean isReady() {
		return false;
	}

	@Override
	public void setWriteListener(WriteListener arg0) {
	}
}