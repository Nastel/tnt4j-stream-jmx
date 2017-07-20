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
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.VMUtils;

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
	protected static boolean TRACE;
	protected static boolean FORCE_OBJECT_NAME;

	private static final String PARAM_VM_DESCRIPTOR = "-vm:";
	private static final String PARAM_AGENT_LIB_PATH = "-ap:";
	private static final String PARAM_AGENT_OPTIONS = "-ao:";
	private static final String PARAM_AGENT_USER_LOGIN = "-ul:";
	private static final String PARAM_AGENT_USER_PASS = "-up:";
	private static final String PARAM_AGENT_CONN_PARAM = "-cp:";

	private static final String AGENT_MODE_AGENT = "-agent";
	private static final String AGENT_MODE_ATTACH = "-attach";
	private static final String AGENT_MODE_CONNECT = "-connect";
	private static final String AGENT_MODE_LOCAL = "-local";

	private static final String AGENT_ARG_MODE = "agent.mode";
	private static final String AGENT_ARG_VM = "vm.descriptor";
	private static final String AGENT_ARG_USER = "user.login";
	private static final String AGENT_ARG_PASS = "user.password";
	private static final String AGENT_ARG_LIB_PATH = "agent.lib.path";
	private static final String AGENT_ARG_OPTIONS = "agent.options";
	private static final String AGENT_ARG_I_FILTER = "beans.include.filter";
	private static final String AGENT_ARG_E_FILTER = "beans.exclude.filter";
	private static final String AGENT_ARG_S_TIME = "agent.sample.time";
	private static final String AGENT_ARG_D_TIME = "agent.sample.delay.time";
	private static final String AGENT_ARG_W_TIME = "agent.wait.time";

	private static final String AGENT_CONN_PARAMS = "agent.connection.params";

	private static final String DEFAULT_AGENT_OPTIONS = Sampler.JMX_FILTER_ALL + "!" + Sampler.JMX_FILTER_NONE + "!"
			+ Sampler.JMX_SAMPLE_PERIOD;

	public static final Properties DEFAULTS = new Properties();
	static {
		initDefaults(DEFAULTS);

		TRACE = getConfFlag("com.jkoolcloud.tnt4j.stream.jmx.agent.trace", false);
		FORCE_OBJECT_NAME = getConfFlag("com.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName", false);
	}

	/**
	 * Entry point to be loaded as {@code -javaagent:jarpath="mbean-filter!sample.ms!initDelay.ms"} command line where
	 * {@code initDelay} is optional. Example: {@code -javaagent:tnt4j-sample-jmx.jar="*:*!30000!1000"}
	 *
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param inst
	 *            instrumentation handle
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter",
				Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
		int initDelay = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.init.delay", period);
		if (options != null) {
			String[] args = options.split("!");
			if (args.length >= 2) {
				incFilter = args[0];
				period = Integer.parseInt(args[1]);
			}
			if (args.length >= 3) {
				initDelay = Integer.parseInt(args[2]);
			}

		}
		sample(incFilter, excFilter, initDelay, period, TimeUnit.MILLISECONDS);
		System.out.println("SamplingAgent.premain: include.filter=" + incFilter
				+ ", exclude.filter=" + excFilter
				+ ", sample.ms=" + period
				+ ", initDelay.ms=" + initDelay
				+ ", trace=" + TRACE
				+ ", forceObjectName=" + FORCE_OBJECT_NAME
				+ ", tnt4j.config=" + System.getProperty("tnt4j.config")
				+ ", jmx.sample.list=" + STREAM_AGENTS);
	}

	/**
	 * Entry point to bea loaded as JVM agent. Does same as {@link #premain(String, Instrumentation)}.
	 *
	 * @param agentArgs
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param inst
	 *            instrumentation handle
	 * @throws IOException
	 *             if any I/O exception occurs while starting agent
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
				} else if (arg.startsWith("forceObjectName=")) {
					String[] prop = arg.split("=");
					FORCE_OBJECT_NAME = prop.length > 1 ? Boolean.parseBoolean(prop[1]) : FORCE_OBJECT_NAME;
				}
			}
		}

		System.out.println("SamplingAgent.agentmain: agent.params=" + agentParams
				+ ", agent.lib.path=" + agentLibPath
				+ ", trace=" + TRACE
				+ ", forceObjectName=" + FORCE_OBJECT_NAME
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
			} else if (AGENT_MODE_LOCAL.equalsIgnoreCase(am)) {
				String agentOptions = props.getProperty(AGENT_ARG_OPTIONS, DEFAULT_AGENT_OPTIONS);

				sampleLocalVM(agentOptions, true);
			} else {
				try {
					String inclF = props.getProperty(AGENT_ARG_I_FILTER, Sampler.JMX_FILTER_ALL);
					String exclF = props.getProperty(AGENT_ARG_E_FILTER, Sampler.JMX_FILTER_NONE);
					long sample_time = Integer
							.parseInt(props.getProperty(AGENT_ARG_S_TIME, String.valueOf(Sampler.JMX_SAMPLE_PERIOD)));
					long delay_time = Integer
							.parseInt(props.getProperty(AGENT_ARG_D_TIME, String.valueOf(sample_time)));
					long wait_time = Integer.parseInt(props.getProperty(AGENT_ARG_W_TIME, "0"));
					sample(inclF, exclF, delay_time, sample_time, TimeUnit.MILLISECONDS);
					System.out.println("SamplingAgent.main: include.filter=" + inclF
							+ ", exclude.filter=" + exclF
							+ ", sample.ms=" + sample_time
							+ ", delay.ms=" + delay_time
							+ ", wait.ms=" + wait_time
							+ ", trace=" + TRACE
							+ ", forceObjectName=" + FORCE_OBJECT_NAME
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
			System.out.println("   or: -local -ao:agentOptions (e.g -local -ao:*:*!!10000");
			System.out.println();
			System.out.println("Parameters definition:");
			System.out.println("   -ao: - agent options string using '!' symbol as delimiter. Options format: mbean-filter!exclude-filter!sample-ms!init-delay-ms");
			System.out.println("       mbean-filter - MBean include name filter defined using object name pattern: domainName:keysSet");
			System.out.println("       exclude-filter - MBean exclude name filter defined using object name pattern: domainName:keysSet");
			System.out.println("       sample-ms - MBeans sampling rate in milliseconds");
			System.out.println("       init-delay-ms - MBeans sampling initial delay in milliseconds. Optional, by default it is equal to 'sample-ms' value.");

			System.exit(1);
		}
	}

	private static boolean parseArgs(Properties props, String... args) {
		boolean ac = StringUtils.equalsAnyIgnoreCase(args[0], AGENT_MODE_ATTACH, AGENT_MODE_CONNECT);
		boolean local = AGENT_MODE_LOCAL.equalsIgnoreCase(args[0]);

		if (ac || local) {
			props.setProperty(AGENT_ARG_MODE, args[0]);

			try {
				for (int ai = 1; ai < args.length; ai++) {
					String arg = args[ai];
					if (StringUtils.isEmpty(arg)) {
						continue;
					}

					if (arg.startsWith(PARAM_VM_DESCRIPTOR)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_VM))) {
							System.out.println("JVM descriptor already defined. Can not use argument ["
									+ PARAM_VM_DESCRIPTOR + "] multiple times.");
							return false;
						}

						setProperty(props, arg, PARAM_VM_DESCRIPTOR, AGENT_ARG_VM);
					} else if (arg.startsWith(PARAM_AGENT_LIB_PATH)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_LIB_PATH))) {
							System.out.println("Agent library path already defined. Can not use argument ["
									+ PARAM_AGENT_LIB_PATH + "] multiple times.");
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_LIB_PATH, AGENT_ARG_LIB_PATH);
					} else if (arg.startsWith(PARAM_AGENT_OPTIONS)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_OPTIONS))) {
							System.out.println("Agent options already defined. Can not use argument ["
									+ PARAM_AGENT_OPTIONS + "] multiple times.");
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_OPTIONS, AGENT_ARG_OPTIONS);
					} else if (arg.startsWith(PARAM_AGENT_USER_LOGIN)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_USER))) {
							System.out.println("User login already defined. Can not use argument ["
									+ PARAM_AGENT_USER_LOGIN + "] multiple times.");
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_USER_LOGIN, AGENT_ARG_USER);
					} else if (arg.startsWith(PARAM_AGENT_USER_PASS)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_PASS))) {
							System.out.println("User password already defined. Can not use argument ["
									+ PARAM_AGENT_USER_PASS + "] multiple times.");
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_USER_PASS, AGENT_ARG_PASS);
					} else if (arg.startsWith(PARAM_AGENT_CONN_PARAM)) {
						String pValue = arg.substring(PARAM_AGENT_CONN_PARAM.length());
						if (StringUtils.isEmpty(pValue)) {
							System.out.println("Missing argument '" + PARAM_AGENT_CONN_PARAM + "' value");
							return false;
						}

						String[] cp = pValue.split("=");

						if (cp.length < 2) {
							System.out.println(
									"Malformed argument '" + PARAM_AGENT_CONN_PARAM + "' value '" + pValue + "'");
							return false;
						}

						@SuppressWarnings("unchecked")
						Map<String, Object> connParams = (Map<String, Object>) props.get(AGENT_CONN_PARAMS);
						if (connParams == null) {
							connParams = new HashMap<String, Object>(5);
							props.put(AGENT_CONN_PARAMS, connParams);
						}

						connParams.put(cp[0], cp[1]);
					} else {
						System.out.println("Invalid argument: " + arg);
						return false;
					}
				}
			} catch (IllegalArgumentException exc) {
				System.out.println(exc.getLocalizedMessage());
				return false;
			}

			if (StringUtils.isEmpty(props.getProperty(AGENT_ARG_VM)) && ac) {
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
			if (args.length > 4) {
				props.setProperty(AGENT_ARG_D_TIME, args[4]);
			}
		}

		return true;
	}

	private static void setProperty(Properties props, String arg, String argName, String agentArgName)
			throws IllegalArgumentException {
		String pValue = arg.substring(argName.length());
		if (StringUtils.isEmpty(pValue)) {
			throw new IllegalArgumentException("Missing argument '" + argName + "' value");
		}

		props.setProperty(agentArgName, pValue);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 */
	public static void sample() throws IOException {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter", Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
		int initDelay = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.init.delay", period);
		sample(incFilter, excFilter, initDelay, period);
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
		sample(incFilter, excFilter, period, period);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @param incFilter semicolon separated include filter list
	 * @param excFilter semicolon separated exclude filter list (null if empty)
	 * @param initDelay initial delay before first sampling
	 * @param period sampling in milliseconds.
	 */
	public static void sample(String incFilter, String excFilter, long initDelay, long period) throws IOException {
		sample(incFilter, excFilter, initDelay, period, TimeUnit.MILLISECONDS);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @param incFilter semicolon separated include filter list
	 * @param excFilter semicolon separated exclude filter list (null if empty)
	 * @param initDelay initial delay before first sampling
	 * @param period sampling time
	 * @param tUnit time units for sampling period
	 */
	public static void sample(String incFilter, String excFilter, long initDelay, long period, TimeUnit tUnit) throws IOException {
		SamplerFactory sFactory = initPlatformJMX(incFilter, excFilter, initDelay, period, tUnit, null);

		// find other registered MBean servers and initiate sampling for all
		ArrayList<MBeanServer> mbsList = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server : mbsList) {
			Sampler jmxSampler = STREAM_AGENTS.get(server);
			if (jmxSampler == null) {
				jmxSampler = sFactory.newInstance(server);
				jmxSampler.setSchedule(incFilter, excFilter, initDelay, period, tUnit, sFactory)
						.addListener(sFactory.newListener(System.out, TRACE, FORCE_OBJECT_NAME)).run();
				STREAM_AGENTS.put(jmxSampler.getMBeanServer(), jmxSampler);
			}
		}
	}

	/**
	 * Schedule sample using defined JMX connector to get MBean server connection instance to monitored JVM.
	 *
	 * @param incFilter semicolon separated include filter list
	 * @param excFilter semicolon separated exclude filter list (null if empty)
	 * @param initDelay initial delay before first sampling
	 * @param period sampling time
	 * @param tUnit time units for sampling period
	 * @param conn JMX connector to get MBean server connection instance
	 */
	public static void sample(String incFilter, String excFilter, long initDelay, long period, TimeUnit tUnit,
			JMXConnector conn) throws IOException {
		// get MBeanServerConnection from JMX RMI connector
		MBeanServerConnection mbSrvConn = conn.getMBeanServerConnection();
		initPlatformJMX(incFilter, excFilter, initDelay, period, tUnit, mbSrvConn);
	}

	private static SamplerFactory initPlatformJMX(String incFilter, String excFilter, long initDelay, long period,
			TimeUnit tUnit, MBeanServerConnection mbSrvConn) throws IOException {
		// obtain a default sample factory
		SamplerFactory sFactory = DefaultSamplerFactory
				.getInstance(Utils.getConfProperty(DEFAULTS, "com.jkoolcloud.tnt4j.stream.jmx.sampler.factory"));

		if (platformJmx == null) {
			// create new sampler with default MBeanServer instance
			platformJmx = mbSrvConn == null ? sFactory.newInstance() : sFactory.newInstance(mbSrvConn);
			// schedule sample with a given filter and sampling period
			platformJmx.setSchedule(incFilter, excFilter, initDelay, period, tUnit, sFactory)
					.addListener(sFactory.newListener(System.out, TRACE, FORCE_OBJECT_NAME)).run();
			STREAM_AGENTS.put(platformJmx.getMBeanServer(), platformJmx);
		}

		return sFactory;
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
		agentOptions += "!forceObjectName=" + FORCE_OBJECT_NAME;

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
	 * @param options '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon separated list of mbean filters and {@code initDelay} is optional
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
	 * @param options '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon separated list of mbean filters and {@code initDelay} is optional
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
	 * @param options '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon separated list of mbean filters and {@code initDelay} is optional
	 * @throws Exception if any exception occurs while connecting to JVM
	 */
	public static void connect(String vmDescr, String user, String pass, String options) throws Exception {
		connect(vmDescr, user, pass, options, null);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param user user login used by JMX service connection
	 * @param pass user password used by JMX service connection
	 * @param options '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon separated list of mbean filters and {@code initDelay} is optional
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

		System.out.println("SamplingAgent.connect: making JMX service URL from address=" + connectorAddress);
		JMXServiceURL url = new JMXServiceURL(connectorAddress);

		Map<String, Object> params = new HashMap<String, Object>(connParams == null ? 1 : connParams.size() + 1);

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
			NotificationListener cnl = new NotificationListener() {
				@Override
				public void handleNotification(Notification notification, Object key) {
					if (notification.getType().contains("closed") || notification.getType().contains("failed")
							|| notification.getType().contains("lost")) {
						System.out.println(
								"SamplingAgent.connect: JMX connection status change: " + notification.getType());
						stopPlatformJMX();
					}
				}
			};
			connector.addConnectionNotificationListener(cnl, null, null);

			startSamplerAndWait(options, connector);

			connector.removeConnectionNotificationListener(cnl);
		} finally {
			Utils.close(connector);
		}
	}

	private static void stopPlatformJMX() {
		System.out.println("SamplingAgent.stopPlatformJMX: releasing sampler lock...");

		if (platformJmx != null) {
			synchronized (platformJmx) {
				platformJmx.notifyAll();
			}
		}
	}

	/**
	 * Initiates MBeans attributes sampling on local process runner JVM.
	 * 
	 * @param options '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon separated list of mbean filters and {@code initDelay} is optional
	 * @param wait flag indicating whether to wait for a runner process to complete
	 * @throws Exception if any exception occurs while initializing local JVM sampler or stopping sampling process
	 */
	public static void sampleLocalVM(String options, boolean wait) throws Exception {
		if (wait) {
			startSamplerAndWait(options);
		} else {
			startSampler(options);
		}
	}

	private static void startSamplerAndWait(String options) throws Exception {
		startSamplerAndWait(options, null);
	}

	private static void startSamplerAndWait(String options, JMXConnector connector) throws Exception {
		startSampler(options, connector);

		final Thread mainThread = Thread.currentThread(); // Reference to the current thread.
		Thread shutdownHook = new Thread(new Runnable() {
			@Override
			public void run() {
				stopPlatformJMX();

				long startTime = System.currentTimeMillis();
				try {
					mainThread.join(TimeUnit.SECONDS.toMillis(2));
				} catch (Exception exc) {
				}
				System.out.println("SamplingAgent.startSamplerAndWait: waited "
						+ (System.currentTimeMillis() - startTime) + "ms. on Stream-JMX to complete...");
			}
		});
		Runtime.getRuntime().addShutdownHook(shutdownHook);

		synchronized (platformJmx) {
			platformJmx.wait();
		}

		System.out.println("SamplingAgent.startSamplerAndWait: Stopping Stream-JMX...");
		try {
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		} catch (IllegalStateException exc) {
		}
	}

	private static void startSampler(String options) throws Exception {
		startSampler(options, null);
	}

	private static void startSampler(String options, JMXConnector connector) throws Exception {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter", Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
		int initDelay = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.init.delay", period);
		if (options != null) {
			String[] args = options.split("!");
			if (args.length > 0) {
				incFilter = args[0];
			}
			if (args.length > 1) {
				excFilter = args.length > 2 ? args[1] : Sampler.JMX_FILTER_NONE;
				period = Integer.parseInt(args.length > 2 ? args[2] : args[1]);
			}
			if (args.length > 2) {
				initDelay = Integer.parseInt(args.length > 3 ? args[3] : args[2]);
			}
		}

		TRACE = getConfFlag("com.jkoolcloud.tnt4j.stream.jmx.agent.trace", TRACE);
		FORCE_OBJECT_NAME = getConfFlag("com.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName", FORCE_OBJECT_NAME);

		if (connector == null) {
			sample(incFilter, excFilter, initDelay, period, TimeUnit.MILLISECONDS);
		} else {
			sample(incFilter, excFilter, initDelay, period, TimeUnit.MILLISECONDS, connector);
		}

		System.out.println("SamplingAgent.startSampler: include.filter=" + incFilter 
				+ ", exclude.filter=" + excFilter
				+ ", sample.ms=" + period 
				+ ", initDelay.ms=" + initDelay 
				+ ", trace=" + TRACE 
				+ ", forceObjectName=" + FORCE_OBJECT_NAME
				+ ", tnt4j.config=" + System.getProperty("tnt4j.config") 
				+ ", jmx.sample.list=" + STREAM_AGENTS);
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
	 * Cancel and close all outstanding {@link Sampler} instances and stop all sampling for all {@link MBeanServer}
	 * instances.
	 */
	public static void cancel() {
		for (Sampler sampler : STREAM_AGENTS.values()) {
			sampler.cancel();
		}
		STREAM_AGENTS.clear();
	}

	/**
	 * Cancel and close all sampling for a given {@link MBeanServer} instance.
	 *
	 * @param mServerConn MBeanServerConnection instance
	 */
	public static void cancel(MBeanServerConnection mServerConn) {
		Sampler sampler = STREAM_AGENTS.remove(mServerConn);
		if (sampler != null) {
			sampler.cancel();
		}
	}

	/**
	 * Stops platform JMX sampler, cancels and close all outstanding {@link Sampler} instances and stop all sampling for
	 * all {@link MBeanServer} instances.
	 *
	 * @see #cancel()
	 */
	public static void destroy() {
		stopPlatformJMX();
		cancel();
		// TrackingLogger.shutdownAll();
		platformJmx = null;
	}

	private static void initDefaults(Properties defProps) {
		Map<String, Properties> defPropsMap = new HashMap<String, Properties>();
		String dPropsKey = null;
		int apiLevel = 0;

		try {
			Properties p = Utils.loadPropertiesResources("sjmx-defaults.properties");

			String key;
			String pKey;
			for (Map.Entry<?, ?> pe : p.entrySet()) {
				pKey = (String) pe.getKey();
				int di = pKey.indexOf("/");
				if (di >= 0) {
					key = pKey.substring(0, di);
					pKey = pKey.substring(di + 1);
				} else {
					key = "";
				}

				if ("api.level".equals(pKey)) {
					int peApiLevel = 0;
					try {
						peApiLevel = Integer.parseInt((String) pe.getValue());
					} catch (NumberFormatException exc) {
					}

					if (peApiLevel > apiLevel) {
						apiLevel = peApiLevel;
						dPropsKey = key;
					}

					continue;
				}

				Properties dpe = defPropsMap.get(key);

				if (dpe == null) {
					dpe = new Properties();
					defPropsMap.put(key, dpe);
				}

				dpe.setProperty(pKey, (String) pe.getValue());
			}
		} catch (Exception exc) {
		}

		if (dPropsKey != null) {
			defProps.putAll(defPropsMap.get(dPropsKey));
		}
	}

	private static boolean getConfFlag(String pName, boolean defValue) {
		String flagValue = Utils.getConfProperty(DEFAULTS, pName, String.valueOf(defValue));
		return Boolean.parseBoolean(flagValue);
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