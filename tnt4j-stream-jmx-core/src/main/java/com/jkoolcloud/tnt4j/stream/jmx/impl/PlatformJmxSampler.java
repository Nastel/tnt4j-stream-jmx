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
package com.jkoolcloud.tnt4j.stream.jmx.impl;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import com.jkoolcloud.tnt4j.TrackingLogger;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeAction;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeCondition;
import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.Scheduler;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.SchedulerImpl;

/**
 * <p>
 * This class provides scheduled execution and sampling of a JMX metrics for a given
 * {@link com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection} instance. By default, the class will use
 * {@link ManagementFactory#getPlatformMBeanServer()} instance.
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
	protected JMXServerConnection targetServer;
	protected SamplerFactory sFactory;

	/**
	 * Create a default instance with default MBean server instance {@link ManagementFactory#getPlatformMBeanServer()}
	 *
	 * @param sFactory
	 *            sampler factory instance
	 */
	protected PlatformJmxSampler(SamplerFactory sFactory) {
		this(new JMXMBeanServerConnection(ManagementFactory.getPlatformMBeanServer()), sFactory);
	}

	/**
	 * Create a default instance with a given MBean server connection instance
	 * 
	 * @param mServerConn
	 *            MBean server connection instance
	 * @param sFactory
	 *            sampler factory instance
	 */
	protected PlatformJmxSampler(JMXServerConnection mServerConn, SamplerFactory sFactory) {
		targetServer = mServerConn;
		this.sFactory = sFactory;
	}

	@Override
	public JMXServerConnection getMBeanServer() {
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
	public Sampler setSchedule(String incFilter, long period) throws IOException {
		return setSchedule(incFilter, null, period);
	}

	@Override
	public Sampler setSchedule(String incFilter, String excFilter, long period) throws IOException {
		return setSchedule(incFilter, excFilter, period, TimeUnit.MILLISECONDS);
	}

	@Override
	public Sampler setSchedule(String incFilter, String excFilter, long period, TimeUnit tUnit) throws IOException {
		return setSchedule(incFilter, excFilter, period, period, tUnit);
	}

	@Override
	public Sampler setSchedule(String incFilter, String excFilter, long initDelay, long period, TimeUnit tUnit)
			throws IOException {
		return setSchedule(incFilter, excFilter, initDelay, period, tUnit, null);
	}

	@Override
	public Sampler setSchedule(String incFilter, String excFilter, long initDelay, long period, TimeUnit tUnit,
			SamplerFactory sFactory) throws IOException {
		return setSchedule(incFilter, excFilter, initDelay, period, tUnit, sFactory, null);
	}

	@Override
	public Sampler setSchedule(String incFilter, String excFilter, long initDelay, long period, TimeUnit tUnit,
			SamplerFactory sFactory, Source source) throws IOException {
		if (sampler == null) {
			sampler = newScheduler(targetServer, incFilter, excFilter, initDelay, period, tUnit, sFactory, source);
			sampler.open();
			return this;
		} else {
			throw new IllegalStateException("setSchedule() already called");
		}
	}

	@Override
	public synchronized void cancel() {
		if (sampler != null) {
			sampler.close();
			sampler = null;
		}
	}

	/**
	 * Create new instance of {@link Scheduler}. Override this call to return your instance of {@link Scheduler}.
	 *
	 * @param mServerConn
	 *            MBean server connection instance
	 * @param incFilter
	 *            MBean include filters semicolon separated
	 * @param excFilter
	 *            MBean exclude filters semicolon separated
	 * @param period
	 *            time period for sampling
	 * @param tUnit
	 *            time units for period
	 * 
	 * @return new {@link Scheduler} instance
	 */
	protected Scheduler newScheduler(JMXServerConnection mServerConn, String incFilter, String excFilter, long period,
			TimeUnit tUnit) {
		return newScheduler(mServerConn, incFilter, excFilter, 0, period, tUnit);
	}

	/**
	 * Create new instance of {@link Scheduler}. Override this call to return your instance of {@link Scheduler}.
	 *
	 * @param mServerConn
	 *            MBean server connection instance
	 * @param incFilter
	 *            MBean include filters semicolon separated
	 * @param excFilter
	 *            MBean exclude filters semicolon separated
	 * @param initDelay
	 *            initial delay before first sampling
	 * @param period
	 *            time period for sampling
	 * @param tUnit
	 *            time units for period
	 *
	 * @return new {@link Scheduler} instance
	 */
	protected Scheduler newScheduler(JMXServerConnection mServerConn, String incFilter, String excFilter,
			long initDelay, long period, TimeUnit tUnit) {
		return new SchedulerImpl(this.getClass().getName(),
				sFactory.newSampleHandler(mServerConn, incFilter, excFilter), incFilter, excFilter, initDelay, period,
				tUnit);
	}

	/**
	 * Create new instance of {@link Scheduler}. Override this call to return your instance of {@link Scheduler}.
	 *
	 * @param mServerConn
	 *            MBean server connection instance
	 * @param incFilter
	 *            MBean include filters semicolon separated
	 * @param excFilter
	 *            MBean exclude filters semicolon separated
	 * @param initDelay
	 *            initial delay before first sampling
	 * @param period
	 *            time period for sampling
	 * @param tUnit
	 *            time units for period
	 * @param sFactory
	 *            sampler factory instance
	 * @return new {@link Scheduler} instance
	 */
	protected Scheduler newScheduler(JMXServerConnection mServerConn, String incFilter, String excFilter,
			long initDelay, long period, TimeUnit tUnit, SamplerFactory sFactory) {
		return newScheduler(mServerConn, incFilter, excFilter, initDelay, period, tUnit, sFactory, null);
	}

	/**
	 * Create new instance of {@link Scheduler}. Override this call to return your instance of {@link Scheduler}.
	 *
	 * @param mServerConn
	 *            MBean server connection instance
	 * @param incFilter
	 *            MBean include filters semicolon separated
	 * @param excFilter
	 *            MBean exclude filters semicolon separated
	 * @param initDelay
	 *            initial delay before first sampling
	 * @param period
	 *            time period for sampling
	 * @param tUnit
	 *            time units for period
	 * @param sFactory
	 *            sampler factory instance
	 * @param source
	 *            sample source
	 * @return new {@link Scheduler} instance
	 */
	protected Scheduler newScheduler(JMXServerConnection mServerConn, String incFilter, String excFilter,
			long initDelay, long period, TimeUnit tUnit, SamplerFactory sFactory, Source source) {
		SchedulerImpl scheduler = new SchedulerImpl(getClass().getName(),
				sFactory == null ? null : sFactory.newSampleHandler(mServerConn, incFilter, excFilter, source),
				incFilter, excFilter, initDelay, period, tUnit, sFactory);
		return scheduler;
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
	public String getIncFilter() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		return sampler.getIncFilter();
	}

	@Override
	public String getExcFilter() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		return sampler.getExcFilter();
	}

	@Override
	public long getPeriod() {
		if (sampler == null) {
			throw new IllegalStateException("no schedule set: call setSchedule() first");
		}
		return sampler.getPeriod();
	}
}
