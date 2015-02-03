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
package org.tnt4j.pingjmx.conditions;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import com.nastel.jkool.tnt4j.core.Activity;
import com.nastel.jkool.tnt4j.utils.Utils;

/**
 * <p> 
 * This class provides a wrapper for sampling a single JMX MBean
 * attribute and maintain sample context.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 */
public class AttributeSample {
	Activity activity;
	MBeanServer server;
	ObjectName name;
	MBeanAttributeInfo ainfo;
	long timeStamp = 0;
	Object value;
	Throwable ex;
	
	/**
	 * Create an attribute sample
	 * 
	 * @param activity associated with current sample
	 * @param server MBean server instance
	 * @param name MBean object name reference
	 * @param ainfo MBean attribute info
	 * 
	 */
	public AttributeSample(Activity activity, MBeanServer server, ObjectName name, MBeanAttributeInfo ainfo) {
		this.activity = activity;
		this.server = server;
		this.name = name;
		this.ainfo = ainfo;
	}
	
	/**
	 * Sample and retrieve the value associated with 
	 * the MBean attribute.
	 * 
	 * @return the value associated with the current attribute 
	 */
	public Object sample() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException {
		value = server.getAttribute(name, ainfo.getName());
		timeStamp = Utils.currentTimeUsec();
		return value;
	}
	
	/**
	 * Returns true if sample failed with error, false otherwise.
	 * Call {@link #getError()} to obtain <code>Throwable</code>
	 * instance when true.
	 * 
	 * @return true if sample failed with error, false otherwise
	 */
	public boolean isError(Throwable error) {
		return ex != null;
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
	 * Obtain MBean server instance associated with this instance
	 * 
	 * @return MBean server instance associated with this instance
	 */
	public MBeanServer getMBeanServer() {
		return server;
	}
	
	/**
	 * Obtain <code>Activity</code> instance associated with this instance.
	 * Activity encapsulates all info about current sample attributes, values,
	 * timing.
	 * 
	 * @return code>Activity</code> instance associated with this instance
	 */
	public Activity getActivity() {
		return activity;
	}
	
	/**
	 * Obtain last sampled value. This value can only be non null
	 * after {@link #sample()} is called.
	 * 
	 * @return Obtain last sampled value, see {@link #sample()}
	 */
	public Object get() {
		return value;
	}
	
	/**
	 * Obtain age in microseconds since last sampled value. {@link #sample()} must 
	 * be called prior to calling this call, otherwise -1 is returned.
	 * 
	 * @return get in microseconds since last sampled value, -1 if no sample was taken.
	 * 			see {@link #sample()}
	 */
	public long ageUsec() {
		return timeStamp > 0? (Utils.currentTimeUsec() - timeStamp): -1;
	}
}
