/*
 * Copyright 2015-2019 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jkoolcloud.tnt4j.stream.jmx.utils;

import java.io.ByteArrayOutputStream;

/**
 * Customized byte array output stream with limited bytes buffer size.
 *
 * @version $Revision: 1 $
 */
class RollingOutputStream extends ByteArrayOutputStream {
	static final int OUT_BUF_SIZE = 65536;

	public RollingOutputStream() {
		this(OUT_BUF_SIZE);
	}

	public RollingOutputStream(int size) {
		super(size);
	}

	@Override
	public synchronized void write(byte b[], int off, int len) {
		if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) - b.length > 0)) {
			throw new IndexOutOfBoundsException();
		}
		int nl = ensureCapacity(count + len);
		if (nl != len) {
			off += len - nl;
			len = nl;
		}
		System.arraycopy(b, off, buf, count, len);
		count += len;
	}

	@Override
	public synchronized void write(int b) {
		ensureCapacity(count + 1);
		buf[count] = (byte) b;
		count += 1;
	}

	private int ensureCapacity(int minCapacity) {
		// overflow-conscious code
		if (minCapacity - buf.length > 0) {
			return roll(minCapacity);
		}

		return minCapacity - count;
	}

	private int roll(int minCapacity) {
		int rollSize = minCapacity - count;
		int fitSize = buf.length - rollSize;
		if (fitSize <= 0) {
			count = 0;
			return buf.length;
		}

		int free = buf.length - count;
		int pos = rollSize - free;
		int len = count - pos;

		rollBuf(buf, pos, len);
		count = len;

		return rollSize;
	}

	private static void rollBuf(byte[] buf, int pos, int len) {
		System.arraycopy(buf, pos, buf, 0, len);

		for (int i = len; i < buf.length; i++) {
			buf[i] = 0;
		}
	}
}
