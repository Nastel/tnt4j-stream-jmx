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
package com.jkoolcloud.tnt4j.stream.jmx.core;

import java.io.IOException;
import java.util.Map;

import com.jkoolcloud.tnt4j.TrackingLogger;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeAction;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeCondition;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.NestedHandler;

/**
 * <p>
 * This interface sampling of MBeans based on a given sampling period and a set of include/exclude filters.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 */
public interface Sampler extends NestedHandler<Sampler, SampleListener>, Runnable {
	public static final String JMX_FILTER_ALL = "*:*";
	public static final String JMX_FILTER_NONE = "";
	public static final int JMX_SAMPLE_PERIOD = 30000;

	public static final String CFG_INITIAL_DELAY = "S_CFG_INITIAL_DELAY";
	public static final String CFG_SAMPLING_PERIOD = "S_CFG_SAMPLING_PERIOD";
	public static final String CFG_TIME_UNIT = "S_CFG_TIME_UNIT";
	public static final String CFG_SAMPLER_FACTORY = "S_CFG_SAMPLER_FACTORY";

	/**
	 * Name associated with this Sampler
	 * 
	 * @return condition name
	 * @throws IllegalStateException
	 *             if setSchedule is not called first
	 */
	String getName();

	/**
	 * Sampling period in milliseconds
	 *
	 * @return Sampling period in milliseconds
	 * @throws IllegalStateException
	 *             if setSchedule is not called first
	 */
	long getPeriod();

	/**
	 * Obtain MBean server associated with this object
	 *
	 * @return MBean server connection instance
	 */
	JMXServerConnection getMBeanServer();

	/**
	 * Obtain {@link TrackingLogger} instance for logging
	 *
	 * @return tracking logger instance associated with this sampler
	 * @throws IllegalStateException
	 *             if setSchedule is not called first
	 */
	TrackingLogger getLogger();

	/**
	 * Set schedule sample with associated MBean server instance
	 *
	 * @param samplerCfg
	 *            sampler configuration map
	 *
	 * @return this sampler instance
	 *
	 * @throws IOException
	 *             if I/O exception occurs opening sampler
	 * @throws IllegalStateException
	 *             if setSchedule is not called first
	 */
	Sampler setSchedule(Map<String, Object> samplerCfg) throws IOException;

	/**
	 * Register a condition/action pair which will be evaluated every sampling interval.
	 *
	 * @param cond
	 *            user defined condition
	 * @param action
	 *            user defined action
	 * @return this sampler instance
	 * 
	 * @throws IllegalStateException
	 *             if setSchedule is not called first
	 */
	Sampler register(AttributeCondition cond, AttributeAction action);

	/**
	 * Obtain sample context associated with the handler
	 *
	 * @return sample context associated with the handler
	 * @throws IllegalStateException
	 *             if setSchedule is not called first
	 */
	SampleContext getContext();

	/**
	 * Cancel/close this object instance and cancel all outstanding or scheduled samplers.
	 *
	 * @throws IllegalStateException
	 *             if setSchedule is not called first
	 */
	void cancel();
}
