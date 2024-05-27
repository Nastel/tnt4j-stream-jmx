/*
 * Copyright 2014-2023 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx;

import java.io.IOException;
import java.util.Map;

import javax.management.MBeanInfo;
import javax.management.ObjectName;

import com.jkoolcloud.tnt4j.core.Activity;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.core.Snapshot;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.UnsupportedAttributeException;

/**
 * Sampler failures handling listener.
 * 
 * @version $Revision: 1 $
 */
public class SamplerFailureListener implements SampleListener {

	private final SamplingAgent agent;

	/**
	 * Constructs a new SamplerFailListener.
	 * 
	 * @param agent
	 *            sampling agent instance
	 */
	protected SamplerFailureListener(SamplingAgent agent) {
		this.agent = agent;
	}

	@Override
	public void register(SampleContext context, ObjectName oName) {
	}

	@Override
	public void unregister(SampleContext context, ObjectName oName) {
	}

	@Override
	public void pre(SampleContext context, AttributeSample sample) {
	}

	@Override
	public void post(SampleContext context, AttributeSample sample) throws UnsupportedAttributeException {
	}

	@Override
	public void complete(SampleContext context, Activity activity, ObjectName name, MBeanInfo info, Snapshot snapshot) {
	}

	@Override
	public void error(SampleContext context, AttributeSample sample, OpLevel level) {
	}

	@Override
	public void error(SampleContext context, Throwable ex) {
		if (ex instanceof IOException) {
			agent.stopSampler();
		}
	}

	@Override
	public void pre(SampleContext context, Activity activity) {
	}

	@Override
	public void post(SampleContext context, Activity activity) {
	}

	@Override
	public void getStats(SampleContext context, Map<String, Object> stats) {
	}
}
