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
package org.tnt4j.pingjmx;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import org.tnt4j.pingjmx.conditions.AttributeAction;
import org.tnt4j.pingjmx.conditions.Condition;

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
	 * Obtain MBean server associated with this object
	 * 
	 * @return MBean server instance
	 */
	MBeanServer getMBeanServer();
	
	/**
	 * Obtain <code>TrackingLogger</code> instance for logging
	 * 
	 * @return tracking logger instance associated with this pinger
	 */
	TrackingLogger getLogger();
	
	/**
	 * Schedule ping with associated MBean server instance
	 * and all MBeans.
	 *  
	 * @param period sampling time in milliseconds
	 * 
	 */
	Pinger setSchedule(long period) throws IOException;

	/**
	 * Schedule ping with associated MBean server instance
	 *  
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling time in milliseconds
	 * 
	 */
	Pinger setSchedule(String jmxfilter, long period) throws IOException;	

	/**
	 * Schedule ping with associated MBean server instance
	 *  
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling time
	 * @param tunit time units for sampling period
	 * 
	 */
	Pinger setSchedule(String jmxfilter, long period, TimeUnit tunit) throws IOException;
	
	
	/**
	 * Register a condition/action pair which will
	 * be evaluated every sampling interval.
	 *
	 * @param cond user defined condition
	 * @param action user defined action
	 *  
	 */
	Pinger register(Condition cond, AttributeAction action);

	/**
	 * Obtain latest sampling statistics for current instance
	 *
	 * @return latest sampling statistics
	 *  
	 */
	SampleContext getStats();

	/**
	 * Cancel/close this object instance and cancel all outstanding
	 * or scheduled samplers.
	 */
	void cancel();
}
