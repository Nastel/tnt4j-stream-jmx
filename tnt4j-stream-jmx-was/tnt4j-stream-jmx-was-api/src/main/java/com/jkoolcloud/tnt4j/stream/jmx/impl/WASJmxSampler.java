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
package com.jkoolcloud.tnt4j.stream.jmx.impl;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;

import com.ibm.websphere.management.AdminServiceFactory;
import com.ibm.websphere.management.MBeanFactory;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;

/**
 * <p>
 * This class provides scheduled execution and sampling of JMX metrics for a WebSphere Application Server
 * {@link MBeanServer} instance.
 * </p>
 *
 * @version $Revision: 1 $
 * 
 * @see PlatformJmxSampler
 */
public class WASJmxSampler extends PlatformJmxSampler {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(WASJmxSampler.class);

	/**
	 * Dynamically identify and load WebSphere JMX MBean Server
	 * {@code com.ibm.websphere.management.AdminServiceFactory}. Throws exception if WAS specific MBeanServer is not
	 * found.
	 *
	 * @return WebSphere JMX MBean server instance
	 *
	 * @throws Exception
	 *             if exception occurs resolving admin server class or resolving and invoking method
	 */
	public static MBeanServer getAdminServer() throws Exception {
		MBeanFactory mbeanFactory = AdminServiceFactory.getMBeanFactory();
		if (mbeanFactory != null) {
			return mbeanFactory.getMBeanServer();
		}
		throw new RuntimeException("No admin mbeanFactory found: class=" + AdminServiceFactory.class.getName());
	}

	/**
	 * Dynamically identify and load WebSphere JMX MBean Server
	 * {@code com.ibm.websphere.management.AdminServiceFactory}. Use {@link ManagementFactory#getPlatformMBeanServer()}
	 * if none found.
	 *
	 * @return WebSphere JMX MBean server instance
	 */
	public static MBeanServer defaultMBeanServer() {
		try {
			return getAdminServer();
		} catch (Throwable ex) {
			LOGGER.log(OpLevel.ERROR, "Failed to load and initialize WebSphere JMX MBean Server", ex);
		}
		return ManagementFactory.getPlatformMBeanServer();
	}

	/**
	 * Create an instance with WebSphere MBean Server instance.
	 *
	 * @param sFactory
	 *            sampler factory instance
	 */
	protected WASJmxSampler(SamplerFactory sFactory) {
		super(new JMXMBeanServerConnection(defaultMBeanServer()), sFactory);
	}

	/**
	 * Create a default instance with a given MBean server instance.
	 *
	 * @param mServerConn
	 *            MBean server connection instance
	 * @param sFactory
	 *            sampler factory instance
	 */
	protected WASJmxSampler(JMXServerConnection mServerConn, SamplerFactory sFactory) {
		super(mServerConn, sFactory);
	}
}
