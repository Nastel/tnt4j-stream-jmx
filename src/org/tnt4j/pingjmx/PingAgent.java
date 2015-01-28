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

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

/**
 * <p> 
 * This class provides java agent implementation as well as <code>main()</code>
 * entry point to run as a standalone application.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see PingFactory
 */
public class PingAgent {
	protected static PlatformJmxPing platformJmx;
	protected static HashMap<MBeanServer, PlatformJmxPing> pingers = new HashMap<MBeanServer, PlatformJmxPing>(89);

	/**
	 * Entry point to be loaded as -javaagent:jarpath="mbean-filter!sample.ms" command line.
	 * Example: -javaagent:tnt4j-ping-jmx.jar="*:*!30000"
	 * 
	 * @param options ! separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param inst instrumentation handle
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		String jmxfilter = System.getProperty("org.tnt4j.jmx.ping.filter", PingJmx.JMX_FILTER_ALL);
		int period = Integer.getInteger("org.tnt4j.jmx.ping.sample", 30000);
		if (options != null) {
			String [] args = options.split("!");
			if (args.length >= 2) {
				jmxfilter = args[0];
				period = Integer.parseInt(args[1]);
			}
		}
		pingJmx(jmxfilter, period, TimeUnit.MILLISECONDS);
		System.out.println("PingAgent: filter=" + jmxfilter + ", sample.ms=" + period + ", jmx.ping.list=" + pingers);
	}

	/**
	 * Main entry point for running as a standalone application (test only).
	 * 
	 * @param args argument list: mbean-filter sample_time_ms
	 */
	public static void main(String[] args) throws InterruptedException, NumberFormatException, IOException {
		if (args.length < 2) {
			System.out.println("Usage: mbean-filter sample-ms (e.g \"*:*\" 30000");
		}
		pingJmx(args[0], Integer.parseInt(args[1]), TimeUnit.MILLISECONDS);
		System.out.println("PingAgent: filter=" + args[0] + ", sample.ms=" + args[1] + ", jmx.ping.list=" + pingers);
		synchronized (platformJmx) {
			platformJmx.wait();
		}
	}
	
	/**
	 * Schedule JMX ping with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 */
	public static void pingJmx() throws IOException {
		String jmxfilter = System.getProperty("org.tnt4j.jmx.ping.filter", PingJmx.JMX_FILTER_ALL);
		int period = Integer.getInteger("org.tnt4j.jmx.ping.sample", 30000);
		pingJmx(jmxfilter, period);
	}
	

	/**
	 * Schedule JMX ping with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling in milliseconds.
	 * 
	 */
	public static void pingJmx(String jmxfilter, long period) throws IOException {
		pingJmx(jmxfilter, period);
	}
	
	/**
	 * Schedule JMX ping with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling time
	 * @param tunit time units for sampling period
	 * 
	 */
	public static void pingJmx(String jmxfilter, long period, TimeUnit tunit) throws IOException {
		// initialize ping with default MBeanServer
		PingFactory pFactory = DefaultPingFactory.getInstance();
		platformJmx = pFactory.newInstance();
		platformJmx.scheduleJmxPing(jmxfilter, period);
		pingers.put(platformJmx.getMBeanServer(), platformJmx);
		
		// find other registered mbean servers
		ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server: mlist) {
			PlatformJmxPing jmxp = pingers.get(server);
			if (jmxp == null) {
				jmxp = pFactory.newInstance(server);
				jmxp.scheduleJmxPing(jmxfilter, period);
				pingers.put(jmxp.getMBeanServer(), jmxp);
			}
		}		
	}
}
