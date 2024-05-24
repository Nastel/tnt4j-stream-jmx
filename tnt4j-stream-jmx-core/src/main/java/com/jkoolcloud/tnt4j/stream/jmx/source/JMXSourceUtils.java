/*
 * Copyright 2014-2023 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx.source;

import java.io.IOException;
import java.util.Arrays;

import com.jkoolcloud.tnt4j.config.DefaultConfigFactory;
import com.jkoolcloud.tnt4j.config.TrackerConfig;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.source.SourceFactory;
import com.jkoolcloud.tnt4j.source.SourceType;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgentThread;

/**
 * JMX {@link Source} resolution utility methods.
 * 
 * @version $Revision: 1 $
 */
public class JMXSourceUtils {

	/**
	 * Returns filled-in source handle representing the user defined path.
	 * 
	 * @param cls
	 *            class to resolve user defined source name
	 * @param logger
	 *            logger instance to log messages
	 * @return source handle representing the user defined path, or root source if no user defined source path
	 *
	 * @throws IOException
	 *             if JMX server communication failure occurs
	 */
	public static Source getSource(Class<?> cls, EventSink logger) throws IOException {
		return getSource(cls.getName(), logger);
	}

	/**
	 * Returns filled-in source handle representing the user defined path.
	 * 
	 * @param className
	 *            class name to resolve user defined source name
	 * @param logger
	 *            logger instance to log messages
	 * @return source handle representing the user defined path, or root source if no user defined source path
	 *
	 * @throws IOException
	 *             if JMX server communication failure occurs
	 */
	public static Source getSource(String className, EventSink logger) throws IOException {
		TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(className);
		SourceFactory factory = config.getSourceFactory();

		if (factory == null) {
			factory = JMXSourceFactoryImpl.getInstance();
		}

		String sourceDescriptor = null;
		Thread thread = Thread.currentThread();
		if (thread instanceof SamplingAgentThread) {
			sourceDescriptor = ((SamplingAgentThread) thread).getVMSourceFQN();
		}

		if (sourceDescriptor == null) {
			return factory.getRootSource();
		}

		Source source;

		try {
			source = factory.fromFQN(sourceDescriptor);
		} catch (IllegalArgumentException e) {
			// TODO placeholders doesn't fill right
			logger.log(OpLevel.WARNING,
					"JMXSourceUtils.getSource: source descriptor ''{1}'' does not match FQN pattern ''SourceType=SourceValue''. Erroneous source type. Valid types ''{0}''. Cause: {2}",
					Arrays.toString(SourceType.values()), sourceDescriptor, e.getMessage());
			throw new RuntimeException("Invalid Source configuration");
		} catch (IndexOutOfBoundsException e) {
			logger.log(OpLevel.WARNING,
					"JMXSourceUtils.getSource: source descriptor ''{0}'' does not match FQN pattern ''SourceType=SourceValue''. Defaulting to ''SERVICE={0}''",
					sourceDescriptor);
			source = factory.newSource(sourceDescriptor, SourceType.SERVICE);
		} catch (JMXCommunicationException exc) {
			throw (IOException) exc.getCause();
		}

		return source;
	}
}
