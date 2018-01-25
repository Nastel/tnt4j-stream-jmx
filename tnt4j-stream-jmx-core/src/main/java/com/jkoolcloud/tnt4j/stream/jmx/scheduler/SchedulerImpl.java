/*
 * Copyright 2015-2018 JKOOL, LLC.
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

import javax.management.MBeanServerConnection;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.ActivityScheduler;
import com.jkoolcloud.tnt4j.config.DefaultConfigFactory;
import com.jkoolcloud.tnt4j.config.TrackerConfig;
import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.core.ActivityListener;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeAction;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeCondition;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;

/**
 * <p>
 * This class provides scheduled sample/heart-beat for a given JMX {@link MBeanServerConnection}.
 * </p>
 * 
 * @version $Revision: 1 $
 */
public class SchedulerImpl extends ActivityScheduler implements Scheduler {
	protected SampleHandler listener;
	protected long initDelay;
	protected long period;
	protected TimeUnit timeUnit;
	protected String incFilter;
	protected String excFilter;

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean server, sampling period. Filter is set to
	 * all MBeans.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param handler
	 *            sample handler instance
	 * @param period
	 *            sampling period in milliseconds
	 */
	public SchedulerImpl(String name, SampleHandler handler, long period) {
		this(name, handler, Sampler.JMX_FILTER_ALL, period);
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param handler
	 *            sample handler instance
	 * @param incFilterList
	 *            MBean filters semicolon separated
	 * @param period
	 *            sampling period in milliseconds
	 */
	public SchedulerImpl(String name, SampleHandler handler, String incFilterList, long period) {
		this(name, handler, incFilterList, null, period, TimeUnit.MILLISECONDS);
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param handler
	 *            sample handler instance
	 * @param incFilterList
	 *            MBean filters semicolon separated
	 * @param period
	 *            sampling period
	 * @param tUnit
	 *            time unit for the sampling period
	 */
	public SchedulerImpl(String name, SampleHandler handler, String incFilterList, long period, TimeUnit tUnit) {
		this(name, handler, incFilterList, null, period, tUnit);
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param handler
	 *            sample handler instance
	 * @param incFilterList
	 *            MBean include filters semicolon separated
	 * @param excFilterList
	 *            MBean exclude filters semicolon separated
	 * @param period
	 *            sampling period
	 * @param tUnit
	 *            time unit for the sampling period
	 */
	public SchedulerImpl(String name, SampleHandler handler, String incFilterList, String excFilterList, long period,
			TimeUnit tUnit) {
		this(name, handler, incFilterList, excFilterList, 0, period, tUnit);
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param handler
	 *            sample handler instance
	 * @param incFilterList
	 *            MBean include filters semicolon separated
	 * @param excFilterList
	 *            MBean exclude filters semicolon separated
	 * @param initDelay
	 *            initial delay before first sampling
	 * @param period
	 *            sampling period
	 * @param tUnit
	 *            time unit for the sampling period
	 */
	public SchedulerImpl(String name, SampleHandler handler, String incFilterList, String excFilterList, long initDelay,
			long period, TimeUnit tUnit) {
		super(name, handler);
		this.listener = (SampleHandler) this.getListener();
		this.initDelay = initDelay;
		this.period = period;
		this.timeUnit = tUnit;
		this.incFilter = incFilterList;
		this.excFilter = excFilterList;
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param handler
	 *            sample handler instance
	 * @param incFilterList
	 *            MBean include filters semicolon separated
	 * @param excFilterList
	 *            MBean exclude filters semicolon separated
	 * @param initDelay
	 *            initial delay before first sampling
	 * @param period
	 *            sampling period
	 * @param tUnit
	 *            time unit for the sampling period
	 * @param sFactory
	 *            sampler factory instance
	 */
	public SchedulerImpl(String name, SampleHandler handler, String incFilterList, String excFilterList, long initDelay,
			long period, TimeUnit tUnit, SamplerFactory sFactory) {
		super(name, loadLoggerConfig(name, sFactory, handler));
		this.listener = (SampleHandler) this.getListener();
		this.initDelay = initDelay;
		this.period = period;
		this.timeUnit = tUnit;
		this.incFilter = incFilterList;
		this.excFilter = excFilterList;
	}

	/**
	 * Loads and sets up instance of {@link TrackerConfig} to be used to create scheduler logger.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param sFactory
	 *            sampler factory instance
	 * @param listener
	 *            activity listener invoked when scheduled activity starts and stops
	 * @return tracker configuration used to create scheduler logger
	 */
	protected static TrackerConfig loadLoggerConfig(String name, SamplerFactory sFactory, ActivityListener listener) {
		TrackerConfig config = DefaultConfigFactory.getInstance().getConfig(name);

		if (sFactory != null && StringUtils.isEmpty(config.getProperty("event.formatter"))) {
			config.setProperty("event.formatter", sFactory.defaultEventFormatterClassName());
			((TrackerConfigStore) config).applyProperties();
		}

		if (listener != null) {
			config.setActivityListener(listener);
		}

		return config;
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
		this.schedule(this.getName(), initDelay, period, timeUnit);
	}

	@Override
	public void close() {
		super.close();

		// TrackingLogger.shutdown(getLogger());
		listener.cleanup();
	}
}
