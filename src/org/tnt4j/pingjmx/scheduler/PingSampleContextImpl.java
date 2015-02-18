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
package org.tnt4j.pingjmx.scheduler;

import javax.management.MBeanServer;

import org.tnt4j.pingjmx.core.SampleContext;

/**
 * <p> 
 * This class provides implementation of {@link SampleContext} and used by {@link PingSampleHandlerImpl}.
 * </p>
 * 
 * @see SampleContext
 * @see PingSampleContextImpl
 * 
 * @version $Revision: 1 $
 */
public class PingSampleContextImpl implements SampleContext {
	PingSampleHandlerImpl handle;
	
	protected PingSampleContextImpl(PingSampleHandlerImpl lst) {
		handle = lst;
	}
	
	@Override
	public MBeanServer getMBeanServer() {
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
	public long getExclAttrCount() {
		return handle.excAttrs.size();
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
}