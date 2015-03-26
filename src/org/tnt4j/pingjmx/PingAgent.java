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
package org.tnt4j.pingjmx;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import org.tnt4j.pingjmx.conditions.AttributeSample;
import org.tnt4j.pingjmx.core.Pinger;
import org.tnt4j.pingjmx.core.SampleContext;
import org.tnt4j.pingjmx.core.SampleListener;
import org.tnt4j.pingjmx.factory.DefaultPingFactory;
import org.tnt4j.pingjmx.factory.PingFactory;

import com.nastel.jkool.tnt4j.core.Activity;

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
	protected static Pinger platformJmx;
	protected static ConcurrentHashMap<MBeanServer, Pinger> pingers = new ConcurrentHashMap<MBeanServer, Pinger>(89);
	protected static boolean TRACE = Boolean.getBoolean("org.tnt4j.pingagent.trace");
	/**
	 * Entry point to be loaded as -javaagent:jarpath="mbean-filter!sample.ms" command line.
	 * Example: -javaagent:tnt4j-ping-jmx.jar="*:*!30000"
	 * 
	 * @param options ! separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param inst instrumentation handle
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		String jmxfilter = System.getProperty("org.tnt4j.jmx.ping.filter", Pinger.JMX_FILTER_ALL);
		int period = Integer.getInteger("org.tnt4j.jmx.ping.period", 30000);
		if (options != null) {
			String [] args = options.split("!");
			if (args.length >= 2) {
				jmxfilter = args[0];
				period = Integer.parseInt(args[1]);
			}
		}
		ping(jmxfilter, period, TimeUnit.MILLISECONDS);
		System.out.println("PingAgent: filter=" + jmxfilter + ", sample.ms=" + period + ", jmx.ping.list=" + pingers);
	}

	/**
	 * Main entry point for running as a standalone application (test only).
	 * 
	 * @param args argument list: mbean-filter sample_time_ms
	 */
	public static void main(String[] args) throws InterruptedException, NumberFormatException, IOException {
		if (args.length < 3) {
			System.out.println("Usage: mbean-filter sample-ms wait-ms(e.g \"*:*\" 30000");
		}
		ping(args[0], Integer.parseInt(args[1]), TimeUnit.MILLISECONDS);
		System.out.println("PingAgent: filter=" + args[0] + ", sample.ms=" + args[1] + ", jmx.ping.list=" + pingers);
		synchronized (platformJmx) {
			platformJmx.wait(Long.parseLong(args[2]));
		}
	}
	
	/**
	 * Schedule ping with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 */
	public static void ping() throws IOException {
		String jmxfilter = System.getProperty("org.tnt4j.jmx.ping.filter", Pinger.JMX_FILTER_ALL);
		int period = Integer.getInteger("org.tnt4j.jmx.ping.period", 30000);
		ping(jmxfilter, period);
	}
	

	/**
	 * Schedule ping with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling in milliseconds.
	 * 
	 */
	public static void ping(String jmxfilter, long period) throws IOException {
		ping(jmxfilter, period, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Schedule ping with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 * @param jmxfilter semicolon separated filter list
	 * @param period sampling time
	 * @param tunit time units for sampling period
	 * 
	 */
	public static void ping(String jmxfilter, long period, TimeUnit tunit) throws IOException {
		// obtain a default ping factory
		PingFactory pFactory = DefaultPingFactory.getInstance();
		
		if (platformJmx == null) {
			// create new pinger with default MBeanServer instance
			platformJmx = pFactory.newInstance();
			// schedule ping with a given filter and sampling period
			platformJmx.setSchedule(jmxfilter, period).addListener(new AgentSampleListener()).run();
			pingers.put(platformJmx.getMBeanServer(), platformJmx);
		}
		
		// find other registered MBean servers and initiate sampling for all
		ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server: mlist) {
			Pinger jmxp = pingers.get(server);
			if (jmxp == null) {
				jmxp = pFactory.newInstance(server);
				jmxp.setSchedule(jmxfilter, period).addListener(new AgentSampleListener()).run();
				pingers.put(jmxp.getMBeanServer(), jmxp);
			}
		}		
	}
	
	/**
	 * Obtain a map of all scheduled MBeanServers and associated ping
	 * references.
	 * 
	 * @return map of all scheduled MBeanServers and associated ping references.
	 */
	public static Map<MBeanServer, Pinger> getPingers() {
		HashMap<MBeanServer, Pinger> copy = new HashMap<MBeanServer, Pinger>(89);
		copy.putAll(pingers);
		return copy;
	}
	
	/**
	 * Cancel and close all outstanding <code>Pinger</code> 
	 * instances and stop all sampling for all <code>MBeanServer</code>
	 * instances.
	 *  
	 */
	public static void cancel() {
		for (Pinger pinger: pingers.values()) {
			pinger.cancel();
		}
		pingers.clear();
	}

	/**
	 * Cancel and close all sampling for a given <code>MBeanServer</code>
	 * instance.
	 *  
	 * @param mserver MBeanServer instance
	 */
	public static void cancel(MBeanServer mserver) {
		Pinger pinger = pingers.remove(mserver);
		if (pinger != null) pinger.cancel();
	}
}

class AgentSampleListener implements SampleListener {
	@Override
	public void pre(SampleContext context, Activity activity) {
	}

	@Override
	public boolean sample(SampleContext context, AttributeSample sample) {
		return true;
	}

	@Override
	public void post(SampleContext context, Activity activity) {
		if (PingAgent.TRACE) {
			System.out.println(activity.getName()
				+ ": sample.count=" + context.getSampleCount()
				+ ", mbean.count=" + context.getMBeanServer().getMBeanCount()
				+ ", elasped.usec=" + activity.getElapsedTime() 
				+ ", snap.count=" + activity.getSnapshotCount() 
				+ ", id.count=" + activity.getIdCount()
				+ ", noop.count=" + context.getTotalNoopCount()
				+ ", sample.mbeans.count=" + context.getMBeanCount()
				+ ", sample.metric.count=" + context.getLastMetricCount()
				+ ", sample.time.usec=" + context.getLastSampleUsec()
				+ ", exclude.attrs=" + context.getExclAttrCount()
				+ ", trackind.id=" + activity.getTrackingId() 
				+ ", mbean.server=" + context.getMBeanServer()
				);
		}
	}

	@Override
    public void error(SampleContext context, AttributeSample sample) {
		if (PingAgent.TRACE) {
			System.err.println("Failed to sample: " + sample.getAttributeInfo() + ", ex=" + sample.getError());
			sample.getError().printStackTrace();
		}
    }
}