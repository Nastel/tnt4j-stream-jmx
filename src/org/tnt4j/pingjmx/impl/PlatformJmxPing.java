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
package org.tnt4j.pingjmx.impl;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import org.tnt4j.pingjmx.PingJmx;
import org.tnt4j.pingjmx.Pinger;
import org.tnt4j.pingjmx.SampleListener;
import org.tnt4j.pingjmx.SampleStats;
import org.tnt4j.pingjmx.conditions.AttributeAction;
import org.tnt4j.pingjmx.conditions.Condition;

import com.nastel.jkool.tnt4j.TrackingLogger;

/**
 * <p> 
 * This class provides scheduled execution and sampling of a JMX metrics
 * for a given <code>MBeanServer</code> instance. By default the class will
 * use <code>ManagementFactory.getPlatformMBeanServer()</code> instance.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 * @see PingJmx
 */
public class PlatformJmxPing implements Pinger {	
	protected PingJmx pinger;
	protected MBeanServer targetServer;
	
	/**
	 * Create a default instance with default MBean server instance
	 * <code>ManagementFactory.getPlatformMBeanServer()</code>
	 * 
	 */
	protected PlatformJmxPing() {
		this(ManagementFactory.getPlatformMBeanServer());
	}
	
	/**
	 * Create a default instance with a given MBean server instance
	 * 
	 * @param mserver MBean server instance
	 */
	protected PlatformJmxPing(MBeanServer mserver) {
		targetServer = mserver;
	}
	
	@Override
	public MBeanServer getMBeanServer() {
		return targetServer;
	}
	
	@Override
    public TrackingLogger getLogger() {
		if (pinger == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
	    return pinger.getLogger();
    }

	@Override
	public Pinger setSchedule(long period) throws IOException {
		return setSchedule(JMX_FILTER_ALL, period);
	}

	@Override
	public Pinger setSchedule(String jmxfilter, long period) throws IOException {
		return setSchedule(jmxfilter, period, TimeUnit.MILLISECONDS);
	}	

	@Override
	public Pinger setSchedule(String jmxfilter, long period, TimeUnit tunit) throws IOException {
		if (pinger == null) {
			pinger = newPingJmxImpl(getMBeanServer(), jmxfilter, period, tunit);
		}
		pinger.open();
		return this;
	}

	@Override
	public void cancel() {
		if (pinger == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		pinger.close();
	}
	
	/**
	 * Obtain underlying instance of the <code>PingJmx</code>
	 * that actually runs/schedules sampling.
	 * 
	 */
	public PingJmx getPingerImpl() {
		return pinger;
	}
	
	/**
	 * Create new instance of <code>PingJmx</code>
	 * Override this call to return your instance of pinger
	 *
	 * @param mserver MBean server instance
	 * @param filter MBean filters semicolon separated
	 * @param period time period for sampling
	 * @param tunit time units for period
	 *  
	 * @return new <code>PingJmx</code> instance
	 */
	protected PingJmx newPingJmxImpl(MBeanServer mserver, String filter, long period, TimeUnit tunit) {
		return new PingJmx(this.getClass().getName(), mserver, filter, period, tunit);
	}

	@Override
	public Pinger addListener(SampleListener listener) {
		if (pinger == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		pinger.getConditionalListener().addListener(listener);
		return this;
	}

	@Override
	public Pinger removeListener(SampleListener listener) {
		if (pinger == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		pinger.getConditionalListener().removeListener(listener);
		return this;
	}

	@Override
	public Pinger register(Condition cond, AttributeAction action) {
		if (pinger == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		pinger.register(cond, action);
		return this;
	}

	@Override
	public void run() {
		if (pinger == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		pinger.run();
	}

	@Override
	public SampleStats getStats() {
		if (pinger == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		return pinger.getConditionalListener().getStats();
	}
}
