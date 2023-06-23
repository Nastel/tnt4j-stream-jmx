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
package com.jkoolcloud.tnt4j.stream.jmx.conditions;

import java.util.*;

import javax.management.*;

import com.jkoolcloud.tnt4j.core.Activity;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;

/**
 * <p>
 * This class provides a wrapper for sampling a list of JMX MBean attributes and maintain sample context.
 * </p>
 * 
 * @version $Revision: 2 $
 * 
 */
public class AttributeSample {
	Activity activity;
	JMXServerConnection server;
	ObjectName name;
	Map<String, MBeanAttributeInfo> aInfoMap;
	long timeStamp = 0;
	AttributeList value;
	Throwable ex;
	PropertySnapshot snapshot;

	Set<String> excludeAttrs = new HashSet<>(2);

	/**
	 * Create an attribute sample.
	 * 
	 * @param activity
	 *            associated with current sample
	 * @param snapshot
	 *            to put MBean attribute values
	 * @param serverConn
	 *            MBean server connection instance
	 * @param name
	 *            MBean object name reference
	 * @param attributes
	 *            MBean attribute handles array
	 */
	protected AttributeSample(Activity activity, PropertySnapshot snapshot, JMXServerConnection serverConn,
			ObjectName name, MBeanAttributeInfo[] attributes) {
		this.activity = activity;
		this.server = serverConn;
		this.name = name;
		this.aInfoMap = asMap(attributes);
		this.snapshot = snapshot;
	}

	/**
	 * Converts MBean attribute handles array into map.
	 * 
	 * @param attributes
	 *            MBean attribute handles array
	 * @return MBean attribute handles map
	 */
	protected static Map<String, MBeanAttributeInfo> asMap(MBeanAttributeInfo[] attributes) {
		Map<String, MBeanAttributeInfo> attrMap = new LinkedHashMap<>();

		if (attributes != null) {
			for (MBeanAttributeInfo ai : attributes) {
				attrMap.put(ai.getName(), ai);
			}
		}

		return attrMap;
	}

	/**
	 * Creates an attribute sample instance.
	 * 
	 * @param activity
	 *            associated with current sample
	 * @param snapshot
	 *            to put MBean attribute values
	 * @param serverConn
	 *            MBean server connection instance
	 * @param name
	 *            MBean object name reference
	 * @param attributes
	 *            MBean attribute handles array
	 * @return a new attribute sample instance
	 */
	public static AttributeSample newAttributeSample(Activity activity, PropertySnapshot snapshot,
			JMXServerConnection serverConn, ObjectName name, MBeanAttributeInfo[] attributes) {
		return new AttributeSample(activity, snapshot, serverConn, name, attributes);
	}

	/**
	 * Sample and retrieve the value list associated with the MBean attributes.
	 * 
	 * @return MBean attributes value list
	 *
	 * @throws Exception
	 *             if any sampling error occurs
	 */
	public AttributeList sample() throws Exception {
		try {
			Set<String> attrNames = new LinkedHashSet<>(aInfoMap.size());
			for (String attrName : aInfoMap.keySet()) {
				if (!excludeAttrs.contains(attrName)) {
					attrNames.add(attrName);
				}
			}
			value = server.getAttributes(name, attrNames.toArray(new String[0]));
			if (attrNames.size() != value.size()) {
				for (Attribute attr : value.asList()) {
					attrNames.remove(attr.getName());
				}

				for (String attrName : attrNames) {
					value.add(new Attribute(attrName, "<unavailable>"));
				}
			}
		} catch (Exception exc) {
			ex = exc;
		}
		timeStamp = Utils.currentTimeUsec();
		return value;
	}

	private static String getValueFromException(Exception exc) throws Exception {
		Throwable ct = exc;
		if (exc instanceof RuntimeMBeanException) {
			ct = exc.getCause();
		}
		String eName = ct.getClass().getSimpleName();

		if (eName.contains("Unsupported")) {
			return "<unsupported>";
		} else if (eName.contains("NotFound")) {
			return "<not found>";
		}

		if (exc instanceof MBeanException) {
			ct = Utils.getTopCause(exc);
		}
		eName = ct.getClass().getSimpleName();

		if (eName.contains("NoSuch")) {
			return "<unsupported>";
		} else if (eName.contains("Illegal")) {
			return "<illegal>";
		} else if (eName.contains("NotRunning")) {
			return "<not running>";
		} else {
			// throw exc;
			return "<" + eName + ">";
		}
	}

