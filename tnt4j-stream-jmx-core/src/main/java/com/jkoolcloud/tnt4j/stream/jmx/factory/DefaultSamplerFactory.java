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
package com.jkoolcloud.tnt4j.stream.jmx.factory;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.impl.PlatformSamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;

/**
 * <p>
 * This class provides a generic way to get a default {@link SamplerFactory} instance.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see SamplerFactory
 */
public class DefaultSamplerFactory {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(DefaultSamplerFactory.class);

	public static final String DEFAULT_SAMPLER_FACTORY = "com.jkoolcloud.tnt4j.stream.jmx.impl.PlatformSamplerFactory";
	private static SamplerFactory defaultFactory;

	private static SamplerFactory initFactory(String factoryClassName) {
		try {
			Class<?> factoryClass = Class
					.forName(StringUtils.isEmpty(factoryClassName) ? DEFAULT_SAMPLER_FACTORY : factoryClassName);
			return (SamplerFactory) factoryClass.newInstance();
		} catch (Throwable ex) {
			LOGGER.log(OpLevel.ERROR, "Failed to initialize sampler factory using class: {0}", factoryClassName, ex);
			return new PlatformSamplerFactory();
		}
	}

	private DefaultSamplerFactory() {
	}

	/**
	 * Obtain a default instance of {@link SamplerFactory}
	 *
	 * @param factoryClassName
	 *            sampler factory class name
	 *
	 * @return default sample factory instance
	 */
	public synchronized static SamplerFactory getInstance(String factoryClassName) {
		if (defaultFactory == null) {
			defaultFactory = initFactory(factoryClassName);
		}

		return defaultFactory;
	}
}
