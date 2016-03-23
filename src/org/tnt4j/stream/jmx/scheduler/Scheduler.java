/*
 * Copyright 2015 JKOOL, LLC.
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
package org.tnt4j.stream.jmx.scheduler;

import java.io.IOException;

import org.tnt4j.stream.jmx.conditions.AttributeAction;
import org.tnt4j.stream.jmx.conditions.AttributeCondition;
import org.tnt4j.stream.jmx.conditions.SampleHandler;

import com.nastel.jkool.tnt4j.TrackingLogger;

/**
 * <p>
 * This interface provides a way to implement classes that implement 
 * scheduled sample/heart-beat for a given JMX {@code MBeanServer}.
 * </p>
 * 
 * @version $Revision: 1 $
 */
public interface Scheduler extends Runnable {
	/**
	 * Name associated with this object
	 * 
	 * @return object name
	 */
	String getName();

	/**
	 * Sampling period in milliseconds
	 * 
	 * @return Sampling period in milliseconds
	 */
	long getPeriod();

	/**
	 * Open current scheduled activity instance.
	 * @throws IOException 
	 * 
	 */
	void open() throws IOException;

	/**
	 * Close current scheduled activity instance.
	 * 
	 */
	void close();

	/**
	 * Register a condition/action pair which will be evaluated every sampling
	 * interval.
	 *
	 * @param cond
	 *            user defined condition
	 * @param action
	 *            user defined action
	 * 
	 */
	void register(AttributeCondition cond, AttributeAction action);

	/**
	 * Obtain sample handler instance which is triggered on every sample.
	 * Sample handler instance is invoked on every sample and handles
	 * all metric collection.
	 *
	 * @return conditional listener instance
	 */
	SampleHandler getSampleHandler();
	
	/**
	 * MBean include filter associated with this sampler
	 * 
	 * @return include filter list
	 */
	String getIncFilter();
	
	/**
	 * MBean Exclude filter associated with this sampler
	 * 
	 * @return exclude filter list
	 */
	String getExcFilter();
	
	/**
	 * Obtain {@code TrackingLogger} instance for logging associated with
	 * this scheduler instance.
	 * 
	 * @return tracking logger instance
	 */
	TrackingLogger getLogger();
}
