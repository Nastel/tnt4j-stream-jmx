/*
 * Copyright 2015-2017 JKOOL, LLC.
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

import java.io.*;
import java.util.Arrays;
import java.util.List;

import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * WAS console output captor.
 *
 * @version $Revision: 1 $
 */
public class ConsoleOutputCaptor {
	private static final int OUT_BUF_SIZE = 8192;

	private ByteArrayOutputStream captorOutput;
	private PrintStream previous;
	private boolean capturing;

	public void start() {
		if (capturing) {
			return;
		}

		capturing = true;
		previous = System.out;
		captorOutput = new ByteArrayOutputStream(OUT_BUF_SIZE);

		OutputStream outputStreamCombiner = new OutputStreamCombiner(Arrays.asList(previous, captorOutput));
		PrintStream custom = new PrintStream(outputStreamCombiner);

		System.setOut(custom);
	}

	public String stop() {
		if (!capturing) {
			return "";
		}

		System.setOut(previous);

		String capturedValue = getCaptured();

		Utils.close(captorOutput);

		captorOutput = null;
		previous = null;
		capturing = false;

		return capturedValue;
	}

	public String getCaptured() {
		if (capturing) {
			try {
				return captorOutput.toString(Utils.UTF8);
			} catch (UnsupportedEncodingException exc) {
				return captorOutput.toString();
			}
		} else {
			return "Not capturing WAS console output";
		}
	}

	private static class OutputStreamCombiner extends OutputStream {
		private List<OutputStream> outputStreams;

		public OutputStreamCombiner(List<OutputStream> outputStreams) {
			this.outputStreams = outputStreams;
		}

		@Override
		public void write(int b) throws IOException {
			for (OutputStream os : outputStreams) {
				os.write(b);
			}
		}

		@Override
		public void flush() throws IOException {
			for (OutputStream os : outputStreams) {
				os.flush();
			}
		}

		@Override
		public void close() throws IOException {
			for (OutputStream os : outputStreams) {
				os.close();
			}
		}
	}
}