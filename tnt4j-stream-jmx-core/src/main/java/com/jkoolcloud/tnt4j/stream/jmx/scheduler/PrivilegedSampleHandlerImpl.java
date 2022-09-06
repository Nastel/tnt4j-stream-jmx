/*
 * Copyright 2015-2022 JKOOL, LLC.
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

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

import javax.management.MBeanServerConnection;

import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;

/**
 * Sample handler extension to be used by Java security enabled JVM and performing attributes sampling by invoking
 * {@link AccessController#doPrivileged(PrivilegedExceptionAction)}.
 *
 * @version $Revision: 1 $
 */
public class PrivilegedSampleHandlerImpl extends SampleHandlerImpl {

	public PrivilegedSampleHandlerImpl(MBeanServerConnection mServerConn, String incFilter, String excFilter,
			Source source) {
		super(mServerConn, incFilter, excFilter, source);
	}

	@Override
	protected Object sample(AttributeSample sample) throws Exception {
		return AccessController.doPrivileged(new SamplePrivilegedAction(sample));
	}

	private static class SamplePrivilegedAction implements PrivilegedExceptionAction<Object> {
		private final AttributeSample sample;

		SamplePrivilegedAction(AttributeSample sample) {
			this.sample = sample;
		}

		@Override
		public Object run() throws Exception {
			return sample.sample();
		}
	}
}
