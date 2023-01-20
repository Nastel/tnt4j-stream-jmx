/*
 * Copyright 2015-2023 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx.vm;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;

/**
 * This class provides a generic way to get a default {@link com.jkoolcloud.tnt4j.stream.jmx.vm.VMResolverFactory}
 * instance.
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.CoreVMResolverFactory
 */
public class DefaultVMResolverFactory {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(DefaultVMResolverFactory.class);

	/**
	 * Constant defining default VMs JMX connections resolver factory class name: {@value}.
	 */
	public static final String DEFAULT_RESOLVER_FACTORY = "com.jkoolcloud.tnt4j.stream.jmx.vm.CoreVMResolverFactory";
	private static VMResolverFactory defaultFactory;

	private static VMResolverFactory initFactory(String factoryClassName) {
		try {
			Class<?> factoryClass = Class
					.forName(StringUtils.isEmpty(factoryClassName) ? DEFAULT_RESOLVER_FACTORY : factoryClassName);
			return (VMResolverFactory) factoryClass.newInstance();
		} catch (Throwable ex) {
			LOGGER.log(OpLevel.ERROR, "Failed to initialize VM resolver factory using class: {0}", factoryClassName,
					ex);
			return new CoreVMResolverFactory();
		}
	}

	private DefaultVMResolverFactory() {
	}

	/**
	 * Obtain a default instance of {@link VMResolverFactory}
	 *
	 * @param factoryClassName
	 *            VM resolver factory class name
	 * @return default VM resolver factory instance
	 */
	public synchronized static VMResolverFactory getInstance(String factoryClassName) {
		if (defaultFactory == null) {
			defaultFactory = initFactory(factoryClassName);
		}

		return defaultFactory;
	}
}
