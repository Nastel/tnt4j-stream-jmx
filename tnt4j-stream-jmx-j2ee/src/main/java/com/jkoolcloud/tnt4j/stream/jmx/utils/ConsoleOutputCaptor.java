/*
 * Copyright 2015-2018 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jkoolcloud.tnt4j.stream.jmx.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * Console output captor, capturing data written to {@link System#out} and {@link System#err} print streams.
 *
 * @version $Revision: 1 $
 */
public class ConsoleOutputCaptor {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(ConsoleOutputCaptor.class);

	private CaptorPrintStream outStream;
	private CaptorPrintStream errStream;

	private static ConsoleOutputCaptor instance;

	private ConsoleOutputCaptor() {
	}

	public static synchronized ConsoleOutputCaptor getInstance() {
		if (instance == null) {
			instance = new ConsoleOutputCaptor();
		}

		return instance;
	}

	public void start() {
		if (System.out instanceof CaptorPrintStream) {
			LOGGER.log(OpLevel.WARNING, "!!!!   ConsoleOutputCaptor must be already running!..   !!!!");
			outStream = (CaptorPrintStream) System.out;
			errStream = (CaptorPrintStream) System.err;
		} else {
			ByteArrayOutputStream captorOutput = new RollingOutputStream(RollingOutputStream.OUT_BUF_SIZE);
			outStream = new CaptorPrintStream(System.out, captorOutput, "OUT");
			errStream = new CaptorPrintStream(System.err, captorOutput, "ERR");

			System.setOut(outStream);
			System.setErr(errStream);
		}
	}

	public String stop() {
		if (outStream == null) {
			return "";
		}

		System.setOut(outStream.original());
		System.setErr(errStream.original());

		String capturedValue = getCaptured();

		Utils.close(outStream);
		Utils.close(errStream);

		Utils.close(outStream.delegate);

		outStream = null;
		errStream = null;

		return capturedValue;
	}

	public String getCaptured() {
		if (outStream != null) {
			try {
				return outStream.delegate.toString(Utils.UTF8);
			} catch (UnsupportedEncodingException exc) {
				return outStream.delegate.toString();
			}
		} else {
			return "!!!   Not capturing System.out and System.err streams...   !!!!";
		}
	}

	private static class CaptorPrintStream extends PrintStream {
		private static final DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);

		static {
			SimpleDateFormat sdf = (SimpleDateFormat) df;
			sdf.applyPattern(sdf.toPattern() + " HH:mm:ss.SSS");
		}

		private PrintStream original;
		private ByteArrayOutputStream delegate;
		private String prefix;

		public CaptorPrintStream(PrintStream original, ByteArrayOutputStream delegate, String prefix) {
			super(delegate);

			this.original = original;
			this.delegate = delegate;
			this.prefix = prefix;
		}

		public PrintStream original() {
			return original;
		}

		@Override
		public void println(String str) {
			original.println(str);

			println_(str);
		}

		@Override
		public void println(Object x) {
			original.println(x);

			println_(String.valueOf(x));
		}

		@Override
		public void println(boolean x) {
			original.println(x);

			println_(String.valueOf(x));
		}

		@Override
		public void print(char c) {
			original.println(c);

			println_(String.valueOf(c));
		}

		@Override
		public void print(int i) {
			original.println(i);

			println_(String.valueOf(i));

		}

		@Override
		public void println(long x) {
			original.println(x);

			println_(String.valueOf(x));
		}

		@Override
		public void println(float x) {
			original.println(x);

			println_(String.valueOf(x));
		}

		@Override
		public void println(double x) {
			original.println(x);

			println_(String.valueOf(x));
		}

		@Override
		public void println(char x[]) {
			original.println(x);

			println_(String.valueOf(x));
		}

		private void println_(String str) {
			StringBuilder sb = new StringBuilder((str == null ? 4 : str.length()) + 32);
			if (StringUtils.isNotEmpty(prefix)) {
				sb.append("[").append(prefix).append("] ");
			}

			sb.append("[").append(df.format(new Date())).append("] ");
			sb.append(str);

			super.println(sb.toString());
		}

		@Override
		public void flush() {
			original.flush();
			try {
				delegate.flush();
			} catch (IOException exc) {
			}
		}

		@Override
		public void close() {
		}
	}

}