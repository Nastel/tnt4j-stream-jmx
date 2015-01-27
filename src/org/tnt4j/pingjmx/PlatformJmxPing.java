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
	
	public static void premain(String options, Instrumentation inst) throws IOException {
		platformJmx = newInstance();
		String jmxfilter = System.getProperty("org.tnt4j.jmx.ping.filter", PingJmx.JMX_FILTER_ALL);
		int period = Integer.getInteger("org.tnt4j.jmx.ping.sample", 30000);
		platformJmx.scheduleJmxPing(jmxfilter, period);
	}

	public static void main(String[] args) throws InterruptedException, NumberFormatException, IOException {
		if (args.length < 2) {
			System.out.println("Usage: jmx-filter sample-ms (e.g \"*:*\" 30000");
		}
		pingJmx(args[0], Integer.parseInt(args[1]), TimeUnit.MILLISECONDS);
		System.out.println("jmx.ping.list=" + pingers);
		synchronized (platformJmx) {
			platformJmx.wait();
		}
	}
	
	public static void pingJmx() throws IOException {
		String jmxfilter = System.getProperty("org.tnt4j.jmx.ping.filter", PingJmx.JMX_FILTER_ALL);
		int period = Integer.getInteger("org.tnt4j.jmx.ping.sample", 30000);
		pingJmx(jmxfilter, period);
	}
	

	public static void pingJmx(String jmxfilter, long period) throws IOException {
		pingJmx(jmxfilter, period);
	}
	
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
				pingers.put(platformJmx.getMBeanServer(), platformJmx);
			}
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
	
	public void scheduleJmxPing(long period) throws IOException {
		scheduleJmxPing(PingJmx.JMX_FILTER_ALL, period);
	}

	public void scheduleJmxPing(String jmxFilter, long period) throws IOException {
		scheduleJmxPing(jmxFilter, period, TimeUnit.MILLISECONDS);
	}	

	public void scheduleJmxPing(String jmxFilter, long period, TimeUnit tunit) throws IOException {
		if (pinger == null) {
			pinger = newPingJmxImpl(getMBeanServer(), jmxFilter, period, tunit);
		}
		pinger.open();
	}
	
	public void close() {
		pinger.close();
	}
	
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
