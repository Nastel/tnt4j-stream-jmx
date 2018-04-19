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
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

import com.jkoolcloud.tnt4j.format.DefaultFormatter;
import com.jkoolcloud.tnt4j.format.EventFormatter;
import com.jkoolcloud.tnt4j.format.SimpleFormatter;
import com.jkoolcloud.tnt4j.sink.*;

/**
 * TNT4J log sink events captor, capturing data written to log sink.
 *
 * @version $Revision: 1 $
 */
public class TextAreaLogAppender implements SinkLogEventListener {

	private static TextAreaLogAppender instance;
	private PrintWriter writer;
	private final DefaultFormatter formatter = new SimpleFormatter();// new DefaultFormatter();
	private ByteArrayOutputStream outStream;

	private TextAreaLogAppender() {
	}

	public void start() {
		final EventSinkFactory delegate = DefaultEventSinkFactory.getInstance();
		final SinkLogEventListener logToConsoleEvenSinkListener = this;
		DefaultEventSinkFactory.setDefaultEventSinkFactory(new EventSinkFactory() {
			@Override
			public EventSink getEventSink(String name) {
				EventSink eventSink = delegate.getEventSink(name);
				configure(eventSink);
				return eventSink;
			}

			@Override
			public EventSink getEventSink(String name, Properties props) {
				EventSink eventSink = delegate.getEventSink(name, props);
				configure(eventSink);
				return eventSink;
			}

			@Override
			public EventSink getEventSink(String name, Properties props, EventFormatter frmt) {
				EventSink eventSink = delegate.getEventSink(name, props, frmt);
				configure(eventSink);
				return eventSink;
			}

			@Override
			public long getTTL() {
				return delegate.getTTL();
			}

			@Override
			public void setTTL(long ttl) {
				delegate.setTTL(ttl);
			}

			private void configure(EventSink eventSink) {
				eventSink.filterOnLog(false);
				eventSink.addSinkLogEventListener(logToConsoleEvenSinkListener);
			}
		});

		outStream = new RollingOutputStream(RollingOutputStream.OUT_BUF_SIZE);
		writer = new PrintWriter(outStream, true);
	}

	public void stop() {
		Utils.close(writer);
	}

	public String getCaptured() {
		if (outStream != null) {
			try {
				return outStream.toString(com.jkoolcloud.tnt4j.utils.Utils.UTF8);
			} catch (UnsupportedEncodingException exc) {
				return outStream.toString();
			}
		} else {
			return "!!!   Not capturing Log Sink events...   !!!!";
		}
	}

	@Override
	public void sinkLogEvent(SinkLogEvent ev) {
		writer.println(formatter.format(ev.getTTL(), ev.getEventSource(), ev.getSeverity(),
				ev.getSinkObject().toString(), ev.getArguments()));
	}

	public static synchronized TextAreaLogAppender getInstance() {
		if (instance == null) {
			instance = new TextAreaLogAppender();
		}

		return instance;
	}
}
