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
package com.jkoolcloud.tnt4j.stream.jmx;

import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;

import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.DefaultSamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * <p>
 * This class provides java agent implementation {@link #premain(String, Instrumentation)}, {@link #agentmain(String, Instrumentation)}
 * as well as {@link #main(String[])}  entry point to run as a standalone application.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see SamplerFactory
 */
public class SamplingAgent {
	protected static Sampler platformJmx;
	protected static ConcurrentHashMap<MBeanServer, Sampler> STREAM_AGENTS = new ConcurrentHashMap<MBeanServer, Sampler>( 89);
	protected static boolean TRACE = Boolean.getBoolean("com.jkoolcloud.tnt4j.stream.jmx.agent.trace");
	protected static boolean VALIDATE_TYPES = Boolean .getBoolean("com.jkoolcloud.tnt4j.stream.jmx.agent.validate.types");

	/**
	 * Entry point to be loaded as {@code -javaagent:jarpath="mbean-filter!sample.ms"} command line.
	 * Example:  {@code -javaagent:tnt4j-sample-jmx.jar="*:*!30000"}
	 * 
	 * @param options ! separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param inst instrumentation handle
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter");
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", 30000);
		if (options != null) {
			String[] args = options.split("!");
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
	 * Entry point to bea loaded as JVM agent. Does same as {@link #premain(String, Instrumentation)}.
	 * 
	 * @param agentArgs ! separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param inst instrumentation handle
	 * @throws IOException if any I/O exception occurs while starting agent
	 *
	 * @see #premain(String, Instrumentation)
	 */
	public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
		String agentParams = "";
		String tnt4jProp = System.getProperty("tnt4j.config");
		String agentLibPath = "";
		if (!isEmpty(agentArgs)) {
			String[] args = agentArgs.split("!");

			if (args.length >= 2) {
				agentParams = args[0] + "!" + args[1];
			}

			for (String arg : args) {
				if (arg.startsWith("-Dtnt4j.config")) {
					if (isEmpty(tnt4jProp)) {
						String[] prop = arg.split("=");
						tnt4jProp = prop.length > 1 ? prop[1] : null;

						System.setProperty("tnt4j.config", tnt4jProp);
					}
				} else if (arg.startsWith("-DSamplingAgent.path")) {
					String[] prop = arg.split("=");
					agentLibPath = prop.length > 1 ? prop[1] : null;
				} else if (arg.startsWith("trace=")) {
					String[] prop = arg.split("=");
					TRACE = prop.length > 1 ? Boolean.parseBoolean(prop[1]) : TRACE;
				} else if (arg.startsWith("validate=")) {
					String[] prop = arg.split("=");
					VALIDATE_TYPES = prop.length > 1 ? Boolean.parseBoolean(prop[1]) : VALIDATE_TYPES;
				}
			}
		}

		System.out.println("tnt4j.config value=" + tnt4jProp);

		File zorkaPath = new File(agentLibPath);
		final String[] classPathEntries = zorkaPath.list(new JarFilter());
		if (classPathEntries != null) {
			for (String classPathEntry : classPathEntries) {
				File pathFile = new File(classPathEntry);
				extendClasspath(pathFile.toURI().toURL());
			}
		}

		premain(agentParams, inst);
	}

	/**
	 * Loads required classpath entries to running JVM.
	 *
	 * @param classPathEntriesURL classpath entries URLs to attach to JVM
	 * @throws Exception if exception occurs while extending system class loader's classpath
	 */
	private static void extendClasspath(URL... classPathEntriesURL) throws Exception {
		URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
		method.setAccessible(true);
		for (URL classPathEntryURL : classPathEntriesURL) {
			try {
				method.invoke(classLoader, classPathEntryURL);
			} catch (Exception e) {
				System.err.println("Could not load lib: " + classPathEntryURL);
				System.err.println("Exception: " + e.getLocalizedMessage());
				// e.printStackTrace ();
			}
		}
	}

	/**
	 * Main entry point for running as a standalone application (test only).
	 * 
	 * @param args
	 *            argument list: mbean-filter sample_time_ms
	 */
	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("Usage: mbean-filter exclude-filter sample-ms [wait-ms] (e.g \"*:*\" \"\" 10000 60000)");
			System.out.println("   or: -attach vmName/vmId agentJarPath (e.g -attach activemq [ENV_PATH]/tnt-stream-jmx.jar)");
		}
		if (args[0].equalsIgnoreCase("-attach")) {
			String vmDescr = args[1];
			String jarPath = args[2];
			String agentOptions = args.length > 3 ? args[3] : "*:*!10000";

			attach(vmDescr, jarPath, agentOptions);
		} else {
			try {
				long sample_time = Integer.parseInt(args[2]);
				long wait_time = args.length > 3 ? Integer.parseInt(args[3]) : 0;
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
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 */
	public static void sample() throws IOException {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter");
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", 30000);
		sample(incFilter, excFilter, period);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 * 
	 * @param incFilter semicolon separated include filter list
	 * @param period sampling in milliseconds.
	 * 
	 */
	public static void sample(String incFilter, long period) throws IOException {
		sample(incFilter, null, period);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
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
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
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
			platformJmx.setSchedule(incFilter, excFilter, period, tunit)
					.addListener(new DefaultSampleListener(System.out, TRACE, VALIDATE_TYPES)).run();
			STREAM_AGENTS.put(platformJmx.getMBeanServer(), platformJmx);
		}

		// find other registered MBean servers and initiate sampling for all
		ArrayList<MBeanServer> mlist = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server : mlist) {
			Sampler jmxp = STREAM_AGENTS.get(server);
			if (jmxp == null) {
				jmxp = pFactory.newInstance(server);
				jmxp.setSchedule(incFilter, excFilter, period, tunit)
						.addListener(new DefaultSampleListener(System.out, TRACE, VALIDATE_TYPES)).run();
				STREAM_AGENTS.put(jmxp.getMBeanServer(), jmxp);
			}
		}
	}

	/**
	 * Attaches to {@code vmDescr} defined JVM as agent.
	 * 
	 * @param vmDescr JVM descriptor: name fragment or pid
	 * @param agentJarPath agent JAR path
	 * @param agentOptions agent options
	 * @throws Exception if any exception occurs while attaching to JVM
	 */
	public static void attach(String vmDescr, String agentJarPath, String agentOptions) throws Exception {
		if (isEmpty(vmDescr) || isEmpty(agentJarPath)) {
			throw new RuntimeException("JVM attach VM descriptor and agent Jar path must be not empty!..");
		}

		List<VirtualMachineDescriptor> runningVMsList = VirtualMachine.list();

		VirtualMachineDescriptor descriptor = null;
		for (VirtualMachineDescriptor rVM : runningVMsList) {
			if ((rVM.displayName().contains(vmDescr)
					&& !rVM.displayName().contains(SamplingAgent.class.getSimpleName()))
					|| rVM.id().equalsIgnoreCase(vmDescr)) {
				descriptor = rVM;
				break;
			}
		}

		if (descriptor == null) {
			System.out.println("----------- Available JVMs -----------");
			for (VirtualMachineDescriptor vmD : runningVMsList) {
				System.out.println(vmD.displayName());
			}
			System.out.println("---------------- END ----------------");
			throw new RuntimeException("Java VM not found using provided descriptor: [" + vmDescr + "]");
		}

		final VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());
		File pathFile = new File(agentJarPath);
		String agentPath = pathFile.getAbsolutePath();
		System.out.println("Attaching agent: " + agentPath + " to " + descriptor + "");

		agentOptions += "!trace=" + TRACE;
		agentOptions += "!validate=" + VALIDATE_TYPES;

		agentOptions += "!-DSamplingAgent.path=" + agentPath;

		String tnt4jConf = System.getProperty("tnt4j.config");

		if (!isEmpty(tnt4jConf)) {
			String tnt4jPropPath = new File(tnt4jConf).getAbsolutePath();
			agentOptions += "!-Dtnt4j.config=" + tnt4jPropPath;
		}

		virtualMachine.loadAgent(agentPath, agentOptions);
		virtualMachine.detach();
	}

	private static boolean isEmpty(String str) {
		return str == null || str.trim().isEmpty();
	}

	/**
	 * Obtain a map of all scheduled MBeanServers and associated sample references.
	 * 
	 * @return map of all scheduled MBeanServers and associated sample references.
	 */
	public static Map<MBeanServer, Sampler> getSamplers() {
		HashMap<MBeanServer, Sampler> copy = new HashMap<MBeanServer, Sampler>(89);
		copy.putAll(STREAM_AGENTS);
		return copy;
	}

	/**
	 * Cancel and close all outstanding {@link Sampler} instances and stop all sampling for all {@code MBeanServer}
	 * instances.
	 * 
	 */
	public static void cancel() {
		for (Sampler sampler : STREAM_AGENTS.values()) {
			sampler.cancel();
		}
		STREAM_AGENTS.clear();
	}

	/**
	 * Cancel and close all sampling for a given {@code MBeanServer} instance.
	 * 
	 * @param mserver MBeanServer instance
	 */
	public static void cancel(MBeanServer mserver) {
		Sampler sampler = STREAM_AGENTS.remove(mserver);
		if (sampler != null) sampler.cancel();
	}

	private static class JarFilter implements FilenameFilter {
		public JarFilter() {
		}

		@Override
		public boolean accept(File var1, String var2) {
			String var3 = var2.toLowerCase();
			return var3.endsWith(".jar") || var3.endsWith(".zip");
		}
	}
}