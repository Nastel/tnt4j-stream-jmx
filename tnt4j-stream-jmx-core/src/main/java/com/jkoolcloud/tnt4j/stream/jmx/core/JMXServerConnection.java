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

package com.jkoolcloud.tnt4j.stream.jmx.core;

import java.util.Set;

import javax.management.*;

/**
 * This interface provides a way obtain MBeans data from MBean server.
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.impl.JMXMBeanServerConnection
 *
 * @version $Revision: 1 $
 */
public interface JMXServerConnection {

	/**
	 * Adds a listener to a registered MBean. Notifications emitted by the MBean will be forwarded to the listener.
	 *
	 * @param name
	 *            The name of the MBean on which the listener should be added.
	 * @param listener
	 *            The listener object which will handle the notifications emitted by the registered MBean.
	 * @param filter
	 *            The filter object. If filter is null, no filtering will be performed before handling notifications.
	 * @param handback
	 *            The context to be sent to the listener when a notification is emitted.
	 *
	 * @exception java.lang.Exception
	 *                when problem occurred when talking to the MBean server
	 */
	void addNotificationListener(ObjectName name, NotificationListener listener, NotificationFilter filter,
			Object handback) throws Exception;

	/**
	 * Gets the names of MBeans controlled by the MBean server.
	 *
	 * @param name
	 *            The object name pattern identifying the MBean names to be retrieved. If null or no domain and key
	 *            properties are specified, the name of all registered MBeans will be retrieved.
	 * @param query
	 *            The query expression to be applied for selecting MBeans. If null no query expression will be applied
	 *            for selecting MBeans.
	 *
	 * @return A set containing the ObjectNames for the MBeans selected. If no MBean satisfies the query, an empty list
	 *         is returned.
	 *
	 * @exception java.lang.Exception
	 *                when problem occurred when talking to the MBean server
	 */
	Set<ObjectName> queryNames(ObjectName name, QueryExp query) throws Exception;

	/**
	 * Gets MBeans controlled by the MBean server.
	 *
	 * @param name
	 *            The object name pattern identifying the MBeans to be retrieved. If null or no domain and key
	 *            properties are specified, all the MBeans registered will be retrieved.
	 * @param query
	 *            The query expression to be applied for selecting MBeans. If null no query expression will be applied
	 *            for selecting MBeans.
	 *
	 * @return A set containing the {@code ObjectInstance} objects for the selected MBeans. If no MBean satisfies the
	 *         query an empty list is returned.
	 *
	 * @exception java.lang.Exception
	 *                when problem occurred when talking to the MBean server
	 */
	Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) throws Exception;

	/**
	 * This method discovers the attributes and operations that an MBean exposes for management.
	 *
	 * @param name
	 *            The name of the MBean to analyze
	 *
	 * @return An instance of {@code MBeanInfo} allowing the retrieval of all attributes and operations of this MBean.
	 *
	 * @exception java.lang.Exception
	 *                when problem occurred when talking to the MBean server
	 */
	MBeanInfo getMBeanInfo(ObjectName name) throws Exception;

	/**
	 * Gets the value of a specific attribute of a named MBean. The MBean is identified by its object name.
	 *
	 * @param name
	 *            The object name of the MBean from which the attribute is to be retrieved.
	 * @param attribute
	 *            A String specifying the name of the attribute to be retrieved.
	 *
	 * @return The value of the retrieved attribute.
	 *
	 * @exception java.lang.Exception
	 *                when problem occurred when talking to the MBean server
	 */
	Object getAttribute(ObjectName name, String attribute) throws Exception;

	/**
	 * Retrieves the values of several attributes of a named MBean. The MBean is identified by its object name.
	 *
	 * @param name
	 *            The object name of the MBean from which the attributes are retrieved.
	 * @param attributes
	 *            A list of the attributes to be retrieved.
	 *
	 * @return The list of the retrieved attributes.
	 *
	 * @exception java.lang.Exception
	 *                when problem occurred when talking to the MBean server
	 */
	AttributeList getAttributes(ObjectName name, String[] attributes) throws Exception;

	/**
	 * Returns the number of MBeans registered in the MBean server.
	 *
	 * @return the number of MBeans registered.
	 *
	 * @exception Exception
	 *                when problem occurred when talking to the MBean server
	 */
	Integer getMBeanCount() throws Exception;

	/**
	 * Invokes an operation on an MBean.
	 * 
	 * @param name
	 *            The object name of the MBean on which the method is to be invoked.
	 * @param operationName
	 *            The name of the operation to be invoked.
	 * @param params
	 *            An array containing the parameters to be set when the operation is invoked
	 * @param signature
	 *            An array containing the signature of the operation, an array of class names in the format returned by
	 *            {@link Class#getName()}. The class objects will be loaded using the same class loader as the one used
	 *            for loading the MBean on which the operation was invoked.
	 *
	 * @return The object returned by the operation, which represents the result of invoking the operation on the MBean
	 *         specified.
	 *
	 * @exception java.lang.Exception
	 *                when problem occurred when talking to the MBean server
	 */
	Object invoke(ObjectName name, String operationName, Object[] params, String[] signature) throws Exception;

}
