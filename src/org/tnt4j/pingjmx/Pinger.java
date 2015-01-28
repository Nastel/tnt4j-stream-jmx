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
public interface Pinger {
	/**
	 * Obtain MBean server associated with this object
	 * 
	 * @return MBean server instance
	 */
	public MBeanServer getMBeanServer();
	
	/**
	 * Schedule JMX ping with associated MBean server instance
	 * and all MBeans.
	 *  
	 * @param period sampling time in milliseconds
	 * 
	 */
	public void scheduleJmxPing(long period) throws IOException;

	/**
	 * Schedule JMX ping with associated MBean server instance
	 *  
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling time in milliseconds
	 * 
	 */
	public void scheduleJmxPing(String jmxfilter, long period) throws IOException;	

	/**
	 * Schedule JMX ping with associated MBean server instance
	 *  
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling time
	 * @param tunit time units for sampling period
	 * 
	 */
	public void scheduleJmxPing(String jmxfilter, long period, TimeUnit tunit) throws IOException;
	
	/**
	 * Cancel/close this object instance and cancel all outstanding
	 * or scheduled samplers.
	 */
	public void cancel();
}
