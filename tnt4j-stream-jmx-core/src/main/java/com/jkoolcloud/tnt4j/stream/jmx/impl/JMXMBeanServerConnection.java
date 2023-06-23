/*
 * Copyright 2014-2023 JKOOL, LLC.
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

import java.io.IOException;
import java.util.Set;

import javax.management.*;

import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;

/**
 * This class wraps {@link javax.management.MBeanServerConnection} instance to obtain MBeans data from MBean server.
 *
 * @version $Revision: 1 $
 */
public class JMXMBeanServerConnection implements JMXServerConnection {

	final private MBeanServerConnection conn;

	public JMXMBeanServerConnection(MBeanServerConnection conn) {
		this.conn = conn;
	}

	@Override
	public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
			Object handback) throws InstanceNotFoundException, IOException {
		conn.addNotificationListener(name, listener, filter, handback);
	}

	@Override
	public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException {
		return conn.queryNames(name, query);
	}

	@Override
	public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws IOException {
		return conn.queryMBeans(name, query);
	}

	@Override
	public MBeanInfo getMBeanInfo(ObjectName name)
			throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
		return conn.getMBeanInfo(name);
	}

	@Override
	public Object getAttribute(ObjectName name, String attribute) throws MBeanException, AttributeNotFoundException,
			InstanceNotFoundException, ReflectionException, IOException {
		return conn.getAttribute(name, attribute);
	}

	@Override
	public AttributeList getAttributes(ObjectName name, String[] attributes)
			throws InstanceNotFoundException, ReflectionException, IOException {
		return conn.getAttributes(name, attributes);
	}

	@Override
	public Integer getMBeanCount() throws IOException {
		return conn.getMBeanCount();
	}

	@Override
	public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
			throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		return conn.invoke(name, operationName, params, signature);
	}

	@Override
	public String toString() {
		return conn.toString();
	}
}
