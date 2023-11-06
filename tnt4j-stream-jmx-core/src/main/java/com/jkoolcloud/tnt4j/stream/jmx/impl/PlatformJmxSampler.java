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
import java.util.Map;

import com.jkoolcloud.tnt4j.TrackingLogger;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeAction;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeCondition;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
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
	private Scheduler scheduler;
	private final JMXServerConnection targetServer;
	private final SamplerFactory sFactory;

	private final Object schedulerLock = new Object();

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
		synchronized (schedulerLock) {
			if (scheduler == null) {
				throw new IllegalStateException("no schedule set: call setSchedule() first");
			}
			return scheduler.getLogger();
		}
	}

	@Override
	public Sampler setSchedule(Map<String, Object> samplerCfg) throws IOException {
		synchronized (schedulerLock) {
			if (scheduler == null) {
				samplerCfg.put(SampleHandler.CFG_MBEAN_SERVER, targetServer);
				samplerCfg.put(Sampler.CFG_SAMPLER_FACTORY, sFactory);
				scheduler = newScheduler(samplerCfg);
				scheduler.open();
				return this;
			} else {
				throw new IllegalStateException("setSchedule() already called");
			}
		}
	}

	@Override
	public void cancel() {
		synchronized (schedulerLock) {
			if (scheduler != null) {
				scheduler.close();
			}
		}
	}

	/**
	 * Create new instance of {@link Scheduler}. Override this call to return your instance of {@link Scheduler}.
	 *
	 * @param samplerCfg
	 *            sampler configuration map
	 * @return new {@link Scheduler} instance
	 */
	protected Scheduler newScheduler(Map<String, ?> samplerCfg) {
		SchedulerImpl scheduler = new SchedulerImpl(getClass().getName(),
				sFactory == null ? null : sFactory.newSampleHandler(samplerCfg), samplerCfg, sFactory);
		return scheduler;
	}

	@Override
	public Sampler addListener(SampleListener listener) {
		synchronized (schedulerLock) {
			if (scheduler == null) {
				throw new IllegalStateException("no schedule set: call setSchedule() first");
			}
			scheduler.getSampleHandler().addListener(listener);
			return this;
		}
	}

	@Override
	public Sampler removeListener(SampleListener listener) {
		synchronized (schedulerLock) {
			if (scheduler == null) {
				throw new IllegalStateException("no schedule set: call setSchedule() first");
			}
			scheduler.getSampleHandler().removeListener(listener);
			return this;
		}
	}

	@Override
	public Sampler register(AttributeCondition cond, AttributeAction action) {
		synchronized (schedulerLock) {
			if (scheduler == null) {
				throw new IllegalStateException("no schedule set: call setSchedule() first");
			}
			scheduler.register(cond, action);
			return this;
		}
	}

	@Override
	public void run() {
		synchronized (schedulerLock) {
			if (scheduler == null) {
				throw new IllegalStateException("no schedule set: call setSchedule() first");
			}
			scheduler.run();
		}
	}

	@Override
	public SampleContext getContext() {
		synchronized (schedulerLock) {
			if (scheduler == null) {
				throw new IllegalStateException("no schedule set: call setSchedule() first");
			}
			return scheduler.getSampleHandler().getContext();
		}
	}

	@Override
	public String getName() {
		synchronized (schedulerLock) {
			if (scheduler == null) {
				throw new IllegalStateException("no schedule set: call setSchedule() first");
			}
			return scheduler.getName();
		}
	}

	@Override
	public long getPeriod() {
		synchronized (schedulerLock) {
			if (scheduler == null) {
				throw new IllegalStateException("no schedule set: call setSchedule() first");
			}
			return scheduler.getPeriod();
		}
	}
}
