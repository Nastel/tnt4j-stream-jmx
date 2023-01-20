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

import javax.management.MBeanServerConnection;

import com.jkoolcloud.tnt4j.stream.jmx.conditions.NestedHandler;

/**
 * <p>
 * This interface provides a way to obtain sample context.
 * </p>
 * 
 * @see Sampler
 * @see NestedHandler
 * @see SampleContext
 * 
 * @version $Revision: 1 $
 */
public interface SampleContext {
	/**
	 * Obtain associated MBean server connection instance.
	 * 
	 * @return MBean server connection instance associated with this listener
	 */
	MBeanServerConnection getMBeanServer();

	/**
	 * Reset all counters maintained by this context
	 * 
	 * @return instance to the same context
	 */
	SampleContext resetCounters();

	/**
	 * Obtain last error exception occurred during last sample
	 * 
	 * @return last error exception occurred during last sample
	 */
	Throwable getLastError();

	/**
	 * Obtain number of executed samples
	 * 
	 * @return number of executed samples
	 */
	long getSampleCount();

	/**
	 * Obtain number of total metrics sampled for all samples
	 * 
	 * @return number of total metrics sampled for all samples
	 */
	long getTotalMetricCount();

	/**
	 * Obtain number of total skipped/ignored samples
	 * 
	 * @return number of total skipped/ignored samples
	 */
	long getTotalNoopCount();

	/**
	 * Obtain number of total failed samples
	 * 
	 * @return number of total failed samples
	 */
	long getTotalErrorCount();

	/**
	 * Obtain number of samples metrics during last sample
	 * 
	 * @return number of samples metrics during last sample
	 */
	long getLastMetricCount();

	/**
	 * Obtain time in microseconds it took to take a last sample
	 * 
	 * @return number of samples metrics during last sample
	 */
	long getLastSampleUsec();

	/**
	 * Obtain number of sampled MBeans
	 * 
	 * @return number of sampled MBeans
	 */
	long getMBeanCount();

	/**
	 * Obtain number of excluded MBean attributes due to some kind of exception during sampling.
	 * 
	 * @return number of excluded MBean attributes
	 */
	long getExcludeAttrCount();
}
