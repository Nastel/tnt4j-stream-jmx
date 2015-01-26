/*
 * Copyright 2014 Nastel Technologies, Inc.
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

import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;

import com.nastel.jkool.tnt4j.ActivityScheduler;

public class PingJmx extends ActivityScheduler {
	public static final String JMX_FILTER_ALL = "*:*";
	
	public PingJmx(String name, MBeanServer server, long period) {
		this(name, server, JMX_FILTER_ALL, period);
    }
	
	public PingJmx(String name, MBeanServer server, String filterList, long period) {
	    super(name, new PingJmxListener(server, filterList));
	    this.schedule(name, period);
    }
	
	public PingJmx(String name, MBeanServer server, String filterList, long period, TimeUnit tunit) {
	    super(name, new PingJmxListener(server, filterList));
	    this.schedule(name, period, tunit);
    }
}
