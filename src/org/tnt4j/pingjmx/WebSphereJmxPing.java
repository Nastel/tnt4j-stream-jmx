/*
 * Copyright 2015 Nastel Technologies, Inc.
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
package org.tnt4j.pingjmx;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;

import javax.management.MBeanServer;

/**
 * <p> 
 * This class provides scheduled execution and sampling of JMX metrics
 * for a WebSphere Application Server <code>MBeanServer</code> instance.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 * @see PlatformJmxPing
 */
public class WebSphereJmxPing extends PlatformJmxPing {
	public static final String WAS_JMX_ADMIN_CLASS = "com.ibm.websphere.management.AdminServiceFactory";
	
	protected static MBeanServer getWasAdminServer()  {
		try {
			Class<?> adminClass = Class.forName(WAS_JMX_ADMIN_CLASS);
			Method getMbeanFactory = adminClass.getDeclaredMethod("getMBeanFactory", (Class<?>) null);
			Object mbeanFactory = getMbeanFactory.invoke(null);
			if (mbeanFactory != null) {
				Method mserverMethod = mbeanFactory.getClass().getMethod("getMBeanServer", (Class<?>) null);
				return (MBeanServer) mserverMethod.invoke(mbeanFactory);			
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
		return ManagementFactory.getPlatformMBeanServer();
	}
	
	public WebSphereJmxPing() {
		super(getWasAdminServer());
	}
}
