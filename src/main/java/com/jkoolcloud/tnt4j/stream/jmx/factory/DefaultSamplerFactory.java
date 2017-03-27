/*
 * Copyright 2015-2017 JKOOL, LLC.
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

import com.jkoolcloud.tnt4j.stream.jmx.impl.PlatformSamplerFactory;

/**
 * <p>
 * This class provides a generic way to get a default {@link SamplerFactory}
 * instance.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see SamplerFactory
 */
public class DefaultSamplerFactory {
	public static final String DEFAULT_PING_FACTORY = "com.jkoolcloud.tnt4j.stream.jmx.impl.PlatformSamplerFactory";
	private static SamplerFactory defaultFactory;

	static {
		String factoryClassName = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.sampler.factory", DEFAULT_PING_FACTORY);
		try {
			Class<?> factoryClass = Class.forName(factoryClassName);
			defaultFactory = (SamplerFactory) factoryClass.newInstance();
		} catch (Throwable ex) {
			defaultFactory = new PlatformSamplerFactory();
			ex.printStackTrace();
		}
	}

	private DefaultSamplerFactory() {}

	/**
	 * Obtain a default instance of {@link SamplerFactory}
	 *
	 * @return default sample factory instance
	 */
	public static SamplerFactory getInstance() {
		return defaultFactory;
	}
}
