/*
 * Copyright 2014-2017 JKOOL, LLC.
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

import java.security.PrivilegedAction;

import javax.management.MBeanServerConnection;

import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;
import com.jkoolcloud.tnt4j.stream.jmx.utils.WASSecurityHelper;

/**
 * Sample handler extension to be used by WebSphere Application Server and performing attributes sampling by invoking
 * {@link WASSecurityHelper#doPrivilegedAction(PrivilegedAction)} using
 * {@link com.ibm.websphere.security.auth.WSSubject#getRunAsSubject()} as security subject.
 * 
 * @version $Revision: 1 $
 */
public class WASSampleHandlerImpl extends SampleHandlerImpl {
	public WASSampleHandlerImpl(MBeanServerConnection mServerConn, String incFilter, String excFilter) {
		super(mServerConn, incFilter, excFilter);
	}

	@Override
	protected Object sample(final AttributeSample sample) throws Exception {
		return WASSecurityHelper.doPrivilegedAction(new SamplePrivilegedAction(sample));
	}

	private static class SamplePrivilegedAction implements PrivilegedAction<Object> {
		private final AttributeSample sample;

		public SamplePrivilegedAction(AttributeSample sample) {
			this.sample = sample;
		}

		@Override
		public Object run() {
			try {
				return sample.sample();
			} catch (Exception exc) {
				throw new RuntimeException("JMX attribute sampling failed", exc);
			}
		}
	}
}
