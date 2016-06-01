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
package com.jkoolcloud.tnt4j.stream.jmx.scheduler;

import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeAction;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeCondition;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;

import com.jkoolcloud.tnt4j.ActivityScheduler;

/**
 * <p>
 * This class provides scheduled sample/heart-beat for a given JMX {@code MBeanServer}.
 * </p>
 * 
 * @version $Revision: 1 $
 */
public class SchedulerImpl extends ActivityScheduler implements Scheduler {
	protected SampleHandler listener;
	protected long period;
	protected TimeUnit timeUnit;
	protected String incFilter;
	protected String excFilter;

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean
	 * server, sampling period. Filter is set to all MBeans.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param server
	 *            MBean server instance
	 * @param period
	 *            sampling period in milliseconds
	 * 
	 */
	public SchedulerImpl(String name, MBeanServer server, long period) {
		this(name, server, Sampler.JMX_FILTER_ALL, period);
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean
	 * server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param server
	 *            MBean server instance
	 * @param filterList
	 *            MBean filters semicolon separated
	 * @param period
	 *            sampling period in milliseconds
	 * 
	 */
	public SchedulerImpl(String name, MBeanServer server, String filterList,
			long period) {
		this(name, server, filterList, null, period, TimeUnit.MILLISECONDS);
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean
	 * server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param server
	 *            MBean server instance
	 * @param incFilterList
	 *            MBean filters semicolon separated
	 * @param period
	 *            sampling period
	 * @param tunit
	 *            time unit for the sampling period
	 * 
	 */
	public SchedulerImpl(String name, MBeanServer server, 
			String incFilterList,
			long period, TimeUnit tunit) {
		this(name, server, incFilterList, null, period, tunit);		
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean
	 * server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param server
	 *            MBean server instance
	 * @param incFilterList
	 *            MBean filters semicolon separated
	 * @param excFilterList
	 *            MBean filters semicolon separated
	 * @param period
	 *            sampling period
	 * @param tunit
	 *            time unit for the sampling period
	 * 
	 */
	public SchedulerImpl(String name, MBeanServer server, 
			String incFilterList,
			String excFilterList,
			long period, TimeUnit tunit) {
		super(name, newSampleHandlerImpl(server, incFilterList, excFilterList));
		this.listener = (SampleHandler) this.getListener();
		this.period = period;
		this.timeUnit = tunit;
		this.incFilter = incFilterList;
		this.excFilter = excFilterList;
	}

	/**
	 * Create new instance of {@code SampleHandler}. Override this call
	 * to return your instance of the sample handler implementation.
	 *
	 * @param server
	 *            MBean server instance
	 * @param filterList
	 *            MBean filters semicolon separated
	 * 
	 * @see SampleHandler
	 * @return new sample handler implementation instance
	 */
	protected static SampleHandler newSampleHandlerImpl(MBeanServer server,
			String filterList, String excfilterList) {
		return new SampleHandlerImpl(server, filterList, excfilterList);
	}

	@Override
	public void register(AttributeCondition cond, AttributeAction action) {
		listener.register(cond, action);
	}

	@Override
	public SampleHandler getSampleHandler() {
		return listener;
	}

	@Override
	public String getIncFilter() {
		return incFilter;
	}
	
	@Override
	public String getExcFilter() {
		return excFilter;
	}
	
	@Override
	public long getPeriod() {
		return TimeUnit.MILLISECONDS.convert(period, timeUnit);
	}
	
	@Override
	public void run() {
		this.schedule(this.getName(), period, timeUnit);
	}	
}
