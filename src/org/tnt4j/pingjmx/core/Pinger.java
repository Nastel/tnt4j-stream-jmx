/*
 * Copyright 2015 Nastel Technologies, Inc.
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
package org.tnt4j.pingjmx.core;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import org.tnt4j.pingjmx.conditions.AttributeAction;
import org.tnt4j.pingjmx.conditions.Condition;
import org.tnt4j.pingjmx.conditions.NestedHandler;

import com.nastel.jkool.tnt4j.TrackingLogger;

/**
 * <p> 
 * This interface defines <code>Pinger</code> which allows sampling
 * of MBeans based on a given sampling period.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 */
public interface Pinger extends NestedHandler<Pinger, SampleListener>, Runnable {
	public static final String JMX_FILTER_ALL = "*:*";

	/**
	 * Name associated with this Pinger
	 * 
	 * @return condition name
	 * @throws IllegalStateException if setSchedule is not called first
	 */
	String getName();
	
	/**
	 * MBean filter associated with this pinger
	 * 
	 * @return filter list
	 * @throws IllegalStateException if setSchedule is not called first
	 */
	String getFilter();
	
	/**
	 * Sampling period in milliseconds
	 * 
	 * @return Sampling period in milliseconds
	 * @throws IllegalStateException if setSchedule is not called first
	 */
	long getPeriod();

		/**
	 * Obtain MBean server associated with this object
	 * 
	 * @return MBean server instance
	 */
	MBeanServer getMBeanServer();
	
	/**
	 * Obtain <code>TrackingLogger</code> instance for logging
	 * 
	 * @return tracking logger instance associated with this pinger
	 * @throws IllegalStateException if setSchedule is not called first
	 */
	TrackingLogger getLogger();
	
	/**
	 * Set schedule ping with associated MBean server instance
	 * and all MBeans.
	 *  
	 * @param period sampling time in milliseconds
	 * @throws IOException 
	 * @throws IllegalStateException if setSchedule is not called first
	 * 
	 */
	Pinger setSchedule(long period) throws IOException;

	/**
	 * Set schedule ping with associated MBean server instance
	 *  
	 * @param mbeanFilter semicolon separated filter list
	 * @param period sampling time in milliseconds
	 * @throws IOException 
	 * @throws IllegalStateException if setSchedule is not called first
	 * 
	 */
	Pinger setSchedule(String mbeanFilter, long period) throws IOException ;	

	/**
	 * Set schedule ping with associated MBean server instance
	 *  
	 * @param mbeanFilter semicolon separated filter list
	 * @param period sampling time
	 * @param tunit time units for sampling period
	 * @throws IOException 
	 * @throws IllegalStateException if setSchedule is not called first
	 * 
	 */
	Pinger setSchedule(String mbeanFilter, long period, TimeUnit tunit) throws IOException ;
	
	
	/**
	 * Register a condition/action pair which will
	 * be evaluated every sampling interval.
	 *
	 * @param cond user defined condition
	 * @param action user defined action
	 * @throws IllegalStateException if setSchedule is not called first
	 *  
	 */
	Pinger register(Condition cond, AttributeAction action);

	/**
	 * Obtain latest sampling statistics for current instance
	 *
	 * @return latest sampling statistics
	 * @throws IllegalStateException if setSchedule is not called first
	 *  
	 */
	SampleContext getStats();

	/**
	 * Cancel/close this object instance and cancel all outstanding
	 * or scheduled samplers.
	 * @throws IllegalStateException if setSchedule is not called first
	 */
	void cancel();
}
