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
import java.lang.reflect.Method;

import javax.management.MBeanServer;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * <p>
 * This class provides scheduled execution and sampling of JMX metrics for a JBoss Application Server
 * {@link MBeanServer} instance.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 * @see PlatformJmxSampler
 */
public class JBossJmxSampler extends PlatformJmxSampler {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(JBossJmxSampler.class);

	public static final String JBOSS_JMX_ADMIN_CLASS = "org.jboss.mx.util.MBeanServerLocator";

	/**
	 * Dynamically identify and load JBoss MBean Server {@code org.jboss.mx.util.MBeanServerLocator.locate()}. Throws
	 * exception if JBoss specific MBeanServer is not found.
	 * 
	 * @return JBoss JMX MBean server instance
	 *
	 * @throws Exception
	 *             if exception occurs resolving admin server class or resolving and invoking method
	 */
	protected static MBeanServer getAdminServer() throws Exception {
		Class<?> adminClass = Class.forName(JBOSS_JMX_ADMIN_CLASS);
		Method mserverMethod = adminClass.getMethod("locate", Utils.NO_PARAMS_C);
		if (mserverMethod != null) {
			return (MBeanServer) mserverMethod.invoke(null, Utils.NO_PARAMS_O);
		}
		throw new RuntimeException("No admin mbeanFactory found: class=" + JBOSS_JMX_ADMIN_CLASS);
	}

	/**
	 * Dynamically identify and load JBoss JMX MBean Server {@code org.jboss.mx.util.MBeanServerLocator}. Use
	 * {@link ManagementFactory#getPlatformMBeanServer()} if none found.
	 *
	 * @return JBoss JMX MBean server instance
	 */
	public static MBeanServer defaultMBeanServer() {
		try {
			return getAdminServer();
		} catch (Throwable ex) {
			LOGGER.log(OpLevel.ERROR, "Failed to load and initialize JBoss JMX MBean Server", ex);
		}
		return ManagementFactory.getPlatformMBeanServer();
	}

	/**
	 * Create an instance with JBoss MBean Server instance.
	 *
	 * @param sFactory
	 *            sampler factory instance
	 */
	protected JBossJmxSampler(SamplerFactory sFactory) {
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
	protected JBossJmxSampler(JMXServerConnection mServerConn, SamplerFactory sFactory) {
		super(mServerConn, sFactory);
	}
}
