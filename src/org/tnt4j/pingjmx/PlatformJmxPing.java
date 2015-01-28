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
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

/**
 * <p> 
 * This class provides scheduled execution and sampling of a JMX metrics
 * for a given <code>MBeanServer</code> instance. By default the class will
 * use <code>ManagementFactory.getPlatformMBeanServer()</code> instance.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 * @see PingJmx
 */
public class PlatformJmxPing {
	protected static PlatformJmxPing platformJmx;
	protected static HashMap<MBeanServer, PlatformJmxPing> pingers = new HashMap<MBeanServer, PlatformJmxPing>(89);
	
	protected PingJmx pinger;
	protected MBeanServer targetServer;
	
	/**
	 * Entry point to be loaded as -agent:jarpath=mbean-filter!sample.ms command line.
	 * Example: -agent:tnt4j-ping-jmx.jar="*:*!30000"
	 * 
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		String [] args = options.split("!");
		String jmxfilter = System.getProperty("org.tnt4j.jmx.ping.filter", PingJmx.JMX_FILTER_ALL);
		int period = Integer.getInteger("org.tnt4j.jmx.ping.sample", 30000);
		if (args.length > 2) {
			jmxfilter = args[0];
			period = Integer.parseInt(args[1]);
		}
		pingJmx(jmxfilter, period, TimeUnit.MILLISECONDS);
		System.out.println("jmx.ping.list=" + pingers);
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
		System.out.println("jmx.ping.list=" + pingers);
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
		platformJmx = newInstance();
		platformJmx.scheduleJmxPing(jmxfilter, period);
		pingers.put(platformJmx.getMBeanServer(), platformJmx);
		
		// find other registered mbean servers
		ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server: mlist) {
			PlatformJmxPing jmxp = pingers.get(server);
			if (jmxp == null) {
				jmxp = newInstance(server);
				jmxp.scheduleJmxPing(jmxfilter, period);
				pingers.put(jmxp.getMBeanServer(), jmxp);
			}
		}		
	}
		
	/**
	 * Create a default instance with default MBean server instance
	 * <code>ManagementFactory.getPlatformMBeanServer()</code>
	 * 
	 */
	public PlatformJmxPing() {
		this(ManagementFactory.getPlatformMBeanServer());
	}
	
	/**
	 * Create a default instance with a given MBean server instance
	 * 
	 * @param mserver MBean server instance
	 */
	public PlatformJmxPing(MBeanServer mserver) {
		targetServer = mserver;
	}
	
	/**
	 * Create a default instance with default MBean server instance
	 * <code>ManagementFactory.getPlatformMBeanServer()</code>
	 * 
	 */
	public static PlatformJmxPing newInstance() {
		return new PlatformJmxPing();
	}

	/**
	 * Create a default instance with a given MBean server instance
	 * 
	 * @param mserver MBean server instance
	 */
	public static PlatformJmxPing newInstance(MBeanServer server) {
		return new PlatformJmxPing(server);
	}

	/**
	 * Obtain MBean server associated with this object
	 * 
	 * @return MBean server instance
	 */
	public MBeanServer getMBeanServer() {
		return targetServer;
	}
	
	/**
	 * Schedule JMX ping with associated MBean server instance
	 * and all MBeans.
	 *  
	 * @param period sampling time in milliseconds
	 * 
	 */
	public void scheduleJmxPing(long period) throws IOException {
		scheduleJmxPing(PingJmx.JMX_FILTER_ALL, period);
	}

	/**
	 * Schedule JMX ping with associated MBean server instance
	 *  
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling time in milliseconds
	 * 
	 */
	public void scheduleJmxPing(String jmxFilter, long period) throws IOException {
		scheduleJmxPing(jmxFilter, period, TimeUnit.MILLISECONDS);
	}	

	/**
	 * Schedule JMX ping with associated MBean server instance
	 *  
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling time
	 * @param tunit time units for sampling period
	 * 
	 */
	public void scheduleJmxPing(String jmxFilter, long period, TimeUnit tunit) throws IOException {
		if (pinger == null) {
			pinger = newPingJmxImpl(getMBeanServer(), jmxFilter, period, tunit);
		}
		pinger.open();
	}
	
	/**
	 * Close this object instance and cancel all outstanding
	 * or scheduled samplers.
	 * 
	 */
	public void close() {
		pinger.close();
	}
	
	/**
	 * Obtain underlying instance of the <code>PingJmx</code>
	 * that actually runs/schedules sampling.
	 * 
	 */
	public PingJmx getPingerImpl() {
		return pinger;
	}
	
	/**
	 * Create new instance of <code>PingJmx</code>
	 * Override this call to return your instance of pinger
	 *
	 * @param server MBean server instance
	 * @param jmxFilter JMX filters semicolon separated
	 * @param period time period for sampling
	 * @param tunit time units for period
	 *  
	 * @return new <code>PingJmx</code> instance
	 */
	protected PingJmx newPingJmxImpl(MBeanServer mserver, String jmxFilter, long period, TimeUnit tunit) {
		return new PingJmx(this.getClass().getName(), mserver, jmxFilter, period, tunit);
	}
}
