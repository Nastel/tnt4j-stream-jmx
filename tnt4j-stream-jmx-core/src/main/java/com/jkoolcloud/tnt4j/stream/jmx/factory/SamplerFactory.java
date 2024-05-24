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
package com.jkoolcloud.tnt4j.stream.jmx.factory;

import java.util.Map;

import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;

/**
 * <p>
 * This interface defines a way to obtain underlying implementation of the sampler object that actually samples
 * underlying MBeans.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see Sampler
 */
public interface SamplerFactory {

	/**
	 * Create a default instance with default MBean server instance
	 * {@link java.lang.management.ManagementFactory#getPlatformMBeanServer()}.
	 *
	 * @return new sampler instance
	 * 
	 * @see Sampler
	 */
	Sampler newInstance();

	/**
	 * Performs sampling procedure initialization.
	 */
	void initialize();

	/**
	 * Create a default instance with a given MBean server instance.
	 * 
	 * @param mServerConn
	 *            MBean server connection instance
	 * @return new sampler instance
	 * 
	 * @see Sampler
	 */
	Sampler newInstance(JMXServerConnection mServerConn);

	/**
	 * Creates instance of {@link SampleListener} to be used by {@link Sampler}.
	 * 
	 * @param properties
	 *            listener configuration properties map
	 * @return sample listener instance to use
	 *
	 * @see SampleListener
	 */
	SampleListener newListener(Map<String, ?> properties);

	/**
	 * Returns default class name for a event formatter to be used by sampler logger.
	 *
	 * @return name of default event formatter class
	 */
	String defaultEventFormatterClassName();

	/**
	 * Creates instance of {@link SampleHandler} to be used by {@link Sampler}.
	 * 
	 * @param config
	 *            configuration map for this sample handler
	 * @return sample handler instance
	 */
	SampleHandler newSampleHandler(Map<String, ?> config);
}
