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

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.sink.DefaultEventSinkFactory;
import com.jkoolcloud.tnt4j.sink.EventSink;

/**
 * Logger related utility methods.
 *
 * @version $Revision: 1 $
 */
public class LoggerUtils {

	/**
	 * System property name for TNT4J-Stream-JMX log file name.
	 */
	public static final String SYS_PROP_STREAM_LOG_FILE_NAME = "tnt4j.stream.log.filename"; // NON-NLS
	/**
	 * System property name for TNT4J-Stream-JMX activities log file name.
	 */
	public static final String SYS_PROP_ACTIVITIES_LOG_FILE_NAME = "tnt4j.activities.log.filename"; // NON-NLS

	private static final String DEFAULT_STREAM_LOG_FILE_NAME = "./logs/tnt4j-stream-jmx.log"; // NON-NLS
	private static final String DEFAULT_ACTIVITIES_LOG_FILE_NAME = "./logs/tnt4j-stream-jmx_samples.log"; // NON-NLS

	/**
	 * Obtains default logger event sink.
	 * <p>
	 * Performs fallback values initialization for required system properties.
	 *
	 * @param clazz
	 *            class for which to get the event sink
	 * @return new event sink instance associated with given class
	 *
	 * @see com.jkoolcloud.tnt4j.sink.DefaultEventSinkFactory#defaultEventSink(Class)
	 */
	public static EventSink getLoggerSink(Class<?> clazz) {
		initLoggerProperties();
		return DefaultEventSinkFactory.defaultEventSink(clazz);
	}

	private static void initLoggerProperties() {
		String lProp = System.getProperty(DefaultEventSinkFactory.DEFAULT_EVENT_FACTORY_KEY);
		if (StringUtils.isEmpty(lProp)) {
			System.setProperty(DefaultEventSinkFactory.DEFAULT_EVENT_FACTORY_KEY,
					"com.jkoolcloud.tnt4j.sink.impl.slf4j.SLF4JEventSinkFactory");
		}
		lProp = System.getProperty(SYS_PROP_STREAM_LOG_FILE_NAME);
		if (StringUtils.isEmpty(lProp)) {
			System.setProperty(SYS_PROP_STREAM_LOG_FILE_NAME, DEFAULT_STREAM_LOG_FILE_NAME);
		}
		lProp = System.getProperty(SYS_PROP_ACTIVITIES_LOG_FILE_NAME);
		if (StringUtils.isEmpty(lProp)) {
			System.setProperty(SYS_PROP_ACTIVITIES_LOG_FILE_NAME, DEFAULT_ACTIVITIES_LOG_FILE_NAME);
		}
	}
}
