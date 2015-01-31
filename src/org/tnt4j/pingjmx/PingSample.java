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

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import com.nastel.jkool.tnt4j.core.Activity;

public class PingSample {
	Activity activity;
	MBeanServer server;
	ObjectName name;
	MBeanAttributeInfo ainfo;
	Object value;
	
	public PingSample(Activity activity, MBeanServer server, ObjectName name, MBeanAttributeInfo ainfo) {
		this.activity = activity;
		this.server = server;
		this.name = name;
		this.ainfo = ainfo;
	}
	
	public Object sample() throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException {
		value = server.getAttribute(name, ainfo.getName());
		return value;
	}
	
	public MBeanAttributeInfo getAttributeInfo() {
		return ainfo;
	}
	
	public ObjectName getObjetName() {
		return name;
	}
	
	public MBeanServer getMBeanServer() {
		return server;
	}
	
	public Activity getActivity() {
		return activity;
	}
	
	public Object get() {
		return value;
	}
}
