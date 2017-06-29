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

import com.ibm.websphere.security.WSSecurityHelper;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.PrivilegedSampleHandlerImpl;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.SampleHandlerImpl;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.WASSampleHandlerImpl;

/**
 * <p>
 * This class provides a {@link SamplerFactory} implementation with {@link WASJmxSampler} as underlying sampler
 * implementation.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see Sampler
 * @see WASJmxSampler
 */
public class WASSamplerFactory implements SamplerFactory {

	private boolean local = false;

	@Override
	public Sampler newInstance() {
		local = true;
		return new WASJmxSampler(this);
	}

	@Override
	public Sampler newInstance(MBeanServerConnection mServerConn) {
		return mServerConn == null ? newInstance() : new WASJmxSampler(mServerConn, this);
	}

	@Override
	public SampleListener newListener(PrintStream pStream, boolean trace) {
		return new WASSampleListener(pStream, trace);
	}

	@Override
	public String defaultEventFormatterClassName() {
		return "com.jkoolcloud.tnt4j.stream.jmx.format.WASFactPathValueFormatter";
	}

	@Override
	public SampleHandler newSampleHandler(MBeanServerConnection mServerConn, String incFilterList,
			String excFilterList) {
		if (local && isServerSecurityEnabled()) {
			return new WASSampleHandlerImpl(mServerConn, incFilterList, excFilterList);
		}

		if (isJavaSecurityEnabled()) {
			return new PrivilegedSampleHandlerImpl(mServerConn, incFilterList, excFilterList);
		} else {
			return new SampleHandlerImpl(mServerConn, incFilterList, excFilterList);
		}
	}

	private static boolean isServerSecurityEnabled() {
		return WSSecurityHelper.isGlobalSecurityEnabled() || WSSecurityHelper.isServerSecurityEnabled();
	}

	private static boolean isJavaSecurityEnabled() {
		return System.getSecurityManager() != null;
	}
}
