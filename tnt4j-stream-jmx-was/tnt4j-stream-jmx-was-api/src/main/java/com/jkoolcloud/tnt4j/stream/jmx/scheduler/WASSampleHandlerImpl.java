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

import java.security.PrivilegedExceptionAction;

import javax.management.AttributeList;
import javax.management.MBeanServerConnection;

import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;
import com.jkoolcloud.tnt4j.stream.jmx.utils.WASSecurityHelper;

/**
 * Sample handler extension to be used by WebSphere Application Server having security enabled and performing JMX
 * attributes sampling by invoking {@link WASSecurityHelper#doPrivilegedAction(PrivilegedExceptionAction)} using
 * {@code com.ibm.websphere.security.auth.WSSubject.getRunAsSubject()} as security subject.
 * 
 * @version $Revision: 1 $
 */
public class WASSampleHandlerImpl extends PrivilegedSampleHandlerImpl {

	/**
	 * Create new instance of {@code WASSampleHandlerImpl} with a given MBean server and a set of filters.
	 *
	 * @param mServerConn
	 *            MBean server connection instance
	 * @param incFilter
	 *            MBean include filters semicolon separated
	 * @param excFilter
	 *            MBean exclude filters semicolon separated
	 * @param source
	 *            sampler source
	 */
	public WASSampleHandlerImpl(MBeanServerConnection mServerConn, String incFilter, String excFilter, Source source) {
		super(mServerConn, incFilter, excFilter, source);
	}

	@Override
	protected AttributeList sample(AttributeSample sample) throws Exception {
		return WASSecurityHelper.doPrivilegedAction(new SamplePrivilegedAction(sample));
	}
}
