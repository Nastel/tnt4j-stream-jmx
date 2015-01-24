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

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

public class PlatformJmxPing {

	protected static PlatformJmxPing platformJmx;
	
	protected PingJmx pinger;
	protected MBeanServer targetServer;
	
	public static void premain(String options, Instrumentation inst) throws IOException {
		platformJmx = newInstance();
		String jmxfilter = System.getProperty("org.tnt4j.jmx.ping.filter", PingJmx.JMX_FILTER_ALL);
		int period = Integer.getInteger("org.tnt4j.jmx.ping.sample", 60000);
		platformJmx.scheduleJmxPing(jmxfilter, period);
	}

	public static void main(String[] args) throws InterruptedException, NumberFormatException, IOException {
		if (args.length < 2) {
			System.out.println("Usage: jmx-filter sample-ms");
		}
		HashMap<MBeanServer, PlatformJmxPing> pingers = new HashMap<MBeanServer, PlatformJmxPing>(89);
		String filter = args[0];
		long period = Integer.parseInt(args[1]);
		
		// intialize ping with default MBeanServer
		platformJmx = newInstance();
		platformJmx.scheduleJmxPing(filter, period);
		pingers.put(platformJmx.getMBeanServer(), platformJmx);
		
		// find other registered MBeanServers
		ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server: mlist) {
			PlatformJmxPing jmxp = pingers.get(server);
			if (jmxp == null) {
				jmxp = newInstance(server);
				jmxp.scheduleJmxPing(filter, period);
				pingers.put(platformJmx.getMBeanServer(), platformJmx);
				System.out.println("Found mbean.server=" + server + ", jmx.ping=" + jmxp + ", filter=" + filter + ", period=" + period);
			}
		}
		System.out.println("jmx.ping.list=" + pingers);
		synchronized (platformJmx) {
			platformJmx.wait();
		}
	}
	
	public PlatformJmxPing() {
		this(ManagementFactory.getPlatformMBeanServer());
	}
	
	public PlatformJmxPing(MBeanServer mserver) {
		targetServer = mserver;
	}
	
	public static PlatformJmxPing newInstance() {
		return new PlatformJmxPing();
	}

	public static PlatformJmxPing newInstance(MBeanServer server) {
		return new PlatformJmxPing(server);
	}

	public MBeanServer getMBeanServer() {
		return targetServer;
	}
	
	protected void scheduleJmxPing(long period) throws IOException {
		scheduleJmxPing(PingJmx.JMX_FILTER_ALL, period);
	}

	protected void scheduleJmxPing(String jmxFilter, long period) throws IOException {
		pinger = new PingJmx(this.getClass().getName(), getMBeanServer(), jmxFilter, period);
		pinger.open();
	}	
}
