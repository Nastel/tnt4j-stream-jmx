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
package org.tnt4j.stream.jmx;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import org.tnt4j.stream.jmx.conditions.AttributeSample;
import org.tnt4j.stream.jmx.core.Sampler;
import org.tnt4j.stream.jmx.core.SampleContext;
import org.tnt4j.stream.jmx.core.SampleListener;
import org.tnt4j.stream.jmx.factory.DefaultSamplerFactory;
import org.tnt4j.stream.jmx.factory.SamplerFactory;

import com.nastel.jkool.tnt4j.core.Activity;

/**
 * <p> 
 * This class provides java agent implementation as well as <code>main()</code>
 * entry point to run as a standalone application.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see SamplerFactory
 */
public class SamplingAgent {
	protected static Sampler platformJmx;
	protected static ConcurrentHashMap<MBeanServer, Sampler> STREAM_AGENTS = new ConcurrentHashMap<MBeanServer, Sampler>(89);
	protected static boolean TRACE = Boolean.getBoolean("org.tnt4j.stream.jmx.agent.trace");
	
	/**
	 * Entry point to be loaded as -javaagent:jarpath="mbean-filter!sample.ms" command line.
	 * Example: -javaagent:tnt4j-sample-jmx.jar="*:*!30000"
	 * 
	 * @param options ! separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param inst instrumentation handle
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		String incFilter = System.getProperty("org.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("org.tnt4j.stream.jmx.exclude.filter");
		int period = Integer.getInteger("org.tnt4j.stream.jmx.period", 30000);
		if (options != null) {
			String [] args = options.split("!");
			if (args.length >= 2) {
				incFilter = args[0];
				period = Integer.parseInt(args[1]);
			}
		}
		sample(incFilter, excFilter, period, TimeUnit.MILLISECONDS);
		System.out.println("SamplingAgent: inlcude.filter=" + incFilter
				+ ", exclude.filter=" + excFilter
				+ ", sample.ms=" + period
				+ ", jmx.sample.list=" + STREAM_AGENTS);
	}

	/**
	 * Main entry point for running as a standalone application (test only).
	 * 
	 * @param args
	 *            argument list: mbean-filter sample_time_ms
	 */
	public static void main(String[] args) throws InterruptedException, NumberFormatException, IOException {
		if (args.length < 4) {
			System.out.println("Usage: mbean-filter exclude-filter sample-ms wait-ms(e.g \"*:*\" 30000");
		}
		try {
			long sample_time = Integer.parseInt(args[2]);
			long wait_time = Integer.parseInt(args[3]);
			sample(args[0], args[1], sample_time, TimeUnit.MILLISECONDS);
			System.out.println("SamplingAgent: inlcude.filter=" + args[0]
					+ ", exclude.filter=" + args[1]
					+ ", sample.ms=" + sample_time
					+ ", wait.ms=" + wait_time
					+ ", jmx.sample.list=" + STREAM_AGENTS);
			synchronized (platformJmx) {
				platformJmx.wait(wait_time);
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}
	
	/**
	 * Schedule sample with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 */
	public static void sample() throws IOException {
		String incFilter = System.getProperty("org.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("org.tnt4j.stream.jmx.exclude.filter");
		int period = Integer.getInteger("org.tnt4j.stream.jmx.period", 30000);
		sample(incFilter, excFilter, period);
	}
	

	/**
	 * Schedule sample with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 * @param incFilter semicolon separated include filter list
	 * @param period sampling in milliseconds.
	 * 
	 */
	public static void sample(String incFilter, long period) throws IOException {
		sample(incFilter, null, period);
	}
	
	/**
	 * Schedule sample with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 * @param incFilter semicolon separated include filter list
	 * @param excFilter semicolon separated exclude filter list (null if empty)
	 * @param period sampling in milliseconds.
	 * 
	 */
	public static void sample(String incFilter, String excFilter, long period) throws IOException {
		sample(incFilter, excFilter, period, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Schedule sample with default MBean server instance as well
	 * as all registered MBean servers within the JVM.
	 * 
	 * @param incFilter semicolon separated include filter list
	 * @param excFilter semicolon separated exclude filter list (null if empty)
	 * @param period sampling time
	 * @param tunit time units for sampling period
	 * 
	 */
	public static void sample(String incFilter, String excFilter, long period, TimeUnit tunit) throws IOException {
		// obtain a default sample factory
		SamplerFactory pFactory = DefaultSamplerFactory.getInstance();
		
		if (platformJmx == null) {
			// create new sampler with default MBeanServer instance
			platformJmx = pFactory.newInstance();
			// schedule sample with a given filter and sampling period
			platformJmx.setSchedule(incFilter, excFilter, period, tunit).addListener(new AgentSampleListener()).run();
			STREAM_AGENTS.put(platformJmx.getMBeanServer(), platformJmx);
		}
		
		// find other registered MBean servers and initiate sampling for all
		ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server: mlist) {
			Sampler jmxp = STREAM_AGENTS.get(server);
			if (jmxp == null) {
				jmxp = pFactory.newInstance(server);
				jmxp.setSchedule(incFilter, excFilter, period, tunit).addListener(new AgentSampleListener()).run();
				STREAM_AGENTS.put(jmxp.getMBeanServer(), jmxp);
			}
		}		
	}
	
	/**
	 * Obtain a map of all scheduled MBeanServers and associated sample
	 * references.
	 * 
	 * @return map of all scheduled MBeanServers and associated sample references.
	 */
	public static Map<MBeanServer, Sampler> getSamplers() {
		HashMap<MBeanServer, Sampler> copy = new HashMap<MBeanServer, Sampler>(89);
		copy.putAll(STREAM_AGENTS);
		return copy;
	}
	
	/**
	 * Cancel and close all outstanding <code>Sampler</code> 
	 * instances and stop all sampling for all <code>MBeanServer</code>
	 * instances.
	 *  
	 */
	public static void cancel() {
		for (Sampler sampler: STREAM_AGENTS.values()) {
			sampler.cancel();
		}
		STREAM_AGENTS.clear();
	}

	/**
	 * Cancel and close all sampling for a given <code>MBeanServer</code>
	 * instance.
	 *  
	 * @param mserver MBeanServer instance
	 */
	public static void cancel(MBeanServer mserver) {
		Sampler sampler = STREAM_AGENTS.remove(mserver);
		if (sampler != null) sampler.cancel();
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
		if (SamplingAgent.TRACE) {
			System.out.println(activity.getName()
				+ ": sample.count=" + context.getSampleCount()
				+ ", mbean.count=" + context.getMBeanServer().getMBeanCount()
				+ ", elasped.usec=" + activity.getElapsedTimeUsec() 
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
		if (SamplingAgent.TRACE) {
			System.err.println("Failed to sample: " + sample.getAttributeInfo() + ", ex=" + sample.getError());
			sample.getError().printStackTrace();
		}
    }
}