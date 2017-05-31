/*
 * Copyright 2015-2017 JKOOL, LLC.
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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.DefaultSamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.jkoolcloud.tnt4j.utils.Utils;

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
	protected static ConcurrentHashMap<MBeanServerConnection, Sampler> STREAM_AGENTS = new ConcurrentHashMap<>(89);
	protected static boolean TRACE = Boolean.getBoolean("com.jkoolcloud.tnt4j.stream.jmx.agent.trace");

	private static final String PARAM_VM_DESCRIPTOR = "-vm:";
	private static final String PARAM_AGENT_LIB_PATH = "-ap:";
	private static final String PARAM_AGENT_OPTIONS = "-ao:";
	private static final String PARAM_AGENT_USER_LOGIN = "-ul:";
	private static final String PARAM_AGENT_USER_PASS = "-up:";
	private static final String PARAM_AGENT_CONN_PARAM = "-cp:";

	private static final String AGENT_MODE_AGENT = "-agent";
	private static final String AGENT_MODE_ATTACH = "-attach";
	private static final String AGENT_MODE_CONNECT = "-connect";

	private static final String AGENT_ARG_MODE = "agent.mode";
	private static final String AGENT_ARG_VM = "vm.descriptor";
	private static final String AGENT_ARG_USER = "user.login";
	private static final String AGENT_ARG_PASS = "user.password";
	private static final String AGENT_ARG_LIB_PATH = "agent.lib.path";
	private static final String AGENT_ARG_OPTIONS = "agent.options";
	private static final String AGENT_ARG_I_FILTER = "beans.include.filter";
	private static final String AGENT_ARG_E_FILTER = "beans.exclude.filter";
	private static final String AGENT_ARG_S_TIME = "agent.sample.time";
	private static final String AGENT_ARG_W_TIME = "agent.wait.time";

	private static final String AGENT_CONN_PARAMS = "agent.connection.params";

	private static final String DEFAULT_AGENT_OPTIONS = Sampler.JMX_FILTER_ALL + "!" + Sampler.JMX_FILTER_NONE + "!"
			+ Sampler.JMX_SAMPLE_PERIOD;

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
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
		if (options != null) {
			String[] args = options.split("!");
			if (args.length >= 2) {
				incFilter = args[0];
				period = Integer.parseInt(args[1]);
			}
		}
		sample(incFilter, excFilter, period, TimeUnit.MILLISECONDS);
		System.out.println("SamplingAgent.premain: include.filter=" + incFilter 
				+ ", exclude.filter=" + excFilter
				+ ", sample.ms=" + period 
				+ ", trace=" + TRACE 
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
				}
			}
		}

		System.out.println("SamplingAgent.agentmain: agent.params=" + agentParams 
				+ ", agent.lib.path=" + agentLibPath
				+ ", trace=" + TRACE 
				+ ", tnt4j.config=" + System.getProperty("tnt4j.config"));

		File agentPath = new File(agentLibPath);
		final String[] classPathEntries = agentPath.list(new JarFilter());
		if (classPathEntries != null) {
			File pathFile;
			for (String classPathEntry : classPathEntries) {
				pathFile = new File(classPathEntry);
				System.out.println("SamplingAgent.agentmain: extending classpath with: " + pathFile.getAbsolutePath());
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
	 *            argument list: [sampling-mode vm-descriptor] [mbean-filter sample_time_ms]
	 */
	public static void main(String[] args) throws Exception {
		Properties props = new Properties();
		boolean argsValid = parseArgs(props, args);

		if (argsValid) {
			String am = props.getProperty(AGENT_ARG_MODE);
			if (AGENT_MODE_CONNECT.equalsIgnoreCase(am)) {
				String vmDescr = props.getProperty(AGENT_ARG_VM);
				String user = props.getProperty(AGENT_ARG_USER);
				String pass = props.getProperty(AGENT_ARG_PASS);
				String agentOptions = props.getProperty(AGENT_ARG_OPTIONS, DEFAULT_AGENT_OPTIONS);
				@SuppressWarnings("unchecked")
				Map<String, ?> connParams = (Map<String, ?>) props.get(AGENT_CONN_PARAMS);

				connect(vmDescr, user, pass, agentOptions, connParams);
			} else if (AGENT_MODE_ATTACH.equalsIgnoreCase(am)) {
				String vmDescr = props.getProperty(AGENT_ARG_VM);
				String jarPath = props.getProperty(AGENT_ARG_LIB_PATH);
				String agentOptions = props.getProperty(AGENT_ARG_OPTIONS, DEFAULT_AGENT_OPTIONS);

				attach(vmDescr, jarPath, agentOptions);
			} else {
				try {
					String inclF = props.getProperty(AGENT_ARG_I_FILTER, Sampler.JMX_FILTER_ALL);
					String exclF = props.getProperty(AGENT_ARG_E_FILTER, Sampler.JMX_FILTER_NONE);
					long sample_time = Integer
							.parseInt(props.getProperty(AGENT_ARG_S_TIME, String.valueOf(Sampler.JMX_SAMPLE_PERIOD)));
					long wait_time = Integer.parseInt(props.getProperty(AGENT_ARG_W_TIME, "0"));
					sample(inclF, exclF, sample_time, TimeUnit.MILLISECONDS);
					System.out.println("SamplingAgent.main: include.filter=" + inclF 
							+ ", exclude.filter=" + exclF
							+ ", sample.ms=" + sample_time 
							+ ", wait.ms=" + wait_time 
							+ ", trace=" + TRACE
							+ ", tnt4j.config=" + System.getProperty("tnt4j.config") 
							+ ", jmx.sample.list=" + STREAM_AGENTS);
					synchronized (platformJmx) {
						platformJmx.wait(wait_time);
					}
				} catch (Throwable ex) {
					ex.printStackTrace();
				}
			}
		} else {
			System.out.println("Usage: mbean-filter exclude-filter sample-ms [wait-ms] (e.g \"*:*\" \"\" 10000 60000)");
			System.out.println("   or: -attach -vm:vmName/vmId -ap:agentJarPath -ao:agentOptions (e.g -attach -vm:activemq -ap:[ENV_PATH]/tnt-stream-jmx.jar -ao:*:*!!10000)");
			System.out.println("   or: -connect -vm:vmName/vmId/JMX_URL -ao:agentOptions (e.g -connect -vm:activemq -ao:*:*!!10000");
			System.out.println("   or: -connect -vm:vmName/vmId/JMX_URL -ul:userLogin -up:userPassword -ao:agentOptions (e.g -connect -vm:activemq -ul:admin -up:admin -ao:*:*!!10000");
			System.out.println("   or: -connect -vm:vmName/vmId/JMX_URL -ul:userLogin -up:userPassword -ao:agentOptions -cp:jmcConnParam1 -cp:jmcConnParam2 -cp:... (e.g -connect -vm:activemq -ul:admin -up:admin -ao:*:*!!10000 -cp:javax.net.ssl.trustStorePassword=trustPass");
			System.out.println();
			System.out.println("Parameters definition:");
			System.out.println("   -ao: - agent options string using '!' symbol as delimiter. Options format: mbean-filter!exclude-filter!sample-ms");
			System.out.println("       mbean-filter - MBean include name filter defined using object name pattern: domainName:keysSet");
			System.out.println("       exclude-filter - MBean exclude name filter defined using object name pattern: domainName:keysSet");
			System.out.println("       sample-ms - MBeans sampling rate in milliseconds");

			System.exit(1);
		}
	}

	private static boolean parseArgs(Properties props, String... args) {
		boolean ac = AGENT_MODE_ATTACH.equalsIgnoreCase(args[0]) || AGENT_MODE_CONNECT.equalsIgnoreCase(args[0]);

		if (ac) {
			props.setProperty(AGENT_ARG_MODE, args[0]);

			for (int ai = 1; ai < args.length; ai++) {
				String arg = args[ai];
				if (StringUtils.isEmpty(arg)) {
					continue;
				}

				if (arg.startsWith(PARAM_VM_DESCRIPTOR)) {
					if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_VM))) {
						System.out.println("JVM descriptor already defined. Can not use argument [" + PARAM_VM_DESCRIPTOR 
								+ "] multiple times.");
						return false;
					}

					String pValue = arg.substring(PARAM_VM_DESCRIPTOR.length());
					if (StringUtils.isEmpty(pValue)) {
						System.out.println("Missing argument '" + PARAM_VM_DESCRIPTOR + "' value");
						return false;
					}

					props.setProperty(AGENT_ARG_VM, pValue);
				} else if (arg.startsWith(PARAM_AGENT_LIB_PATH)) {
					if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_LIB_PATH))) {
						System.out.println("Agent library path already defined. Can not use argument ["
								+ PARAM_AGENT_LIB_PATH + "] multiple times.");
						return false;
					}

					String pValue = arg.substring(PARAM_AGENT_LIB_PATH.length());
					if (StringUtils.isEmpty(pValue)) {
						System.out.println("Missing argument '" + PARAM_AGENT_LIB_PATH + "' value");
						return false;
					}

					props.setProperty(AGENT_ARG_LIB_PATH, pValue);
				} else if (arg.startsWith(PARAM_AGENT_OPTIONS)) {
					if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_OPTIONS))) {
						System.out.println("Agent options already defined. Can not use argument [" + PARAM_AGENT_OPTIONS
								+ "] multiple times.");
						return false;
					}

					String pValue = arg.substring(PARAM_AGENT_OPTIONS.length());
					if (StringUtils.isEmpty(pValue)) {
						System.out.println("Missing argument '" + PARAM_AGENT_OPTIONS + "' value");
						return false;
					}

					props.setProperty(AGENT_ARG_OPTIONS, pValue);
				} else if (arg.startsWith(PARAM_AGENT_USER_LOGIN)) {
					if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_USER))) {
						System.out.println("User login already defined. Can not use argument [" + PARAM_AGENT_USER_LOGIN
								+ "] multiple times.");
						return false;
					}

					String pValue = arg.substring(PARAM_AGENT_USER_LOGIN.length());
					if (StringUtils.isEmpty(pValue)) {
						System.out.println("Missing argument '" + PARAM_AGENT_USER_LOGIN + "' value");
						return false;
					}

					props.setProperty(AGENT_ARG_USER, pValue);
				} else if (arg.startsWith(PARAM_AGENT_USER_PASS)) {
					if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_PASS))) {
						System.out.println("User password already defined. Can not use argument ["
								+ PARAM_AGENT_USER_PASS + "] multiple times.");
						return false;
					}

					String pValue = arg.substring(PARAM_AGENT_USER_PASS.length());
					if (StringUtils.isEmpty(pValue)) {
						System.out.println("Missing argument '" + PARAM_AGENT_USER_PASS + "' value");
						return false;
					}

					props.setProperty(AGENT_ARG_PASS, pValue);
				} else if (arg.startsWith(PARAM_AGENT_CONN_PARAM)) {
					String pValue = arg.substring(PARAM_AGENT_CONN_PARAM.length());
					if (StringUtils.isEmpty(pValue)) {
						System.out.println("Missing argument '" + PARAM_AGENT_CONN_PARAM + "' value");
						return false;
					}

					String[] cp = pValue.split("=");

					if (cp.length < 2) {
						System.out
								.println("Malformed argument '" + PARAM_AGENT_CONN_PARAM + "' value '" + pValue + "'");
						return false;
					}

					@SuppressWarnings("unchecked")
					Map<String, Object> connParams = (Map<String, Object>) props.get(AGENT_CONN_PARAMS);
					if (connParams == null) {
						connParams = new HashMap<>(5);
						props.put(AGENT_CONN_PARAMS, connParams);
					}

					connParams.put(cp[0], cp[1]);
				} else {
					System.out.println("Invalid argument: " + arg);
					return false;
				}
			}

			if (StringUtils.isEmpty(props.getProperty(AGENT_ARG_VM))) {
				System.out.println("Missing mandatory argument '" + PARAM_VM_DESCRIPTOR + "' defining JVM descriptor.");
				return false;
			}

			// if (AGENT_MODE_ATTACH.equalsIgnoreCase(props.getProperty(AGENT_ARG_MODE))
			// && StringUtils.isEmpty(props.getProperty(AGENT_ARG_LIB_PATH))) {
			// System.out.println(
			// "Missing mandatory argument '" + PARAM_AGENT_LIB_PATH + "' defining agent library path.");
			// return false;
			// }
		} else {
			props.setProperty(AGENT_ARG_MODE, AGENT_MODE_AGENT);

			props.setProperty(AGENT_ARG_I_FILTER, args[0]);
			props.setProperty(AGENT_ARG_E_FILTER, args[1]);
			props.setProperty(AGENT_ARG_S_TIME, args[2]);
			if (args.length > 3) {
				props.setProperty(AGENT_ARG_W_TIME, args[3]);
			}
		}

		return true;
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 */
	public static void sample() throws IOException {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter", Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
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
	 * @param tUnit time units for sampling period
	 */
	public static void sample(String incFilter, String excFilter, long period, TimeUnit tUnit) throws IOException {
		SamplerFactory pFactory = initPlatformJMX(incFilter, excFilter, period, tUnit, null);

		// find other registered MBean servers and initiate sampling for all
		ArrayList<MBeanServer> mbsList = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server : mbsList) {
			Sampler jmxSampler = STREAM_AGENTS.get(server);
			if (jmxSampler == null) {
				jmxSampler = pFactory.newInstance(server);
				jmxSampler.setSchedule(incFilter, excFilter, period, tUnit)
						.addListener(new DefaultSampleListener(System.out, TRACE)).run();
				STREAM_AGENTS.put(jmxSampler.getMBeanServer(), jmxSampler);
			}
		}
	}

	/**
	 * Schedule sample using defined JMX connector to get MBean server connection instance to monitored JVM.
	 * 
	 * @param incFilter semicolon separated include filter list
	 * @param excFilter semicolon separated exclude filter list (null if empty)
	 * @param period sampling time
	 * @param tUnit time units for sampling period
	 * @param conn JMX connector to get MBean server connection instance
	 */
	public static void sample(String incFilter, String excFilter, long period, TimeUnit tUnit, JMXConnector conn)
			throws IOException {
		// get MBeanServerConnection from JMX RMI connector
		MBeanServerConnection mbSrvConn = conn.getMBeanServerConnection();
		SamplerFactory pFactory = initPlatformJMX(incFilter, excFilter, period, tUnit, mbSrvConn);
	}

	private static SamplerFactory initPlatformJMX(String incFilter, String excFilter, long period, TimeUnit tUnit,
			MBeanServerConnection mbSrvConn) throws IOException {
		// obtain a default sample factory
		SamplerFactory pFactory = DefaultSamplerFactory.getInstance();

		if (platformJmx == null) {
			// create new sampler with default MBeanServer instance
			platformJmx = mbSrvConn == null ? pFactory.newInstance() : pFactory.newInstance(mbSrvConn);
			// schedule sample with a given filter and sampling period
			platformJmx.setSchedule(incFilter, excFilter, period, tUnit)
					.addListener(new DefaultSampleListener(System.out, TRACE)).run();
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
		if (Utils.isEmpty(vmDescr)) {
			throw new RuntimeException("Java VM descriptor must be not empty!..");
		}

		File pathFile;
		if (StringUtils.isEmpty(agentJarPath)) {
			System.out.println("SamplingAgent.attach: no agent jar defined");
			pathFile = getSAPath();
		} else {
			pathFile = new File(agentJarPath);
			if (!pathFile.exists()) {
				System.out.println("SamplingAgent.attach: non-existing argument defined agent jar: " + agentJarPath);
				System.out.println("                      absolute agent jar path: " + pathFile.getAbsolutePath());
				pathFile = getSAPath();
			}
		}

		String agentPath = pathFile.getAbsolutePath();

		agentOptions += "!trace=" + TRACE;

		agentOptions += "!-DSamplingAgent.path=" + agentPath;

		String tnt4jConf = System.getProperty("tnt4j.config");

		if (!Utils.isEmpty(tnt4jConf)) {
			String tnt4jPropPath = new File(tnt4jConf).getAbsolutePath();
			agentOptions += "!-Dtnt4j.config=" + tnt4jPropPath;
		}

		VMUtils.attachVM(vmDescr, agentPath, agentOptions);
	}

	private static File getSAPath() throws URISyntaxException {
		String saPath = SamplingAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
		System.out.println("SamplingAgent.attach: using SamplingAgent class referenced jar path: " + saPath);
		File agentFile = new File(saPath);

		if (!agentFile.exists()) {
			throw new RuntimeException("Could not find agent jar: " + agentFile.getAbsolutePath());
		}

		return agentFile;
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
		connect(vmDescr, null, null, options, null);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param options '!' separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param connParams map of JMX connection parameters
	 * @throws Exception if any exception occurs while connecting to JVM
	 */
	public static void connect(String vmDescr, String options, Map<String, ?> connParams) throws Exception {
		connect(vmDescr, null, null, options, connParams);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param user user login used by JMX service connection
	 * @param pass user password used by JMX service connection
	 * @param options '!' separated list of options mbean-filter!sample.ms, where mbean-filter is semicolon separated list of mbean filters
	 * @param connParams map of JMX connection parameters
	 * @throws Exception if any exception occurs while connecting to JVM
	 */
	public static void connect(String vmDescr, String user, String pass, String options, Map<String, ?> connParams)
			throws Exception {
		if (Utils.isEmpty(vmDescr)) {
			throw new RuntimeException("Java VM descriptor must be not empty!..");
		}

		String connectorAddress;
		if (vmDescr.startsWith("service:jmx:")) {
			connectorAddress = vmDescr;
		} else {
			connectorAddress = VMUtils.getVMConnAddress(vmDescr);
		}

		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter",
				Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
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

		System.out.println("SamplingAgent.connect: making JMX service URL from address=" + connectorAddress);
		JMXServiceURL url = new JMXServiceURL(connectorAddress);

		Map<String, Object> params = new HashMap<>(connParams == null ? 1 : connParams.size() + 1);

		if (StringUtils.isNotEmpty(user) && StringUtils.isNotEmpty(pass)) {
			System.out.println("SamplingAgent.connect: adding user login and password to connection credentials...");
			String[] credentials = new String[] { user, pass };
			params.put(JMXConnector.CREDENTIALS, credentials);
		}

		if (connParams != null) {
			params.putAll(connParams);
		}

		System.out.println("SamplingAgent.connect: connecting JMX service using URL=" + url);
		JMXConnector connector = JMXConnectorFactory.connect(url, params);

		try {
			sample(incFilter, excFilter, period, TimeUnit.MILLISECONDS, connector);

			System.out.println("SamplingAgent.connect: include.filter=" + incFilter 
					+ ", exclude.filter=" + excFilter
					+ ", sample.ms=" + period 
					+ ", trace=" + TRACE 
					+ ", tnt4j.config=" + System.getProperty("tnt4j.config") 
					+ ", jmx.sample.list=" + STREAM_AGENTS);

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
		HashMap<MBeanServerConnection, Sampler> copy = new HashMap<>(89);
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
		JarFilter() {
		}

		@Override
		public boolean accept(File dir, String name) {
			String nfn = name.toLowerCase();
			return nfn.endsWith(".jar") || nfn.endsWith(".zip");
		}
	}
}