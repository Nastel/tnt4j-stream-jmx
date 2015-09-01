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
package org.tnt4j.stream.jmx.impl;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import org.tnt4j.stream.jmx.conditions.AttributeAction;
import org.tnt4j.stream.jmx.conditions.AttributeCondition;
import org.tnt4j.stream.jmx.core.Sampler;
import org.tnt4j.stream.jmx.core.SampleContext;
import org.tnt4j.stream.jmx.core.SampleListener;
import org.tnt4j.stream.jmx.scheduler.Scheduler;
import org.tnt4j.stream.jmx.scheduler.SchedulerImpl;

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
 * @see Sampler
 * @see Scheduler
 * @see SchedulerImpl
 */
public class PlatformJmxSampler implements Sampler {	
	protected Scheduler sampler;
	protected MBeanServer targetServer;
	
	/**
	 * Create a default instance with default MBean server instance
	 * <code>ManagementFactory.getPlatformMBeanServer()</code>
	 * 
	 */
	protected PlatformJmxSampler() {
		this(ManagementFactory.getPlatformMBeanServer());
	}
	
	/**
	 * Create a default instance with a given MBean server instance
	 * 
	 * @param mserver MBean server instance
	 */
	protected PlatformJmxSampler(MBeanServer mserver) {
		targetServer = mserver;
	}
	
	@Override
	public MBeanServer getMBeanServer() {
		return targetServer;
	}
	
	@Override
    public TrackingLogger getLogger() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
	    return sampler.getLogger();
    }

	@Override
	public Sampler setSchedule(long period) throws IOException {
		return setSchedule(JMX_FILTER_ALL, period);
	}

	@Override
	public Sampler setSchedule(String jmxfilter, long period) throws IOException {
		return setSchedule(jmxfilter, period, TimeUnit.MILLISECONDS);
	}	

	@Override
	public synchronized Sampler setSchedule(String jmxfilter, long period, TimeUnit tunit) throws IOException {
		if (sampler == null) {
			sampler = newPingScheduler(getMBeanServer(), jmxfilter, period, tunit);
			sampler.open();
			return this;
		} else {
			throw new IllegalStateException("setSchedule() already called");			
		}
	}

	@Override
	public synchronized void cancel() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		} else {
			sampler.close();
			sampler = null;
		}
	}
		
	/**
	 * Create new instance of <code>Scheduler</code>
	 * Override this call to return your instance of <code>Scheduler</code>.
	 *
	 * @param mserver MBean server instance
	 * @param filter MBean filters semicolon separated
	 * @param period time period for sampling
	 * @param tunit time units for period
	 *  
	 * @return new <code>Scheduler</code> instance
	 */
	protected Scheduler newPingScheduler(MBeanServer mserver, String filter, long period, TimeUnit tunit) {
		return new SchedulerImpl(this.getClass().getName(), mserver, filter, period, tunit);
	}

	@Override
	public Sampler addListener(SampleListener listener) {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		sampler.getSampleHandler().addListener(listener);
		return this;
	}

	@Override
	public Sampler removeListener(SampleListener listener) {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		sampler.getSampleHandler().removeListener(listener);
		return this;
	}

	@Override
	public Sampler register(AttributeCondition cond, AttributeAction action) {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		sampler.register(cond, action);
		return this;
	}

	@Override
	public void run() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		sampler.run();
	}

	@Override
	public SampleContext getContext() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		return sampler.getSampleHandler().getContext();
	}

	@Override
    public String getName() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
	    return sampler.getName();
    }

	@Override
    public String getFilter() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
	    return sampler.getFilter();
    }

	@Override
    public long getPeriod() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
	    return sampler.getPeriod();
    }
}
