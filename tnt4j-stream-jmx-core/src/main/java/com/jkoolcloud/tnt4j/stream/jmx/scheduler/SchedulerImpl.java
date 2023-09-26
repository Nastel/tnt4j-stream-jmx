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
package com.jkoolcloud.tnt4j.stream.jmx.scheduler;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.ActivityScheduler;
import com.jkoolcloud.tnt4j.TrackingLogger;
import com.jkoolcloud.tnt4j.config.DefaultConfigFactory;
import com.jkoolcloud.tnt4j.config.TrackerConfig;
import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.core.ActivityListener;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeAction;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeCondition;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;

/**
 * <p>
 * This class provides scheduled sample/heart-beat for a given JMX
 * {@link com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection}.
 * </p>
 * 
 * @version $Revision: 1 $
 */
public class SchedulerImpl extends ActivityScheduler implements Scheduler {
	protected SampleHandler listener;
	protected long initDelay;
	protected long period;
	protected TimeUnit timeUnit;

	protected SampleActivityTask task;

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param handler
	 *            sample handler instance
	 * @param samplerCfg
	 *            sampler configuration map
	 */
	public SchedulerImpl(String name, SampleHandler handler, Map<String, ?> samplerCfg) {
		super(name, handler);
		this.listener = (SampleHandler) this.getListener();
		this.listener.setScheduler(this);
		this.initDelay = ((Number) samplerCfg.get(Sampler.CFG_INITIAL_DELAY)).longValue();
		this.period = ((Number) samplerCfg.get(Sampler.CFG_SAMPLING_PERIOD)).longValue();
		this.timeUnit = (TimeUnit) samplerCfg.get(Sampler.CFG_TIME_UNIT);
	}

	/**
	 * Create new instance of {@code SchedulerImpl} with a given name, MBean server, filter list and sampling period.
	 *
	 * @param name
	 *            name of assigned to the sampler
	 * @param handler
	 *            sample handler instance
	 * @param samplerCfg
	 *            sampler configuration map
	 * @param sFactory
	 *            sampler factory instance
	 */
	public SchedulerImpl(String name, SampleHandler handler, Map<String, ?> samplerCfg, SamplerFactory sFactory) {
		super(name, loadLoggerConfig(name, sFactory, handler));
		this.listener = (SampleHandler) this.getListener();
		this.listener.setScheduler(this);
		this.initDelay = ((Number) samplerCfg.get(Sampler.CFG_INITIAL_DELAY)).longValue();
		this.period = ((Number) samplerCfg.get(Sampler.CFG_SAMPLING_PERIOD)).longValue();
		this.timeUnit = (TimeUnit) samplerCfg.get(Sampler.CFG_TIME_UNIT);
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
	public long getPeriod() {
		return TimeUnit.MILLISECONDS.convert(period, timeUnit);
	}

	@Override
	public void run() {
		this.schedule(this.getName(), initDelay, period, timeUnit);
	}

	@Override
	protected Runnable newActivityTask(TrackingLogger lg, String name, OpLevel level) {
		task = new SampleActivityTask(lg, name, (level == null ? getOpLevel() : level));

		return task;
	}

	/**
	 * Returns activity task bound to this scheduler.
	 * 
	 * @return activity task bound to this scheduler
	 */
	protected SampleActivityTask getActivityTask() {
		return task;
	}

	@Override
	public void close() {
		super.close();
		if (SamplingAgent.getAllSamplers().isEmpty()) {
			TrackingLogger.shutdown(getLogger());
		}

		listener.cleanup();
	}
}
