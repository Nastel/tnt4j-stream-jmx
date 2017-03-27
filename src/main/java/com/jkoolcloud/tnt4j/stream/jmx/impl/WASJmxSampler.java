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

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;

/**
 * <p>
 * This class provides scheduled execution and sampling of JMX metrics for a WebSphere Application Server
 * {@code MBeanServer} instance.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 * @see PlatformJmxSampler
 */
public class WASJmxSampler extends PlatformJmxSampler {
	public static final String WAS_JMX_ADMIN_CLASS = "com.ibm.websphere.management.AdminServiceFactory";

	/**
	 * Dynamically identify and load WebSphere JMX MBean Server
	 * {@code com.ibm.websphere.management.AdminServiceFactory}. Throws exception if WAS specific MBeanServer is not
	 * found.
	 * 
	 * @return WebSphere JMX MBean server instance
	 * @throws ClassNotFoundException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 */
	public static MBeanServer getAdminServer() throws ClassNotFoundException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		Class<?> adminClass = Class.forName(WAS_JMX_ADMIN_CLASS);
		Method getMbeanFactory = adminClass.getDeclaredMethod("getMBeanFactory", (Class<?>) null);
		Object mbeanFactory = getMbeanFactory.invoke(null);
		if (mbeanFactory != null) {
			Method mserverMethod = mbeanFactory.getClass().getMethod("getMBeanServer", (Class<?>) null);
			return (MBeanServer) mserverMethod.invoke(mbeanFactory);
		}
		throw new RuntimeException("No admin mbeanFactory found: class=" + WAS_JMX_ADMIN_CLASS);
	}

	/**
	 * Dynamically identify and load WebSphere JMX MBean Server
	 * {@code com.ibm.websphere.management.AdminServiceFactory}. Use {@code ManagementFactory.getPlatformMBeanServer()}
	 * if none found.
	 * 
	 * @return WebSphere JMX MBean server instance
	 */
	public static MBeanServer defaultMBeanServer() {
		try {
			return getAdminServer();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return ManagementFactory.getPlatformMBeanServer();
	}

	/**
	 * Create an instance with WebSphere MBean Server instance
	 */
	protected WASJmxSampler() {
		super(defaultMBeanServer());
	}

	/**
	 * Create a default instance with a given MBean server instance
	 * 
	 * @param mServerConn MBean server connection instance
	 */
	protected WASJmxSampler(MBeanServerConnection mServerConn) {
		super(mServerConn);
	}
}
