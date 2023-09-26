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

import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Map;

import javax.management.AttributeList;

import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;

/**
 * Sample handler extension to be used by Java security enabled JVM and performing attributes sampling by invoking
 * {@link AccessController#doPrivileged(PrivilegedExceptionAction)}.
 *
 * @version $Revision: 1 $
 */
public class PrivilegedSampleHandlerImpl extends SampleHandlerImpl {

	/**
	 * Create new instance of {@code PrivilegedSampleHandlerImpl} with a given MBean server and a set of filters.
	 *
	 * @param config
	 *            configuration map for this sample handler
	 */
	public PrivilegedSampleHandlerImpl(Map<String, ?> config) {
		super(config);
	}

	@Override
	protected AttributeList sample(AttributeSample sample) throws Exception {
		return AccessController.doPrivileged(new SamplePrivilegedAction(sample));
	}

	/**
	 * This class defines privileged exception action to sample MBean attributes.
	 */
	protected static class SamplePrivilegedAction implements PrivilegedExceptionAction<AttributeList> {
		private final AttributeSample sample;

		/**
		 * Create new instance of {@code SamplePrivilegedAction} with a given MBean sample instance.
		 * 
		 * @param sample
		 *            MBean attributes sample instance
		 */
		SamplePrivilegedAction(AttributeSample sample) {
			this.sample = sample;
		}

		@Override
		public AttributeList run() throws Exception {
			return sample.sample();
		}
	}
}
