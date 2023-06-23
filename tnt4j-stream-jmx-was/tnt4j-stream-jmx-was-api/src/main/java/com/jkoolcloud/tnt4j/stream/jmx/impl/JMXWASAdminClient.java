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

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.exception.ConnectorException;
import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;

/**
 * This class wraps {@link AdminClient} instance to obtain MBeans data from MBean server.
 *
 * @version $Revision: 1 $
 */
public class JMXWASAdminClient implements JMXServerConnection {
	final private AdminClient aClient;

	public JMXWASAdminClient(AdminClient aClient) {
		this.aClient = aClient;
	}

	@Override
	public void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
			Object handback) throws InstanceNotFoundException, IOException, ConnectorException {
		aClient.addNotificationListener(name, listener, filter, handback);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws IOException, ConnectorException {
		return aClient.queryNames(name, query);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws IOException, ConnectorException {
		return aClient.queryMBeans(name, query);
	}

	@Override
	public MBeanInfo getMBeanInfo(ObjectName name) throws InstanceNotFoundException, IntrospectionException,
			ReflectionException, IOException, ConnectorException {
		return aClient.getMBeanInfo(name);
	}

	@Override
	public Object getAttribute(ObjectName name, String attribute) throws MBeanException, AttributeNotFoundException,
			InstanceNotFoundException, ReflectionException, IOException, ConnectorException {
		return aClient.getAttribute(name, attribute);
	}

	@Override
	public AttributeList getAttributes(ObjectName name, String[] attributes)
			throws InstanceNotFoundException, ReflectionException, IOException, ConnectorException {
		return aClient.getAttributes(name, attributes);
	}

	@Override
	public Integer getMBeanCount() throws IOException, ConnectorException {
		return aClient.getMBeanCount();
	}

	@Override
	public Object invoke(ObjectName name, String operationName, Object[] params, String[] signature)
			throws InstanceNotFoundException, MBeanException, ReflectionException, IOException, ConnectorException {
		return aClient.invoke(name, operationName, params, signature);
	}

	@Override
	public String toString() {
		return aClient.toString();
	}
}
