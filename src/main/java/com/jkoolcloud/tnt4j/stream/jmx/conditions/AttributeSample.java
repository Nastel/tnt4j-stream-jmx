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
package com.jkoolcloud.tnt4j.stream.jmx.conditions;

import javax.management.*;

import com.jkoolcloud.tnt4j.core.Activity;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * <p>
 * This class provides a wrapper for sampling a single JMX MBean attribute and maintain sample context.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 */
public class AttributeSample {
	Activity activity;
	MBeanServerConnection server;
	ObjectName name;
	MBeanAttributeInfo ainfo;
	long timeStamp = 0;
	Object value;
	Throwable ex;
	PropertySnapshot snapshot;
	boolean excludeNext = false;

	/**
	 * Create an attribute sample
	 * 
	 * @param activity associated with current sample
	 * @param serverConn MBean server connection instance
	 * @param name MBean object name reference
	 * @param ainfo MBean attribute info
	 */
	protected AttributeSample(Activity activity, PropertySnapshot snapshot, MBeanServerConnection serverConn,
			ObjectName name, MBeanAttributeInfo ainfo) {
		this.activity = activity;
		this.server = serverConn;
		this.name = name;
		this.ainfo = ainfo;
		this.snapshot = snapshot;
	}

	/**
	 * Create an attribute sample
	 * 
	 * @param activity associated with current sample
	 * @param serverConn MBean server connection instance
	 * @param name MBean object name reference
	 * @param ainfo MBean attribute info
	 */
	public static AttributeSample newAttributeSample(Activity activity, PropertySnapshot snapshot,
			MBeanServerConnection serverConn, ObjectName name, MBeanAttributeInfo ainfo) {
		return new AttributeSample(activity, snapshot, serverConn, name, ainfo);
	}

	/**
	 * Sample and retrieve the value associated with the MBean attribute.
	 * 
	 * @return the value associated with the current attribute
	 */
	public Object sample() throws Exception {
		try {
			value = server.getAttribute(name, ainfo.getName());
		} catch (Exception exc) {
			Throwable ct = exc;
			if (exc instanceof RuntimeMBeanException) {
				ct = exc.getCause();
			}

			if (ct instanceof UnsupportedOperationException) {
				value = "<unsupported>";
			} else if (ct instanceof AttributeNotFoundException) {
				value = "<not found>";
			} else {
				throw exc;
			}
		}
		timeStamp = Utils.currentTimeUsec();
		return value;
	}

	/**
	 * Returns true if sample failed with error, false otherwise. Call {@link #getError()} to obtain {@code Throwable}
	 * instance when true.
	 * 
	 * @return true if sample failed with error, false otherwise
	 */
	public boolean isError(Throwable error) {
		return ex != null;
	}

	/**
	 * Has the attribute been marked for exclusion
	 * 
	 * @return true if attribute to be marked for exclusion, false otherwise
	 */
	public boolean excludeNext() {
		return excludeNext;
	}

	/**
	 * Mark attribute to be excluded from sampling
	 * 
	 * @param exclude {@code true} to exclude, {@code false} to include
	 * @return true if attribute to be marked for exclusion, false otherwise
	 */
	public boolean excludeNext(boolean exclude) {
		excludeNext = exclude;
		return excludeNext;
	}

	/**
	 * Set error associated with this sample
	 * 
	 * @param error associated with this sample
	 */
	public void setError(Throwable error) {
		ex = error;
	}

	/**
	 * Obtain MBean attribute handle associated with this sample
	 * 
	 * @return MBean attribute handle associated with this sample
	 */
	public MBeanAttributeInfo getAttributeInfo() {
		return ainfo;
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
	public ObjectName getObjetName() {
		return name;
	}

	/**
	 * Obtain MBean server connection instance associated with this instance
	 * 
	 * @return MBean server connection instance associated with this instance
	 */
	public MBeanServerConnection getMBeanServer() {
		return server;
	}

	/**
	 * Obtain {@code Activity} instance associated with this instance. Activity encapsulates all info about current
	 * sample attributes, values, timing.
	 * 
	 * @return {@code Activity} instance associated with this instance
	 */
	public Activity getActivity() {
		return activity;
	}

	/**
	 * Obtain {@code PropertySnapshot} instance associated with this instance. Snapshot encapsulates all info about
	 * current sample key/value pairs.
	 * 
	 * @return {@code PropertySnapshot} instance associated with this instance
	 */
	public PropertySnapshot getSnapshot() {
		return snapshot;
	}

	/**
	 * Obtain last sampled value. This value can only be non null after {@link #sample()} is called.
	 * 
	 * @return Obtain last sampled value, see {@link #sample()}
	 */
	public Object get() {
		return value;
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
