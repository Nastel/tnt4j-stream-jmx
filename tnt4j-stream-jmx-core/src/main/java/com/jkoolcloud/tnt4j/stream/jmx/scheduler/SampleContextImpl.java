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

import javax.management.MBeanServerConnection;

import com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext;

/**
 * <p>
 * This class provides implementation of {@link SampleContext} and used by {@link SampleHandlerImpl}.
 * </p>
 * 
 * @see SampleContext
 * @see SampleContextImpl
 * 
 * @version $Revision: 1 $
 */
public class SampleContextImpl implements SampleContext {
	final SampleHandlerImpl handle;

	protected SampleContextImpl(SampleHandlerImpl lst) {
		handle = lst;
	}

	@Override
	public MBeanServerConnection getMBeanServer() {
		return handle.mbeanServer;
	}

	@Override
	public long getSampleCount() {
		return handle.sampleCount;
	}

	@Override
	public long getMBeanCount() {
		return handle.mbeans.size();
	}

	@Override
	public long getExcludeAttrCount() {
		return handle.excCount;
	}

	@Override
	public long getTotalMetricCount() {
		return handle.totalMetricCount;
	}

	@Override
	public long getLastMetricCount() {
		return handle.lastMetricCount;
	}

	@Override
	public long getTotalNoopCount() {
		return handle.noopCount;
	}

	@Override
	public Throwable getLastError() {
		return handle.lastError;
	}

	@Override
	public long getLastSampleUsec() {
		return handle.lastSampleTimeUsec;
	}

	@Override
	public SampleContext resetCounters() {
		handle.resetCounters();
		return this;
	}

	@Override
	public long getTotalErrorCount() {
		return handle.errorCount;
	}

	@Override
	public String toString() {
		return handle.mbeanServer.hashCode() + "=>" + Utils.toString(handle.serviceConn);
	}
}