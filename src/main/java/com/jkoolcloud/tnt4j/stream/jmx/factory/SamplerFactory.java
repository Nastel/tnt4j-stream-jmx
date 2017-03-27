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
package com.jkoolcloud.tnt4j.stream.jmx.factory;

import javax.management.MBeanServerConnection;

import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;

/**
 * <p>
 * This interface defines a way to obtain underlying implementation of the sampler object that actually samples
 * underlying MBeans.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see Sampler
 */
public interface SamplerFactory {

	/**
	 * Create a default instance with default MBean server instance {@code ManagementFactory.getPlatformMBeanServer()}
	 * 
	 * @see Sampler
	 */
	Sampler newInstance();

	/**
	 * Create a default instance with a given MBean server instance
	 * 
	 * @param mServerConn MBean server connection instance
	 * @see Sampler
	 */
	Sampler newInstance(MBeanServerConnection mServerConn);
}
