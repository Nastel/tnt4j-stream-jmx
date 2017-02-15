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
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.DefaultSamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.jkoolcloud.tnt4j.utils.Utils;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * <p>
 * This class provides java agent implementation {@link #premain(String, Instrumentation)},
 * {@link #agentmain(String, Instrumentation)} as well as {@link #main(String[])} entry point to run as a standalone
 * application.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see SamplerFactory
 */
public class SamplingAgent {
	protected static Sampler platformJmx;
	protected static ConcurrentHashMap<MBeanServerConnection, Sampler> STREAM_AGENTS = new ConcurrentHashMap<MBeanServerConnection, Sampler>(89);
	protected static boolean TRACE = Boolean.getBoolean("com.jkoolcloud.tnt4j.stream.jmx.agent.trace");
	protected static boolean VALIDATE_TYPES = Boolean.getBoolean("com.jkoolcloud.tnt4j.stream.jmx.agent.validate.types");

	/**
	 * Entry point to be loaded as {@code -javaagent:jarpath="mbean-filter!sample.ms"} command line.
	 * Example:  {@code -javaagent:tnt4j-sample-jmx.jar="*:*!30000"}
	 * 
	 * @param options '!' separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param inst instrumentation handle
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter", Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", 30000);
		if (options != null) {
			String[] args = options.split("!");
			if (args.length >= 2) {
				incFilter = args[0];
				period = Integer.parseInt(args[1]);
			}
		}
		sample(incFilter, excFilter, period, TimeUnit.MILLISECONDS);
		System.out.println("SamplingAgent.premain: inlcude.filter=" + incFilter 
				+ ", exclude.filter=" + excFilter
				+ ", sample.ms=" + period 
				+ ", trace=" + TRACE 
				+ ", validate.types=" + VALIDATE_TYPES
				+ ", tnt4j.config=" + System.getProperty("tnt4j.config") 
				+ ", jmx.sample.list=" + STREAM_AGENTS);
	}

	/**
	 * Entry point to bea loaded as JVM agent. Does same as {@link #premain(String, Instrumentation)}.
	 * 
	 * @param agentArgs '!' separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param inst instrumentation handle
	 * @throws IOException if any I/O exception occurs while starting agent
	 *
	 * @see #premain(String, Instrumentation)
	 */
	public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
		String agentParams = "";
		String tnt4jProp = System.getProperty("tnt4j.config");
		String agentLibPath = "";
		if (!Utils.isEmpty(agentArgs)) {
			String[] args = agentArgs.split("!");

			if (args.length >= 2) {
				agentParams = args[0] + "!" + args[1];
			}

			for (String arg : args) {
				if (arg.startsWith("-Dtnt4j.config")) {
					if (Utils.isEmpty(tnt4jProp)) {
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

		System.out.println("SamplingAgent.agentmain: agent.params=" + agentParams 
				+ ", agent.lib.path=" + agentLibPath
				+ ", trace=" + TRACE 
				+ ", validate.types=" + VALIDATE_TYPES 
				+ ", tnt4j.config=" + System.getProperty("tnt4j.config"));

		File agentPath = new File(agentLibPath);
		final String[] classPathEntries = agentPath.list(new JarFilter());
		if (classPathEntries != null) {
			File pathFile;
			for (String classPathEntry : classPathEntries) {
				pathFile = new File(classPathEntry);
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
				System.err.println("SamplingAgent.extendClasspath: Could not load lib " + classPathEntryURL);
				System.err.println("                    Exception: " + e.getLocalizedMessage());
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
		if (args.length < 2) {
			System.out.println("Usage: mbean-filter exclude-filter sample-ms [wait-ms] (e.g \"*:*\" \"\" 10000 60000)");
			System.out.println("   or: -attach vmName/vmId agentJarPath (e.g -attach activemq [ENV_PATH]/tnt-stream-jmx.jar)");
			System.out.println("   or: -connect vmName/vmId/JMX_URL (e.g -connect activemq");
		}
		if (args[0].equalsIgnoreCase("-connect")) {
			String vmDescr = args[1];
			String agentOptions = args.length > 2 ? args[2] : "*:*!10000";

			connect(vmDescr, agentOptions);
		} else if (args[0].equalsIgnoreCase("-attach")) {
			String vmDescr = args[1];
			String jarPath = args[2];
			String agentOptions = args.length > 3 ? args[3] : "*:*!10000";

			attach(vmDescr, jarPath, agentOptions);
		} else {
			try {
				long sample_time = Integer.parseInt(args[2]);
				long wait_time = args.length > 3 ? Integer.parseInt(args[3]) : 0;
				sample(args[0], args[1], sample_time, TimeUnit.MILLISECONDS);
				System.out.println("SamplingAgent.main: inlcude.filter=" + args[0] 
						+ ", exclude.filter=" + args[1]
						+ ", sample.ms=" + sample_time 
						+ ", wait.ms=" + wait_time 
						+ ", trace=" + TRACE
						+ ", validate.types=" + VALIDATE_TYPES 
						+ ", tnt4j.config=" + System.getProperty("tnt4j.config")
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
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter", Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", 30000);
		sample(incFilter, excFilter, period);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 * 
	 * @param incFilter semicolon separated include filter list
	 * @param period sampling in milliseconds.
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
	 */
	public static void sample(String incFilter, String excFilter, long period, TimeUnit tunit) throws IOException {
		SamplerFactory pFactory = initPlatformJMX(incFilter, excFilter, period, tunit, null);

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
	 * Schedule sample using defined JMX connector to get MBean server connection instance to monitored JVM.
	 * 
	 * @param incFilter semicolon separated include filter list
	 * @param excFilter semicolon separated exclude filter list (null if empty)
	 * @param period sampling time
	 * @param tunit time units for sampling period
	 * @param conn JMX connector to get MBean server connection instance
	 */
	public static void sample(String incFilter, String excFilter, long period, TimeUnit tunit, JMXConnector conn)
			throws IOException {
		// get MBeanServerConnection from JMX RMI connector
		MBeanServerConnection mbSrvConn = conn.getMBeanServerConnection();
		SamplerFactory pFactory = initPlatformJMX(incFilter, excFilter, period, tunit, mbSrvConn);
	}

	private static SamplerFactory initPlatformJMX(String incFilter, String excFilter, long period, TimeUnit tunit,
			MBeanServerConnection mbSrvConn) throws IOException {
		// obtain a default sample factory
		SamplerFactory pFactory = DefaultSamplerFactory.getInstance();

		if (platformJmx == null) {
			// create new sampler with default MBeanServer instance
			platformJmx = mbSrvConn == null ? pFactory.newInstance() : pFactory.newInstance(mbSrvConn);
			// schedule sample with a given filter and sampling period
			platformJmx.setSchedule(incFilter, excFilter, period, tunit)
					.addListener(new DefaultSampleListener(System.out, TRACE, VALIDATE_TYPES)).run();
			STREAM_AGENTS.put(platformJmx.getMBeanServer(), platformJmx);
		}

		return pFactory;
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
		if (Utils.isEmpty(vmDescr) || Utils.isEmpty(agentJarPath)) {
			throw new RuntimeException("JVM attach VM descriptor and agent Jar path must be not empty!..");
		}

		VirtualMachineDescriptor descriptor = findVM(vmDescr);

		final VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());
		File pathFile = new File(agentJarPath);
		String agentPath = pathFile.getAbsolutePath();
		System.out.println("SamplingAgent.attach: attaching agent: " + agentPath + " to " + descriptor + "");

		agentOptions += "!trace=" + TRACE;
		agentOptions += "!validate=" + VALIDATE_TYPES;

		agentOptions += "!-DSamplingAgent.path=" + agentPath;

		String tnt4jConf = System.getProperty("tnt4j.config");

		if (!Utils.isEmpty(tnt4jConf)) {
			String tnt4jPropPath = new File(tnt4jConf).getAbsolutePath();
			agentOptions += "!-Dtnt4j.config=" + tnt4jPropPath;
		}

		System.out.println("SamplingAgent.attach: agent.path=" + agentPath 
			+ ", agent.options=" + agentOptions);

		virtualMachine.loadAgent(agentPath, agentOptions);
		virtualMachine.detach();
	}

	private static VirtualMachineDescriptor findVM(String vmDescr) {
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
			System.err.println("SamplingAgent: ----------- Available JVMs -----------");
			for (VirtualMachineDescriptor vmD : runningVMsList) {
				System.err.println("SamplingAgent: JVM.id=" + vmD.id() + ", name=" + vmD.displayName());
			}
			System.err.println("SamplingAgent: ---------------- END ----------------");
			throw new RuntimeException("Java VM not found using provided descriptor: [" + vmDescr + "]");
		}

		return descriptor;
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 * 
	 * @param vmDescr JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param options '!' separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @throws Exception if any exception occurs while connecting to JVM
	 */
	public static void connect(String vmDescr, String options) throws Exception {
		if (Utils.isEmpty(vmDescr)) {
			throw new RuntimeException("JVM attach VM descriptor must be not empty!..");
		}

		String connectorAddress;
		if (vmDescr.startsWith("service:jmx:")) {
			connectorAddress = vmDescr;
		} else {
			VirtualMachineDescriptor descriptor = findVM(vmDescr);

			final VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());

			Properties props = virtualMachine.getAgentProperties();
			connectorAddress = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
			if (connectorAddress == null) {
				throw new RuntimeException("JVM does not support JMX connection...");
			}
		}

		System.out.println("SamplingAgent.connect: making JMX service URL from address=" + connectorAddress);
		JMXServiceURL url = new JMXServiceURL(connectorAddress);

		System.out.println("SamplingAgent.connect: connecting JMX service using URL=" + url);
		JMXConnector connector = JMXConnectorFactory.connect(url);

		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter",
				Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", 30000);
		if (options != null) {
			String[] args = options.split("!");
			if (args.length > 0) {
				incFilter = args[0];
			}
			if (args.length > 1) {
				excFilter = args.length > 2 ? args[1] : Sampler.JMX_FILTER_NONE;
				period = Integer.parseInt(args.length > 2 ? args[2] : args[1]);
			}
		}

		try {
			sample(incFilter, excFilter, period, TimeUnit.MILLISECONDS, connector);

			System.out.println("SamplingAgent.connect: inlcude.filter=" + incFilter + ", exclude.filter=" + excFilter
					+ ", sample.ms=" + period + ", trace=" + TRACE + ", validate.types=" + VALIDATE_TYPES
					+ ", tnt4j.config=" + System.getProperty("tnt4j.config") + ", jmx.sample.list=" + STREAM_AGENTS);

			NotificationListener cnl = new NotificationListener() {
				@Override
				public void handleNotification(Notification notification, Object key) {
					if (notification.getType().contains("closed") || notification.getType().contains("failed")
							|| notification.getType().contains("lost")) {
						System.out.println(
								"SamplingAgent.connect: JMX connection status change: " + notification.getType());
						stopConnection();
					}
				}
			};
			connector.addConnectionNotificationListener(cnl, null, null);
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				@Override
				public void run() {
					stopConnection();
				}
			}));

			synchronized (platformJmx) {
				platformJmx.wait();
			}

			connector.removeConnectionNotificationListener(cnl);

			System.out.println("SamplingAgent.connect: Stopping Stream-JMX...");
		} finally {
			try {
				connector.close();
			} catch (IOException exc) {
			}
		}
	}

	private static void stopConnection() {
		if (platformJmx != null) {
			synchronized (platformJmx) {
				platformJmx.notifyAll();
			}
		}
	}

	/**
	 * Obtain a map of all scheduled MBeanServerConnections and associated sample references.
	 * 
	 * @return map of all scheduled MBeanServerConnections and associated sample references.
	 */
	public static Map<MBeanServerConnection, Sampler> getSamplers() {
		HashMap<MBeanServerConnection, Sampler> copy = new HashMap<MBeanServerConnection, Sampler>(89);
		copy.putAll(STREAM_AGENTS);
		return copy;
	}

	/**
	 * Cancel and close all outstanding {@link Sampler} instances and stop all sampling for all {@code MBeanServer}
	 * instances.
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
	 * @param mServerConn MBeanServerConnection instance
	 */
	public static void cancel(MBeanServerConnection mServerConn) {
		Sampler sampler = STREAM_AGENTS.remove(mServerConn);
		if (sampler != null) {
			sampler.cancel();
		}
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