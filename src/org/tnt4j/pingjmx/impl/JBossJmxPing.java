/*
 * Copyright 2015 JKOOL, LLC.
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
package org.tnt4j.pingjmx.impl;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

import javax.management.MBeanServer;

/**
 * <p> 
 * This class provides scheduled execution and sampling of JMX metrics
 * for a JBoss Application Server <code>MBeanServer</code> instance.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 * @see PlatformJmxPing
 */
public class JBossJmxPing extends PlatformJmxPing {
	public static final String JBOSS_JMX_ADMIN_CLASS = "org.jboss.mx.util.MBeanServerLocator";
	/**
	 * Dynamically identify and load JBoss MBean Server
	 * <code>org.jboss.mx.util.MBeanServerLocator.locate()</code>.
	 * Use <code>ManagementFactory.getPlatformMBeanServer()</code> if none found.
	 * 
	 * @return JBoss JMX MBean server instance
	 */
	protected static MBeanServer getAdminServer()  {
		try {
			Class<?> adminClass = Class.forName(JBOSS_JMX_ADMIN_CLASS);
			Method mserverMethod = adminClass.getMethod("locate", (Class<?>) null);
			if (mserverMethod != null) {
				return (MBeanServer) mserverMethod.invoke(null);			
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return ManagementFactory.getPlatformMBeanServer();
	}

	/**
	 * Create an instance with JBoss MBean Server instance
	 * 
	 */
	protected JBossJmxPing() {
		super(getAdminServer());
	}
	
	/**
	 * Create a default instance with a given MBean server instance
	 * 
	 * @param mserver MBean server instance
	 */
	protected JBossJmxPing(MBeanServer mserver) {
		super(mserver);
	}
}
