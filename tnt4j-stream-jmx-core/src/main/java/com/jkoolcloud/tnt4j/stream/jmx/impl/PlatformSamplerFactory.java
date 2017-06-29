/*
 * Copyright 2015-2017 JKOOL, LLC.
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
package com.jkoolcloud.tnt4j.stream.jmx.impl;

import java.io.PrintStream;

import javax.management.MBeanServerConnection;

import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
import com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.PrivilegedSampleHandlerImpl;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.SampleHandlerImpl;

/**
 * <p>
 * This class provides a {@link SamplerFactory} implementation with {@link PlatformJmxSampler} as underlying sampler
 * implementation.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see Sampler
 * @see PlatformJmxSampler
 */
public class PlatformSamplerFactory implements SamplerFactory {

	@Override
	public Sampler newInstance() {
		return new PlatformJmxSampler(this);
	}

	@Override
	public Sampler newInstance(MBeanServerConnection mServerConn) {
		return mServerConn == null ? newInstance() : new PlatformJmxSampler(mServerConn, this);
	}

	@Override
	public SampleListener newListener(PrintStream pStream, boolean trace) {
		return new DefaultSampleListener(pStream, trace);
	}

	@Override
	public String defaultEventFormatterClassName() {
		return "com.jkoolcloud.tnt4j.stream.jmx.format.FactPathValueFormatter";
	}

	@Override
	public SampleHandler newSampleHandler(MBeanServerConnection mServerConn, String incFilterList,
			String excFilterList) {
		return System.getSecurityManager() == null ? new SampleHandlerImpl(mServerConn, incFilterList, excFilterList)
				: new PrivilegedSampleHandlerImpl(mServerConn, incFilterList, excFilterList);
	}
}
