/*
 * Copyright 2015-2018 JKOOL, LLC.
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

import static com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener.ListenerProperties.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.management.*;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.DefaultEventSinkFactory;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.PropertyNameBuilder;
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
	private static final EventSink LOGGER = DefaultEventSinkFactory.defaultEventSink(SamplingAgent.class);
	public static final String SOURCE_SERVER_ADDRESS = "sjmx.serverAddress";
	public static final String SOURCE_SERVER_NAME = "sjmx.serverName";

	protected static Sampler platformJmx;
	protected static ConcurrentHashMap<MBeanServerConnection, Sampler> STREAM_AGENTS = new ConcurrentHashMap<MBeanServerConnection, Sampler>(
			89);
	protected static final long CONN_RETRY_INTERVAL = 10;

	private static final String LOG4J_PROPERTIES_KEY = "log4j.configuration";
	private static final String SYS_PROP_LOG4J_CFG = "-D" + LOG4J_PROPERTIES_KEY;
	private static final String SYS_PROP_TNT4J_CFG = "-D" + TrackerConfigStore.TNT4J_PROPERTIES_KEY;
	private static final String SYS_PROP_AGENT_PATH = "-DSamplingAgent.path";

	private static final String PARAM_VM_DESCRIPTOR = "-vm:";
	private static final String PARAM_AGENT_LIB_PATH = "-ap:";
	private static final String PARAM_AGENT_OPTIONS = "-ao:";
	private static final String PARAM_AGENT_USER_LOGIN = "-ul:";
	private static final String PARAM_AGENT_USER_PASS = "-up:";
	private static final String PARAM_AGENT_CONN_PARAM = "-cp:";
	private static final String PARAM_AGENT_CONN_RETRY_INTERVAL = "-cri:";
	private static final String PARAM_AGENT_LISTENER_PARAM = "-slp:";
	private static final String PARAM_AGENT_SYSTEM_PROPERTY = "-sp:";

	private static final String AGENT_MODE_AGENT = "-agent";
	private static final String AGENT_MODE_ATTACH = "-attach";
	private static final String AGENT_MODE_CONNECT = "-connect";
	private static final String AGENT_MODE_LOCAL = "-local";

	private static final String AGENT_ARG_MODE = "agent.mode";
	private static final String AGENT_ARG_VM = "vm.descriptor";
	private static final String AGENT_ARG_USER = "user.login";
	private static final String AGENT_ARG_PASS = "user.password";
	private static final String AGENT_ARG_CONN_RETRY_INTERVAL = "conn.retry.interval";
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
	public static final Map<String, Object> LISTENER_PROPERTIES = new HashMap<String, Object>(5);
	static {
		initDefaults(DEFAULTS);

		copyProperty(FORCE_OBJECT_NAME, DEFAULTS, LISTENER_PROPERTIES, false);
		copyProperty(COMPOSITE_DELIMITER, DEFAULTS, LISTENER_PROPERTIES, PropertyNameBuilder.DEFAULT_COMPOSITE_DELIMITER);
		copyProperty(USE_OBJECT_NAME_PROPERTIES, DEFAULTS, LISTENER_PROPERTIES, true);

		copyProperty(FORCE_OBJECT_NAME, System.getProperties(), LISTENER_PROPERTIES);
		copyProperty(COMPOSITE_DELIMITER, System.getProperties(), LISTENER_PROPERTIES);
		copyProperty(USE_OBJECT_NAME_PROPERTIES, System.getProperties(), LISTENER_PROPERTIES);
	}

	private static boolean stopSampling = false;

	/**
	 * Entry point to be loaded as {@code -javaagent:jarpath="mbean-filter!sample.ms!initDelay.ms"} command line where
	 * {@code initDelay} is optional. Example: {@code -javaagent:tnt4j-sample-jmx.jar="*:*!30000!1000"}
	 *
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param inst
	 *            instrumentation handle
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter",
				Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
		int initDelay = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.init.delay", period);
		if (options != null) {
			String[] args = options.split("!");
			if (args.length > 0) {
				incFilter = args[0];
			}
			if (args.length > 1) {
				excFilter = args.length > 2 ? args[1] : excFilter;
				period = Integer.parseInt(args.length > 2 ? args[2] : args[1]);
			}
			if (args.length > 2) {
				initDelay = Integer.parseInt(args.length > 3 ? args[3] : args[2]);
			}
		}
		sample(incFilter, excFilter, initDelay, period, TimeUnit.MILLISECONDS);
		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.premain: include.filter={0}, exclude.filter={1}, sample.ms={2}, initDelay.ms={3}, listener.properties={4}, tnt4j.config={5}, jmx.sample.list={6}",
				incFilter, excFilter, period, initDelay, LISTENER_PROPERTIES,
				System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY), STREAM_AGENTS);
	}

	/**
	 * Entry point to be loaded as JVM agent. Does same as {@link #premain(String, Instrumentation)}.
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
		LOGGER.log(OpLevel.INFO, "SamplingAgent.agentmain(): agentArgs={0}", agentArgs);
		String agentParams = "";
		String tnt4jProp = System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY);
		String log4jProp = System.getProperty(LOG4J_PROPERTIES_KEY);
		String agentLibPath = "";
		if (!Utils.isEmpty(agentArgs)) {
			String[] args = agentArgs.split("!");

			for (String arg : args) {
				if (arg.startsWith(SYS_PROP_TNT4J_CFG)) {
					if (Utils.isEmpty(tnt4jProp)) {
						String[] prop = arg.split("=", 2);
						tnt4jProp = prop.length > 1 ? prop[1] : null;

						System.setProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY, tnt4jProp);
					}
				} else if (arg.startsWith(SYS_PROP_AGENT_PATH)) {
					String[] prop = arg.split("=", 2);
					agentLibPath = prop.length > 1 ? prop[1] : null;
				}
				if (arg.startsWith(SYS_PROP_LOG4J_CFG)) {
					if (Utils.isEmpty(log4jProp)) {
						String[] prop = arg.split("=", 2);
						log4jProp = prop.length > 1 ? prop[1] : null;

						System.setProperty(LOG4J_PROPERTIES_KEY, log4jProp);
					}
				} else if (arg.startsWith(FORCE_OBJECT_NAME.pName() + "=")) {
					String[] prop = arg.split("=", 2);
					if (prop.length > 1) {
						LISTENER_PROPERTIES.put(FORCE_OBJECT_NAME.pName(), prop[1]);
					}
				} else if (arg.startsWith(COMPOSITE_DELIMITER.pName() + "=")) {
					String[] prop = arg.split("=", 2);
					if (prop.length > 1) {
						LISTENER_PROPERTIES.put(COMPOSITE_DELIMITER.pName(), prop[1]);
					}
				} else if (arg.startsWith(USE_OBJECT_NAME_PROPERTIES.pName() + "=")) {
					String[] prop = arg.split("=", 2);
					if (prop.length > 1) {
						LISTENER_PROPERTIES.put(USE_OBJECT_NAME_PROPERTIES.pName(), prop[1]);
					}
				} else {
					agentParams += agentParams.isEmpty() ? arg : "!" + arg;
				}
			}
		}

		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.agentmain: agent.params={0}, agent.lib.path={1}, listener.properties={2}, tnt4j.config={3}",
				agentParams, agentLibPath, LISTENER_PROPERTIES,
				System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY));

		File agentPath = new File(agentLibPath);
		String[] classPathEntries = agentPath.list(new JarFilter());
		if (classPathEntries != null) {
			File pathFile;
			for (String classPathEntry : classPathEntries) {
				pathFile = new File(classPathEntry);
				LOGGER.log(OpLevel.INFO, "SamplingAgent.agentmain: extending classpath with: {0}",
						pathFile.getAbsolutePath());
				extendClasspath(pathFile.toURI().toURL());
			}
		}

		premain(agentParams, inst);
	}

	/**
	 * Loads required classpath entries to running JVM.
	 *
	 * @param classPathEntriesURL
	 *            classpath entries URLs to attach to JVM
	 * @throws Exception
	 *             if exception occurs while extending system class loader's classpath
	 */
	private static void extendClasspath(URL... classPathEntriesURL) throws Exception {
		URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
		method.setAccessible(true);
		for (URL classPathEntryURL : classPathEntriesURL) {
			try {
				method.invoke(classLoader, classPathEntryURL);
			} catch (Exception e) {
				LOGGER.log(OpLevel.ERROR, "SamplingAgent.extendClasspath: could not load lib {0}", classPathEntryURL);
				LOGGER.log(OpLevel.ERROR, "                    Exception: {0}", Utils.getExceptionMessages(e));
			}
		}
	}

	/**
	 * Main entry point for running as a standalone application (test only).
	 *
	 * @param args
	 *            argument list: [sampling-mode vm-descriptor] [mbean-filter sample_time_ms]
	 *
	 * @throws Exception
	 *             if exception occurs while initializing MBeans sampling
	 */
	public static void main(String[] args) throws Exception {
		Properties props = new Properties();
		boolean argsValid = parseArgs(props, args);

		if (argsValid) {
			SamplerFactory sFactory = DefaultSamplerFactory
					.getInstance(Utils.getConfProperty(DEFAULTS, "com.jkoolcloud.tnt4j.stream.jmx.sampler.factory"));
			sFactory.initialize();

			String am = props.getProperty(AGENT_ARG_MODE);
			if (AGENT_MODE_CONNECT.equalsIgnoreCase(am)) {
				String vmDescr = props.getProperty(AGENT_ARG_VM);
				String user = props.getProperty(AGENT_ARG_USER);
				String pass = props.getProperty(AGENT_ARG_PASS);
				String agentOptions = props.getProperty(AGENT_ARG_OPTIONS, DEFAULT_AGENT_OPTIONS);
				@SuppressWarnings("unchecked")
				Map<String, ?> connParams = (Map<String, ?>) props.get(AGENT_CONN_PARAMS);
				long cri = Long.parseLong(
						props.getProperty(AGENT_ARG_CONN_RETRY_INTERVAL, String.valueOf(CONN_RETRY_INTERVAL)));

				connect(vmDescr, user, pass, agentOptions, connParams, cri);
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
					LOGGER.log(OpLevel.INFO,
							"SamplingAgent.main: include.filter={0}, exclude.filter={1}, sample.ms={2}, delay.ms={3}, wait.ms={4}, listener.properties={5}, tnt4j.config={6}, jmx.sample.list={7}",
							inclF, exclF, sample_time, delay_time, wait_time, LISTENER_PROPERTIES,
							System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY), STREAM_AGENTS);
					synchronized (platformJmx) {
						platformJmx.wait(wait_time);
					}
				} catch (Throwable ex) {
					LOGGER.log(OpLevel.ERROR, "SamplingAgent.main: failed to configure and run JMX sampling...");
					LOGGER.log(OpLevel.ERROR, "         Exception: {0}", Utils.getExceptionMessages(ex));
				}
			}
		} else {
			LOGGER.log(OpLevel.INFO, "Printing usage instructions before exit!..");
			System.out.println("Usage: mbean-filter exclude-filter sample-ms [wait-ms] (e.g \"*:*\" \"\" 10000 60000)");
			System.out.println("   or: -attach -vm:vmName/vmId -ap:agentJarPath -ao:agentOptions (e.g -attach -vm:activemq -ap:[ENV_PATH]/tnt-stream-jmx.jar -ao:*:*!!10000)");
			System.out.println("   or: -connect -vm:vmName/vmId/JMX_URL -ao:agentOptions (e.g -connect -vm:activemq -ao:*:*!!10000");
			System.out.println("   or: -connect -vm:vmName/vmId/JMX_URL -cri:connRetryIntervalSec -ao:agentOptions (e.g -connect -vm:activemq -cri:30 -ao:*:*!!10000");
			System.out.println("   or: -connect -vm:vmName/vmId/JMX_URL -ul:userLogin -up:userPassword -ao:agentOptions (e.g -connect -vm:activemq -ul:admin -up:admin -ao:*:*!!10000");
			System.out.println("   or: -connect -vm:vmName/vmId/JMX_URL -ul:userLogin -up:userPassword -ao:agentOptions -cp:jmcConnParam1 -cp:jmcConnParam2 -cp:... (e.g -connect -vm:activemq -ul:admin -up:admin -ao:*:*!!10000 -cp:javax.net.ssl.trustStorePassword=trustPass");
			System.out.println("   or: -local -ao:agentOptions (e.g -local -ao:*:*!!10000");
			System.out.println();
			System.out.println("Arguments definition:");
			System.out.println("   -ao: - agent options string using '!' symbol as delimiter. Options format: mbean-filter!exclude-filter!sample-ms!init-delay-ms");
			System.out.println("       mbean-filter - MBean include name filter defined using object name pattern: domainName:keysSet");
			System.out.println("       exclude-filter - MBean exclude name filter defined using object name pattern: domainName:keysSet");
			System.out.println("       sample-ms - MBeans sampling rate in milliseconds");
			System.out.println("       init-delay-ms - MBeans sampling initial delay in milliseconds. Optional, by default it is equal to 'sample-ms' value.");
			System.out.println("   -cp: - JMX connection parameter string using '=' symbol as delimiter. Defines only one parameter, to define more than one use this argument multiple times. Argument format: -cp:key=value");
			System.out.println("       see https://docs.oracle.com/javase/7/docs/technotes/guides/management/agent.html for more details");
			System.out.println("  -slp: - sampler parameter string using '=' symbol as delimiter. Defines only one parameter, to define more than one use this argument multiple times. Argument format: -slp:key=value");
			System.out.println("       trace - flag indicating whether the sample listener should print trace entries to print stream. Default value - 'false'");
			System.out.println("       forceObjectName - flag indicating to forcibly add 'objectName' attribute if such is not present for a MBean. Default value - 'false'");
			System.out.println("       compositeDelimiter - delimiter used to tokenize composite/tabular type MBean properties keys. Default value - '\\'");
			System.out.println("       useObjectNameProperties - flag indicating to copy MBean ObjectName contained properties into sample snapshot properties. Default value - 'true'");
			System.out.println("   -sp: - sampler system property string using '=' symbol as delimiter. Defines only one system property, to define more than one use this argument multiple times. Argument format: -sp:key=value");

			System.exit(1);
		}
	}

	private static void resolveServer(String host) {
		String serverAddress;
		String serverName;

		if (StringUtils.isEmpty(host)) {
			serverAddress = Utils.getLocalHostAddress();
			serverName = Utils.getLocalHostName();
		} else {
			serverAddress = Utils.resolveHostNameToAddress(host);
			serverName = Utils.resolveAddressToHostName(host);
		}

		System.setProperty(SOURCE_SERVER_ADDRESS, serverAddress);
		System.setProperty(SOURCE_SERVER_NAME, serverName);
	}

	private static boolean parseArgs(Properties props, String... args) {
		LOGGER.log(OpLevel.INFO, "SamplingAgent.parseArgs(): args={0}", Arrays.toString(args));
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
							LOGGER.log(OpLevel.WARNING,
									"SamplingAgent.parseArgs: JVM descriptor already defined. Can not use argument [{0}] multiple times.",
									PARAM_VM_DESCRIPTOR);
							return false;
						}

						setProperty(props, arg, PARAM_VM_DESCRIPTOR, AGENT_ARG_VM);
					} else if (arg.startsWith(PARAM_AGENT_LIB_PATH)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_LIB_PATH))) {
							LOGGER.log(OpLevel.WARNING,
									"SamplingAgent.parseArgs: agent library path already defined. Can not use argument [{0}] multiple times.",
									PARAM_AGENT_LIB_PATH);
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_LIB_PATH, AGENT_ARG_LIB_PATH);
					} else if (arg.startsWith(PARAM_AGENT_OPTIONS)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_OPTIONS))) {
							LOGGER.log(OpLevel.WARNING,
									"SamplingAgent.parseArgs: agent options already defined. Can not use argument [{0}] multiple times.",
									PARAM_AGENT_OPTIONS);
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_OPTIONS, AGENT_ARG_OPTIONS);
					} else if (arg.startsWith(PARAM_AGENT_USER_LOGIN)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_USER))) {
							LOGGER.log(OpLevel.WARNING,
									"SamplingAgent.parseArgs: user login already defined. Can not use argument [{0}] multiple times.",
									PARAM_AGENT_USER_LOGIN);
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_USER_LOGIN, AGENT_ARG_USER);
					} else if (arg.startsWith(PARAM_AGENT_USER_PASS)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_PASS))) {
							LOGGER.log(OpLevel.WARNING,
									"SamplingAgent.parseArgs: user password already defined. Can not use argument [{0}] multiple times.",
									PARAM_AGENT_USER_PASS);
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_USER_PASS, AGENT_ARG_PASS);
					} else if (arg.startsWith(PARAM_AGENT_CONN_PARAM)) {
						String[] cp = parseCompositeArg(arg, PARAM_AGENT_CONN_PARAM);
						if (cp == null) {
							return false;
						}

						@SuppressWarnings("unchecked")
						Map<String, Object> connParams = (Map<String, Object>) props.get(AGENT_CONN_PARAMS);
						if (connParams == null) {
							connParams = new HashMap<String, Object>(5);
							props.put(AGENT_CONN_PARAMS, connParams);
						}

						connParams.put(cp[0], cp[1]);
					} else if (arg.startsWith(PARAM_AGENT_CONN_RETRY_INTERVAL)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_CONN_RETRY_INTERVAL))) {
							LOGGER.log(OpLevel.WARNING,
									"SamplingAgent.parseArgs: connection retry interval already defined. Can not use argument [{0}] multiple times.",
									PARAM_AGENT_CONN_RETRY_INTERVAL);
							return false;
						}

						setProperty(props, arg, PARAM_AGENT_CONN_RETRY_INTERVAL, AGENT_ARG_CONN_RETRY_INTERVAL);
					} else if (arg.startsWith(PARAM_AGENT_LISTENER_PARAM)) {
						String[] slp = parseCompositeArg(arg, PARAM_AGENT_LISTENER_PARAM);
						if (slp == null) {
							return false;
						}

						LISTENER_PROPERTIES.put(slp[0], slp[1]);
					} else if (arg.startsWith(PARAM_AGENT_SYSTEM_PROPERTY)) {
						String[] slp = parseCompositeArg(arg, PARAM_AGENT_SYSTEM_PROPERTY);
						if (slp == null) {
							return false;
						}

						System.setProperty(slp[0], slp[1]);
					} else {
						LOGGER.log(OpLevel.WARNING, "SamplingAgent.parseArgs: invalid argument [{0}]", arg);
						return false;
					}
				}
			} catch (IllegalArgumentException exc) {
				LOGGER.log(OpLevel.ERROR, "SamplingAgent.parseArgs: arguments parsing failed...");
				LOGGER.log(OpLevel.ERROR, "              Exception: {0}", Utils.getExceptionMessages(exc));
				return false;
			}

			if (StringUtils.isEmpty(props.getProperty(AGENT_ARG_VM)) && ac) {
				LOGGER.log(OpLevel.WARNING,
						"SamplingAgent.parseArgs: missing mandatory argument [{0}] defining JVM descriptor.",
						PARAM_VM_DESCRIPTOR);
				return false;
			}

			// if (AGENT_MODE_ATTACH.equalsIgnoreCase(props.getProperty(AGENT_ARG_MODE))
			// && StringUtils.isEmpty(props.getProperty(AGENT_ARG_LIB_PATH))) {
			// LOGGER.log(OpLevel.WARNING,
			// "SamplingAgent.parseArgs: missing mandatory argument [{0}] defining agent library path.",
			// PARAM_AGENT_LIB_PATH);
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

	private static String[] parseCompositeArg(String arg, String argName) {
		String argValue = arg.substring(argName.length());
		if (StringUtils.isEmpty(argValue)) {
			LOGGER.log(OpLevel.WARNING, "SamplingAgent.parseArgs: missing argument [{0}] value", argName);
			return null;
		}

		String[] slp = argValue.split("=", 2);

		if (slp.length < 2) {
			LOGGER.log(OpLevel.WARNING, "SamplingAgent.parseArgs: malformed argument [{0}] value [{1}]", argName,
					argValue);
			return null;
		}

		return slp;
	}

	private static void setProperty(Properties props, String arg, String argName, String agentArgName)
			throws IllegalArgumentException {
		String pValue = arg.substring(argName.length());
		if (StringUtils.isEmpty(pValue)) {
			throw new IllegalArgumentException("Missing argument [" + argName + "] value");
		}

		props.setProperty(agentArgName, pValue);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public static void sample() throws IOException {
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter",
				Sampler.JMX_FILTER_NONE);
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
		int initDelay = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.init.delay", period);
		sample(incFilter, excFilter, initDelay, period);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @param incFilter
	 *            semicolon separated include filter list
	 * @param period
	 *            sampling in milliseconds.
	 *
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public static void sample(String incFilter, long period) throws IOException {
		sample(incFilter, null, period);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @param incFilter
	 *            semicolon separated include filter list
	 * @param excFilter
	 *            semicolon separated exclude filter list (null if empty)
	 * @param period
	 *            sampling in milliseconds.
	 *
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public static void sample(String incFilter, String excFilter, long period) throws IOException {
		sample(incFilter, excFilter, period, period);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @param incFilter
	 *            semicolon separated include filter list
	 * @param excFilter
	 *            semicolon separated exclude filter list (null if empty)
	 * @param initDelay
	 *            initial delay before first sampling
	 * @param period
	 *            sampling in milliseconds.
	 *
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public static void sample(String incFilter, String excFilter, long initDelay, long period) throws IOException {
		sample(incFilter, excFilter, initDelay, period, TimeUnit.MILLISECONDS);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @param incFilter
	 *            semicolon separated include filter list
	 * @param excFilter
	 *            semicolon separated exclude filter list (null if empty)
	 * @param initDelay
	 *            initial delay before first sampling
	 * @param period
	 *            sampling time
	 * @param tUnit
	 *            time units for sampling period
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public static void sample(String incFilter, String excFilter, long initDelay, long period, TimeUnit tUnit)
			throws IOException {
		resolveServer(null);
		SamplerFactory sFactory = initPlatformJMX(incFilter, excFilter, initDelay, period, tUnit, null);

		// find other registered MBean servers and initiate sampling for all
		ArrayList<MBeanServer> mbsList = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server : mbsList) {
			Sampler jmxSampler = STREAM_AGENTS.get(server);
			if (jmxSampler == null) {
				jmxSampler = sFactory.newInstance(server);
				scheduleSampler(incFilter, excFilter, initDelay, period, tUnit, sFactory, jmxSampler, STREAM_AGENTS);
			}
		}
	}

	/**
	 * Schedule sample using defined JMX connector to get MBean server connection instance to monitored JVM.
	 *
	 * @param incFilter
	 *            semicolon separated include filter list
	 * @param excFilter
	 *            semicolon separated exclude filter list (null if empty)
	 * @param initDelay
	 *            initial delay before first sampling
	 * @param period
	 *            sampling time
	 * @param tUnit
	 *            time units for sampling period
	 * @param conn
	 *            JMX connector to get MBean server connection instance
	 *
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public static void sample(String incFilter, String excFilter, long initDelay, long period, TimeUnit tUnit,
			JMXConnector conn) throws IOException {
		// get MBeanServerConnection from JMX RMI connector
		if (conn instanceof JMXAddressable) {
			JMXServiceURL url = ((JMXAddressable) conn).getAddress();
			resolveServer(url == null ? null : url.getHost());
		}
		MBeanServerConnection mbSrvConn = conn.getMBeanServerConnection();
		initPlatformJMX(incFilter, excFilter, initDelay, period, tUnit, mbSrvConn);
	}

	private static SamplerFactory initPlatformJMX(String incFilter, String excFilter, long initDelay, long period,
			TimeUnit tUnit, MBeanServerConnection mbSrvConn) throws IOException {
		// obtain a default sample factory
		SamplerFactory sFactory = DefaultSamplerFactory
				.getInstance(Utils.getConfProperty(DEFAULTS, "com.jkoolcloud.tnt4j.stream.jmx.sampler.factory"));

		if (platformJmx == null) {
			synchronized (STREAM_AGENTS) {
				// create new sampler with default MBeanServer instance
				platformJmx = mbSrvConn == null ? sFactory.newInstance() : sFactory.newInstance(mbSrvConn);
				// schedule sample with a given filter and sampling period
				scheduleSampler(incFilter, excFilter, initDelay, period, tUnit, sFactory, platformJmx, STREAM_AGENTS);
			}
		}

		return sFactory;
	}

	private static void scheduleSampler(String incFilter, String excFilter, long initDelay, long period, TimeUnit tUnit,
			SamplerFactory sFactory, Sampler sampler, Map<MBeanServerConnection, Sampler> agents) throws IOException {
		agents.put(sampler.getMBeanServer(), sampler);
		sampler.setSchedule(incFilter, excFilter, initDelay, period, tUnit, sFactory)
				.addListener(sFactory.newListener(LISTENER_PROPERTIES)).run();
	}

	/**
	 * Attaches to {@code vmDescr} defined JVM as agent.
	 *
	 * @param vmDescr
	 *            JVM descriptor: name fragment or pid
	 * @param agentJarPath
	 *            agent JAR path
	 * @param agentOptions
	 *            agent options
	 * @throws Exception
	 *             if any exception occurs while attaching to JVM
	 */
	public static void attach(String vmDescr, String agentJarPath, String agentOptions) throws Exception {
		LOGGER.log(OpLevel.INFO, "SamplingAgent.attach(): vmDescr={0}, agentJarPath={1}, agentOptions={2}", vmDescr,
				agentJarPath, agentOptions);
		if (Utils.isEmpty(vmDescr)) {
			throw new RuntimeException("Java VM descriptor must be not empty!..");
		}

		File pathFile;
		if (StringUtils.isEmpty(agentJarPath)) {
			LOGGER.log(OpLevel.INFO, "SamplingAgent.attach: no agent jar defined");
			pathFile = getSAPath();
		} else {
			pathFile = new File(agentJarPath);
			if (!pathFile.exists()) {
				LOGGER.log(OpLevel.INFO, "SamplingAgent.attach: non-existing argument defined agent jar: {0}",
						agentJarPath);
				LOGGER.log(OpLevel.INFO, "                      absolute agent jar path: {0}",
						pathFile.getAbsolutePath());
				pathFile = getSAPath();
			}
		}

		String agentPath = pathFile.getAbsolutePath();

		for (Map.Entry<String, Object> lpe : LISTENER_PROPERTIES.entrySet()) {
			agentOptions += "!" + lpe.getKey() + "=" + String.valueOf(lpe.getValue());
		}

		agentOptions += "!" + SYS_PROP_AGENT_PATH + "=" + agentPath;

		String tnt4jConf = System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY);
		if (!Utils.isEmpty(tnt4jConf)) {
			String tnt4jPropPath = new File(tnt4jConf).getAbsolutePath();
			agentOptions += "!" + SYS_PROP_TNT4J_CFG + "=" + tnt4jPropPath;
		}
		String log4jConf = System.getProperty(LOG4J_PROPERTIES_KEY);
		if (!Utils.isEmpty(log4jConf)) {
			String log4jPropPath = new File(log4jConf).getAbsolutePath();
			agentOptions += "!" + SYS_PROP_LOG4J_CFG + "=" + log4jPropPath;
		}

		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.attach: attaching JVM using vmDescr={0}, agentJarPath={1}, agentOptions={2}", vmDescr,
				agentJarPath, agentOptions);
		VMUtils.attachVM(vmDescr, agentPath, agentOptions);
	}

	private static File getSAPath() throws URISyntaxException {
		String saPath = SamplingAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
		LOGGER.log(OpLevel.INFO, "SamplingAgent.attach: using SamplingAgent class referenced jar path: {0}", saPath);
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
	 * @param vmDescr
	 *            JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see #connect(String,String,long)
	 */
	public static void connect(String vmDescr, String options) throws Exception {
		connect(vmDescr, options, CONN_RETRY_INTERVAL);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr
	 *            JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param connRetryInterval
	 *            connect reattempt interval value in seconds, {@code -1} - do not retry
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see #connect(String,String,Map,long)
	 */
	public static void connect(String vmDescr, String options, long connRetryInterval) throws Exception {
		connect(vmDescr, options, null, connRetryInterval);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr
	 *            JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param connParams
	 *            map of JMX connection parameters, defined by {@link javax.naming.Context}
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see #connect(String, String, java.util.Map, long)
	 */
	public static void connect(String vmDescr, String options, Map<String, ?> connParams) throws Exception {
		connect(vmDescr, options, connParams, CONN_RETRY_INTERVAL);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr
	 *            JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param connParams
	 *            map of JMX connection parameters, defined by {@link javax.naming.Context}
	 * @param connRetryInterval
	 *            connect reattempt interval value in seconds, {@code -1} - do not retry
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see #connect(String, String, String, String, java.util.Map, long)
	 */
	public static void connect(String vmDescr, String options, Map<String, ?> connParams, long connRetryInterval)
			throws Exception {
		connect(vmDescr, null, null, options, connParams, connRetryInterval);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr
	 *            JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param user
	 *            user login used by JMX service connection
	 * @param pass
	 *            user password used by JMX service connection
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see #connect(String, String, String, String, long)
	 */
	public static void connect(String vmDescr, String user, String pass, String options) throws Exception {
		connect(vmDescr, user, pass, options, CONN_RETRY_INTERVAL);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr
	 *            JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param user
	 *            user login used by JMX service connection
	 * @param pass
	 *            user password used by JMX service connection
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param connRetryInterval
	 *            connect reattempt interval value in seconds, {@code -1} - do not retry
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see #connect(String, String, String, String, java.util.Map, long)
	 */
	public static void connect(String vmDescr, String user, String pass, String options, long connRetryInterval)
			throws Exception {
		connect(vmDescr, user, pass, options, null, connRetryInterval);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link MBeanServerConnection} to
	 * collect samples.
	 *
	 * @param vmDescr
	 *            JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param user
	 *            user login used by JMX service connection
	 * @param pass
	 *            user password used by JMX service connection
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param connParams
	 *            map of JMX connection parameters, defined by {@link javax.naming.Context}
	 * @param connRetryInterval
	 *            connect reattempt interval value in seconds, {@code -1} - do not retry
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see JMXConnectorFactory#connect(javax.management.remote.JMXServiceURL, java.util.Map)
	 * @see javax.naming.Context
	 */
	public static void connect(String vmDescr, String user, String pass, String options, Map<String, ?> connParams,
			long connRetryInterval) throws Exception {
		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.connect(): vmDescr={0}, user={1}, pass={2}, options={3}, connParams={4}, connRetryInterval={5}",
				vmDescr, user, Utils.hideEnd(pass, "x", 0), options, connParams, connRetryInterval);
		if (Utils.isEmpty(vmDescr)) {
			throw new RuntimeException("Java VM descriptor must be not empty!..");
		}

		Map<String, Object> params = new HashMap<String, Object>(connParams == null ? 1 : connParams.size() + 1);

		if (StringUtils.isNotEmpty(user) && StringUtils.isNotEmpty(pass)) {
			LOGGER.log(OpLevel.INFO,
					"SamplingAgent.connect: adding user login and password to connection credentials...");
			String[] credentials = new String[] { user, pass };
			params.put(JMXConnector.CREDENTIALS, credentials);
		}

		if (connParams != null) {
			params.putAll(connParams);
		}

		do {
			try {
				String connectorAddress;
				if (vmDescr.startsWith("service:jmx:")) {
					connectorAddress = vmDescr;
				} else {
					connectorAddress = VMUtils.getVMConnAddress(vmDescr);
				}

				LOGGER.log(OpLevel.INFO, "SamplingAgent.connect: making JMX service URL from address={0}",
						connectorAddress);
				JMXServiceURL url = new JMXServiceURL(connectorAddress);

				LOGGER.log(OpLevel.INFO, "SamplingAgent.connect: connecting JMX service using URL={0}", url);
				JMXConnector connector = JMXConnectorFactory.connect(url, params);

				try {
					NotificationListener cnl = new NotificationListener() {
						@Override
						public void handleNotification(Notification notification, Object key) {
							if (notification.getType().contains("closed") || notification.getType().contains("failed")
									|| notification.getType().contains("lost")) {
								LOGGER.log(OpLevel.INFO, "SamplingAgent.connect: JMX connection status change: {0}",
										notification.getType());
								stopSampler();
							}
						}
					};
					connector.addConnectionNotificationListener(cnl, null, null);

					startSamplerAndWait(options, connector);

					shutdownSamplers();
					connector.removeConnectionNotificationListener(cnl);
				} finally {
					Utils.close(connector);
				}
			} catch (IOException exc) {
				LOGGER.log(OpLevel.ERROR, "SamplingAgent.connect: failed to connect JMX service");
				LOGGER.log(OpLevel.ERROR, "            Exception: {0}", Utils.getExceptionMessages(exc));

				if (connRetryInterval < 0) {
					stopSampling = true;
				}

				if (!stopSampling && connRetryInterval > 0) {
					LOGGER.log(OpLevel.INFO, "SamplingAgent.connect: will retry connect attempt in {0} seconds...",
							connRetryInterval);
					Thread.sleep(TimeUnit.SECONDS.toMillis(connRetryInterval));
				}
			}
		} while (!stopSampling);
	}

	private static void stopSampler() {
		LOGGER.log(OpLevel.INFO, "SamplingAgent.stopSampler: releasing sampler lock...");

		if (platformJmx != null) {
			synchronized (platformJmx) {
				platformJmx.notifyAll();
			}
		}
	}

	/**
	 * Initiates MBeans attributes sampling on local process runner JVM.
	 * 
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @param wait
	 *            flag indicating whether to wait for a runner process to complete
	 * @throws Exception
	 *             if any exception occurs while initializing local JVM sampler or stopping sampling process
	 */
	public static void sampleLocalVM(String options, boolean wait) throws Exception {
		LOGGER.log(OpLevel.INFO, "SamplingAgent.sampleLocalVM(): options={0}, wait={1}", options, wait);
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
				stopSampling = true;
				stopSampler();

				long startTime = System.currentTimeMillis();
				try {
					mainThread.join(TimeUnit.SECONDS.toMillis(2));
				} catch (Exception exc) {
				}
				LOGGER.log(OpLevel.INFO,
						"SamplingAgent.startSamplerAndWait: waited {0}ms. on Stream-JMX to complete...",
						(System.currentTimeMillis() - startTime));
			}
		});
		Runtime.getRuntime().addShutdownHook(shutdownHook);

		LOGGER.log(OpLevel.INFO, "SamplingAgent.startSamplerAndWait: locking on sampler...");
		if (platformJmx != null) {
			synchronized (platformJmx) {
				platformJmx.wait();
			}
		}

		LOGGER.log(OpLevel.INFO, "SamplingAgent.startSamplerAndWait: stopping Stream-JMX...");
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
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter",
				Sampler.JMX_FILTER_NONE);
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

		if (connector == null) {
			sample(incFilter, excFilter, initDelay, period, TimeUnit.MILLISECONDS);
		} else {
			sample(incFilter, excFilter, initDelay, period, TimeUnit.MILLISECONDS, connector);
		}

		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.startSampler: include.filter={0}, exclude.filter={1}, sample.ms={2}, initDelay.ms={3}, listener.properties={4}, tnt4j.config={5}, jmx.sample.list={6}",
				incFilter, excFilter, period, initDelay, LISTENER_PROPERTIES,
				System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY), STREAM_AGENTS);

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
	 * @param mServerConn
	 *            MBeanServerConnection instance
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
		synchronized (STREAM_AGENTS) {
			stopSampler();
			shutdownSamplers();
		}
	}

	private static void shutdownSamplers() {
		cancel();
		DefaultEventSinkFactory.shutdownAll();
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

	private static void copyProperty(DefaultSampleListener.ListenerProperties lProp, Map<?, ?> sProperties,
			Map<String, Object> tProperties) {
		Utils.copyProperty(lProp.apName(), sProperties, lProp.pName(), tProperties);
	}

	private static void copyProperty(DefaultSampleListener.ListenerProperties lProp, Map<?, ?> sProperties,
			Map<String, Object> tProperties, Object defValue) {
		Utils.copyProperty(lProp.apName(), sProperties, lProp.pName(), tProperties, defValue);
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