	/**
	 * Returns {@code true} if sample failed with error, {@code false} otherwise. Call {@link #getError()} to obtain
	 * {@code Throwable} instance when {@code true}.
	 * 
	 * @return {@code true} if sample failed with error, {@code false} otherwise
	 */
	public boolean isError() {
		return ex != null;
	}

	/**
	 * Add MBean attribute handle to excluded attributes list. To be added, provided MBean attribute handle shall be
	 * associated with this sample.
	 * 
	 * @param ainfo
	 *            MBean attribute handle
	 */
	public void exclude(MBeanAttributeInfo ainfo) {
		if (aInfoMap.get(ainfo.getName()) != null) {
			excludeAttrs.add(ainfo.getName());
		}
	}

	/**
	 * Obtain collection of excluded attribute names.
	 * 
	 * @return collection of excluded attribute names
	 */
	public Collection<String> excludes() {
		return excludeAttrs;
	}

	/**
	 * Checks if this sample has any attributes available to sample. Excluded attribute names list shall not contain all
	 * MBean available attributes.
	 * 
	 * @return {@code true} if this sample has any attributes to sample, {@code false} - otherwise
	 */
	public boolean hasAttributesToSample() {
		return aInfoMap.size() > excludeAttrs.size();
	}

	/**
	 * Set error associated with this sample
	 * 
	 * @param error
	 *            associated with this sample
	 */
	public void setError(Throwable error) {
		ex = error;
	}

	/**
	 * Obtain MBean attribute handles map associated with this sample.
	 * 
	 * @return MBean attribute handles map associated with this sample
	 */
	public Map<String, MBeanAttributeInfo> getAttributesInfo() {
		return aInfoMap;
	}

	/**
	 * Obtain MBean attribute handle associated with this sample by attribute name.
	 * 
	 * @param name
	 *            MBean attribute name
	 * @return MBean attribute handle
	 */
	public MBeanAttributeInfo getAttributeInfo(String name) {
		return aInfoMap.get(name);
	}

	/**
	 * Obtain exception (if any) that occurred during sample
	 * 
	 * @return exception (if any) that occurred during sample, null otherwise
	 */
	public Throwable getError() {
		return ex;
	}

	/**
	 * Obtain MBean object name associated with this sample
	 * 
	 * @return MBean object name handle associated with this sample
	 */
	public ObjectName getObjectName() {
		return name;
	}

	/**
	 * Obtain MBean server connection instance associated with this instance
	 * 
	 * @return MBean server connection instance associated with this instance
	 */
	public JMXServerConnection getMBeanServer() {
		return server;
	}

	/**
	 * Obtain {@link Activity} instance associated with this instance. Activity encapsulates all info about current
	 * sample attributes, values, timing.
	 * 
	 * @return {@link Activity} instance associated with this instance
	 */
	public Activity getActivity() {
		return activity;
	}

	/**
	 * Obtain {@link PropertySnapshot} instance associated with this instance. Snapshot encapsulates all info about
	 * current sample key/value pairs.
	 * 
	 * @return {@link PropertySnapshot} instance associated with this instance
	 */
	public PropertySnapshot getSnapshot() {
		return snapshot;
	}

	/**
	 * Obtain last sampled attribute values list. This list can only be non-null after {@link #sample()} is called.
	 * 
	 * @return last sampled attribute values list
	 * 
	 * @see #sample()
	 */
	public AttributeList get() {
		return value;
	}

	/**
	 * Obtain last sampled attribute value.
	 * 
	 * @param attrName
	 *            attribute name
	 * @return last sampled attribute value, or {@code null} if attribute is not found by provided name
	 * 
	 * @see #sample()
	 */
	public Object getValue(String attrName) {
		for (Attribute attr : value.asList()) {
			if (attr.getName().equals(attrName)) {
				return attr.getValue();
			}
		}
		return null;
	}

	/**
	 * Obtain age in microseconds since last sampled value. {@link #sample()} must be called prior to calling this call,
	 * otherwise -1 is returned.
	 * 
	 * @return get in microseconds since last sampled value, -1 if no sample was taken.
	 *
	 * @see #sample()
	 */
	public long ageUsec() {
		return timeStamp > 0 ? (Utils.currentTimeUsec() - timeStamp) : -1;
	}
}
