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

import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import org.tnt4j.pingjmx.conditions.AttributeAction;
import org.tnt4j.pingjmx.conditions.Condition;
import org.tnt4j.pingjmx.conditions.ConditionalListener;

import com.nastel.jkool.tnt4j.ActivityScheduler;

/**
 * <p> 
 * This class provides scheduled ping/heart-beat for a given
 * JMX <code>MBeanServer</code>. 
 * </p>
 * 
 * @version $Revision: 1 $
 */
public class PingJmx extends ActivityScheduler {
	public static final String JMX_FILTER_ALL = "*:*";
	
	protected ConditionalListener listener;
	
	/**
	 * Create new instance of <code>PingJmx</code> with a given
	 * name, MBean server, sampling period. Filter is set to 
	 * all MBeans.
	 *
	 * @param name name of assigned to the pinger
	 * @param server MBean server instance
	 * @param period sampling period in milliseconds
	 *  
	 */
	public PingJmx(String name, MBeanServer server, long period) {
		this(name, server, JMX_FILTER_ALL, period);
    }
	
	/**
	 * Create new instance of <code>PingJmx</code> with a given
	 * name, MBean server, filter list and sampling period.
	 *
	 * @param name name of assigned to the pinger
	 * @param server MBean server instance
	 * @param filterList JMX filters semicolon separated
	 * @param period sampling period in milliseconds
	 *  
	 */
	public PingJmx(String name, MBeanServer server, String filterList, long period) {
	    super(name, newListenerImpl(server, filterList));
	    this.schedule(name, period);
	    this.listener = (ConditionalListener) this.getListener();
    }
	
	/**
	 * Create new instance of <code>PingJmx</code> with a given
	 * name, MBean server, filter list and sampling period.
	 *
	 * @param name name of assigned to the pinger
	 * @param server MBean server instance
	 * @param filterList JMX filters semicolon separated
	 * @param period sampling period
	 * @param tunit time unit for the sampling period
	 *  
	 */
	public PingJmx(String name, MBeanServer server, String filterList, long period, TimeUnit tunit) {
	    super(name, newListenerImpl(server, filterList));
	    this.schedule(name, period, tunit);
	    this.listener = (ConditionalListener) this.getListener();
    }
	
	/**
	 * Register a condition/action pair which will
	 * be evaluated every sampling interval.
	 *
	 * @param cond user defined condition
	 * @param action user defined action
	 *  
	 */
	public void register(Condition cond, AttributeAction action) {
		listener.register(cond, action);
	}

	/**
	 * Create new instance of <code>ActivityListener</code>
	 * Override this call to return your instance of listener
	 *
	 * @param server MBean server instance
	 * @param filterList JMX filters semicolon separated
	 *  
	 * @see ConditionalListener
	 * @return new conditional listener instance
	 */
	protected static ConditionalListener newListenerImpl(MBeanServer server, String filterList) {
		return new PingJmxListener(server, filterList);
	}	
}
