/*
 * Copyright 2015-2023 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.net.ssl.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.DefaultEventSinkFactory;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.SampleHandler;
import com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;
import com.jkoolcloud.tnt4j.stream.jmx.core.PropertyNameBuilder;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.DefaultSamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;
import com.jkoolcloud.tnt4j.stream.jmx.impl.JMXMBeanServerConnection;
import com.jkoolcloud.tnt4j.stream.jmx.source.JMXSourceUtils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;
import com.jkoolcloud.tnt4j.stream.jmx.vm.*;

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
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(SamplingAgent.class);

	private Sampler platformJmx;
	private final Map<JMXServerConnection, Sampler> STREAM_SAMPLERS = Collections
			.synchronizedMap(new LinkedHashMap<>(5));

	private static final Collection<SamplingAgent> ALL_AGENTS = new ArrayList<>(5);

	private static final String LOG4J_PROPERTIES_KEY = "log4j2.configurationFile";
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
	private static final String PARAM_AGENT_CONNECTIONS_CONFIG_FILE = "-f:";
	private static final String PARAM_AGENT_DISABLE_SSL_VALIDATION = "-ssl";

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
	private static final String AGENT_ARG_BATCH_SIZE = "agent.batch.size";
	private static final String AGENT_ARG_DISABLE_SSL = "ssl.disable";

	private static final String AGENT_CONN_PARAMS = "agent.connection.params";

	private static final String DEFAULT_AGENT_OPTIONS = Sampler.JMX_FILTER_ALL + "!" + Sampler.JMX_FILTER_NONE + "!"
			+ Sampler.JMX_SAMPLE_PERIOD;

	public static final Properties DEFAULTS = new Properties();
	public static final Map<String, Object> LISTENER_PROPERTIES = new HashMap<>(5);
	static {
		initDefaults(DEFAULTS);

		initListenerProperties();
		initFeatures();

		initShutdownHook();
	}

	private static VMResolverFactory vmResolverFactory;
	private static final Properties clProps = new Properties();

	private boolean stopSampling = false;
	private boolean synchronousSamplers = false;
	private JMXConnector connector;
	private AtomicBoolean stopSamplerLatch = new AtomicBoolean(false);

	private SamplingAgent() {
	}

	/**
	 * Creates new instance of sampling agent and puts it to registry.
	 *
	 * @return new sampling agent instance
	 * 
	 * @see #newSamplingAgent(boolean)
	 */
	public static SamplingAgent newSamplingAgent() {
		return newSamplingAgent(false);
	}

	/**
	 * Creates new instance of sampling agent and puts it to registry.
	 *
	 * @param synchronousSamplers
	 *            flag indicating whether agent handled samplers shall run synchronously within agent thread or
	 *            concurrently by starting dedicated threads
	 * @return new sampling agent instance
	 */
	public static SamplingAgent newSamplingAgent(boolean synchronousSamplers) {
		SamplingAgent agent = new SamplingAgent();
		agent.synchronousSamplers = synchronousSamplers;
		ALL_AGENTS.add(agent);
		return agent;
	}

	/**
	 * Initializes sampling agent shutdown hook.
	 * 
	 * @see #destroy()
	 */
	protected static void initShutdownHook() {
		Thread shutdownHook = new Thread(() -> {
			long startTime = System.currentTimeMillis();
			try {
				destroy();
			} catch (Exception exc) {
			}
			LOGGER.log(OpLevel.INFO, "SamplingAgent.shutdownHook: waited {0}ms. on Stream-JMX to complete...",
					(System.currentTimeMillis() - startTime));
		}, "tnt4j-stream-jmx-shutdown-hook");
		Runtime.getRuntime().addShutdownHook(shutdownHook);
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
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public static void premain(String options, Instrumentation inst) throws IOException {
		LOGGER.log(OpLevel.INFO,
				"meshIQ TNT4J-Stream-JMX v.{0} session starting as JVM agent...\n" + "  Runtime environment: {1}",
				pkgVersion(), runEnv());
		SamplingArgs sa = SamplingArgs.parse(options);
		Map<String, Object> samplerCfg = sa.getAsMap();
		SamplingAgent agent = newSamplingAgent(true);
		agent.sample(samplerCfg);
		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.premain: sampler.config={0}, listener.properties={1}, tnt4j.config={2}, jmx.sample.list={3}",
				samplerCfg, LISTENER_PROPERTIES, System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY),
				agent.STREAM_SAMPLERS);
	}

	/**
	 * Initializes listener properties picking values from defaults and System properties.
	 */
	public static void initListenerProperties() {
		LISTENER_PROPERTIES.clear();

		copyProperty(FORCE_OBJECT_NAME, DEFAULTS, LISTENER_PROPERTIES, false);
		copyProperty(COMPOSITE_DELIMITER, DEFAULTS, LISTENER_PROPERTIES,
				PropertyNameBuilder.DEFAULT_COMPOSITE_DELIMITER);
		copyProperty(USE_OBJECT_NAME_PROPERTIES, DEFAULTS, LISTENER_PROPERTIES, true);
		copyProperty(EXCLUDE_ON_ERROR, DEFAULTS, LISTENER_PROPERTIES, false);
		copyProperty(USER_EXCLUDED_ATTRIBUTES, DEFAULTS, LISTENER_PROPERTIES, "");

		copyProperty(FORCE_OBJECT_NAME, System.getProperties(), LISTENER_PROPERTIES);
		copyProperty(COMPOSITE_DELIMITER, System.getProperties(), LISTENER_PROPERTIES);
		copyProperty(USE_OBJECT_NAME_PROPERTIES, System.getProperties(), LISTENER_PROPERTIES);
		copyProperty(EXCLUDE_ON_ERROR, System.getProperties(), LISTENER_PROPERTIES);
		copyProperty(USER_EXCLUDED_ATTRIBUTES, System.getProperties(), LISTENER_PROPERTIES);
	}

	/**
	 * Initiates configurable Stream-JMX features.
	 */
	protected static void initFeatures() {
		vmResolverFactory = DefaultVMResolverFactory.getInstance(
				Utils.getConfProperty(DEFAULTS, "com.jkoolcloud.tnt4j.stream.jmx.sampler.vm.resolver.factory")); // NON-NLS
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
		LOGGER.log(OpLevel.INFO,
				"meshIQ TNT4J-Stream-JMX v.{0} session starting as JVM agent...\n" + "  Runtime environment: {1}",
				pkgVersion(), runEnv());
		LOGGER.log(OpLevel.INFO, "SamplingAgent.agentmain(): agentArgs={0}", agentArgs);
		String agentParams = "";
		String tnt4jProp = System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY);
		String log4jProp = System.getProperty(LOG4J_PROPERTIES_KEY);
		String agentLibPath = "";
		if (StringUtils.isNotEmpty(agentArgs)) {
			String[] args = agentArgs.split("!");

			for (String arg : args) {
				if (arg.startsWith(SYS_PROP_TNT4J_CFG)) {
					if (StringUtils.isEmpty(tnt4jProp)) {
						String[] prop = arg.split("=", 2);
						tnt4jProp = prop.length > 1 ? prop[1] : null;

						System.setProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY, tnt4jProp);
					}
				} else if (arg.startsWith(SYS_PROP_AGENT_PATH)) {
					String[] prop = arg.split("=", 2);
					agentLibPath = prop.length > 1 ? prop[1] : null;
				}
				if (arg.startsWith(SYS_PROP_LOG4J_CFG)) {
					if (StringUtils.isEmpty(log4jProp)) {
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
				} else if (arg.startsWith(EXCLUDE_ON_ERROR.pName() + "=")) {
					String[] prop = arg.split("=", 2);
					if (prop.length > 1) {
						LISTENER_PROPERTIES.put(EXCLUDE_ON_ERROR.pName(), prop[1]);
					}
				} else if (arg.startsWith(USER_EXCLUDED_ATTRIBUTES.pName() + "=")) {
					String[] prop = arg.split("=", 2);
					if (prop.length > 1) {
						LISTENER_PROPERTIES.put(USER_EXCLUDED_ATTRIBUTES.pName(), prop[1]);
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
		ClassLoader classLoader = ClassLoader.getSystemClassLoader();
		if (classLoader == null) {
			return;
		}

		Object ucp = FieldUtils.readDeclaredField(classLoader, "ucp", true); // NON-NLS
		Method method = ucp.getClass().getDeclaredMethod("addURL", URL.class);
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
	public static void main(String... args) throws Exception {
		LOGGER.log(OpLevel.INFO, "meshIQ TNT4J-Stream-JMX v.{0} session starting as standalone application...\n"
				+ "  Runtime environment: {1}", pkgVersion(), runEnv());
		boolean argsValid = parseArgs(clProps, args);

		if (argsValid) {
			SamplerFactory sFactory = DefaultSamplerFactory
					.getInstance(Utils.getConfProperty(DEFAULTS, "com.jkoolcloud.tnt4j.stream.jmx.sampler.factory"));
			sFactory.initialize();

			String disableSSL = clProps.getProperty(AGENT_ARG_DISABLE_SSL);
			if (StringUtils.equalsIgnoreCase("true", disableSSL)) {
				disableSslVerification();
			}

			String am = clProps.getProperty(AGENT_ARG_MODE);
			if (AGENT_MODE_CONNECT.equalsIgnoreCase(am)) {
				String vmDesc = clProps.getProperty(AGENT_ARG_VM);
				List<VMParams<JMXServiceURL>> allVMs = getAllVMs(clProps);
				if (Utils.isEmpty(allVMs)) {
					LOGGER.log(OpLevel.CRITICAL, "No JVMs found using VM descriptor ''{0}''", vmDesc);
				} else {
					connectAll(allVMs, clProps);
				}
			} else if (AGENT_MODE_ATTACH.equalsIgnoreCase(am)) {
				String vmDescr = clProps.getProperty(AGENT_ARG_VM);
				String jarPath = clProps.getProperty(AGENT_ARG_LIB_PATH);
				String agentOptions = clProps.getProperty(AGENT_ARG_OPTIONS, DEFAULT_AGENT_OPTIONS);

				attach(vmDescr, jarPath, agentOptions);
			} else if (AGENT_MODE_LOCAL.equalsIgnoreCase(am)) {
				String agentOptions = clProps.getProperty(AGENT_ARG_OPTIONS, DEFAULT_AGENT_OPTIONS);

				sampleLocalVM(agentOptions, true);
			} else {
				try {
					String inclF = clProps.getProperty(AGENT_ARG_I_FILTER, Sampler.JMX_FILTER_ALL);
					String exclF = clProps.getProperty(AGENT_ARG_E_FILTER, Sampler.JMX_FILTER_NONE);
					long sample_time = Integer
							.parseInt(clProps.getProperty(AGENT_ARG_S_TIME, String.valueOf(Sampler.JMX_SAMPLE_PERIOD)));
					long delay_time = Integer
							.parseInt(clProps.getProperty(AGENT_ARG_D_TIME, String.valueOf(sample_time)));
					long wait_time = Integer.parseInt(clProps.getProperty(AGENT_ARG_W_TIME, "0"));
					int batch_size = Integer.parseInt(clProps.getProperty(AGENT_ARG_BATCH_SIZE, "-1"));
					SamplingAgent samplingAgent = newSamplingAgent();
					Map<String, Object> samplerCfg = new HashMap<>(6);
					samplerCfg.put(SampleHandler.CFG_INCLUDE_FILTER, inclF);
					samplerCfg.put(SampleHandler.CFG_EXCLUDE_FILTER, exclF);
					samplerCfg.put(SampleHandler.CFG_BATCH_SIZE, batch_size);

					samplerCfg.put(Sampler.CFG_INITIAL_DELAY, delay_time);
					samplerCfg.put(Sampler.CFG_SAMPLING_PERIOD, sample_time);
					samplerCfg.put(Sampler.CFG_TIME_UNIT, TimeUnit.MILLISECONDS);
					samplingAgent.initPlatformJMX(samplerCfg, null);
					LOGGER.log(OpLevel.INFO,
							"SamplingAgent.main: sampler.config={0}, wait.ms={1}, listener.properties={2}, tnt4j.config={3}, jmx.sample.list={4}",
							samplerCfg, wait_time, LISTENER_PROPERTIES,
							System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY), samplingAgent.STREAM_SAMPLERS);

					synchronized (samplingAgent.platformJmx) {
						samplingAgent.platformJmx.wait(wait_time);
					}
				} catch (Throwable ex) {
					LOGGER.log(OpLevel.ERROR, "SamplingAgent.main: failed to configure and run JMX sampling...");
					LOGGER.log(OpLevel.ERROR, "         Exception: {0}", Utils.getExceptionMessages(ex));
				}
			}
		} else {
			LOGGER.log(OpLevel.INFO, "Printing usage instructions before exit!..");
			String usageStr = "Usage: mbean-filter exclude-filter sample-ms [wait-ms] (e.g \"*:*\" \"\" 10000 60000)\n"
					+ "   or: -attach -vm:vmName/vmId -ap:agentJarPath -ao:agentOptions (e.g -attach -vm:activemq -ap:[ENV_PATH]/tnt-stream-jmx.jar -ao:*:*!!10000)\n"
					+ "   or: -connect -vm:vmName/vmId/JMX_URL -ao:agentOptions (e.g -connect -vm:activemq -ao:*:*!!10000\n"
					+ "   or: -connect -vm:vmName/vmId/JMX_URL -cri:connRetryIntervalSec -ao:agentOptions (e.g -connect -vm:activemq -cri:30 -ao:*:*!!10000\n"
					+ "   or: -connect -vm:vmName/vmId/JMX_URL -ul:userLogin -up:userPassword -ao:agentOptions (e.g -connect -vm:activemq -ul:admin -up:admin -ao:*:*!!10000\n"
					+ "   or: -connect -vm:vmName/vmId/JMX_URL -ul:userLogin -up:userPassword -ao:agentOptions -cp:jmcConnParam1 -cp:jmcConnParam2 -cp:... (e.g -connect -vm:activemq -ul:admin -up:admin -ao:*:*!!10000 -cp:javax.net.ssl.trustStorePassword=trustPass\n"
					+ "   or: -local -ao:agentOptions (e.g -local -ao:*:*!!10000\n"
					+ "                                                         \n"
					+ "Arguments definition:                                    \n"
					+ "   -vm: - virtual machine descriptor. It can be PID or JVM process name fragment. '*' value is wildcard to pick all found VMs, running on local machine.\n"
					+ "   -ao: - agent options string using '!' symbol as delimiter. Options format: mbean-filter!exclude-filter!sample-ms!init-delay-ms\n"
					+ "       mbean-filter - MBean include name filter defined using object name pattern: domainName:keysSet\n"
					+ "       exclude-filter - MBean exclude name filter defined using object name pattern: domainName:keysSet\n"
					+ "       sample-ms - MBeans sampling rate in milliseconds\n"
					+ "       init-delay-ms - MBeans sampling initial delay in milliseconds. Optional, by default it is equal to 'sample-ms' value.\n"
					+ "       batch-size - number of sampled MBeans to post over single package. Optional, default is `-1` (unlimited).\n"
					+ "   -cp: - JMX connection parameter string using '=' symbol as delimiter. Defines only one parameter, to define more than one use this argument multiple times. Argument format: -cp:key=value\n"
					+ "       see https://docs.oracle.com/javase/7/docs/technotes/guides/management/agent.html for more details\n"
					+ "  -slp: - sampler parameter string using '=' symbol as delimiter. Defines only one parameter, to define more than one use this argument multiple times. Argument format: -slp:key=value\n"
					+ "       trace - flag indicating whether the sample listener should print trace entries to print stream. Default value - 'false'.\n"
					+ "       forceObjectName - flag indicating to forcibly add 'objectName' attribute if such is not present for a MBean. Default value - 'false'.\n"
					+ "       compositeDelimiter - delimiter used to tokenize composite/tabular type MBean properties keys. Default value - '\\'.\n"
					+ "       useObjectNameProperties - flag indicating to copy MBean ObjectName contained properties into sample snapshot properties. Default value - 'true'.\n"
					+ "       excludeOnError - flag indicating to auto-exclude failed to sample attributes. Default value - 'false'.\n"
					+ "       excludedAttributes - list of user chosen attribute names (may have wildcards '*' and '?') to exclude, pattern: 'attr1,attr2,...,attrN@MBean1_ObjectName;...;attr1,attr2,...,attrN@MBeanN_ObjectName'.\n"
					+ "   -sp: - sampler system property string using '=' symbol as delimiter. Defines only one system property, to define more than one use this argument multiple times. Argument format: -sp:key=value";

			System.out.println(usageStr);

			System.exit(1);
		}
	}

	/**
	 * Collects all Java VM JMX server connection URLs resolvable from defined JVM descriptor.
	 * <p>
	 * JVM descriptor shall be defined over provided {@code props} entry with key {@value #AGENT_ARG_VM}.
	 * 
	 * @param props
	 *            sampling context properties having defined JVM descriptor
	 * @return list of Java VM JMX server connection URLs
	 * @throws Exception
	 *             if resolution of Java VM JMX server connection URLs fails
	 */
	protected static List<VMParams<JMXServiceURL>> getAllVMs(Properties props) throws Exception {
		String criProp = props.getProperty(AGENT_ARG_CONN_RETRY_INTERVAL, String.valueOf(VMParams.CONN_RETRY_INTERVAL));
		long cri = Long.parseLong(criProp);

		VMParams<String> vmdp = new VMDescriptorParams(props.getProperty(AGENT_ARG_VM))
				.setUser(props.getProperty(AGENT_ARG_USER)).setPass(props.getProperty(AGENT_ARG_PASS))
				.setAgentOptions(props.getProperty(AGENT_ARG_OPTIONS, DEFAULT_AGENT_OPTIONS)).setReconnectInterval(cri);

		return vmResolverFactory.getJmxServiceURLs(vmdp);
	}

	/**
	 * Connects to all provided JMX servers using provided URLs and sampling context properties.
	 * 
	 * @param allVMs
	 *            list of Java VM JMX server connection URLs
	 * @param props
	 *            sampling context properties
	 * @throws Exception
	 *             if JMX server connection initialization fails
	 */
	public static void connectAll(List<VMParams<JMXServiceURL>> allVMs, Properties props) throws Exception {
		if (Utils.isEmpty(allVMs)) {
			return;
		}

		if (props == null) {
			props = clProps;
		}

		@SuppressWarnings("unchecked")
		Map<String, ?> connParams = (Map<String, ?>) props.get(AGENT_CONN_PARAMS);

		for (VMParams<JMXServiceURL> cp : allVMs) {
			if (cp.equals(JMXURLConnectionParams.ASYNC_CONN)) {
				// Asynchronous connections will be handled by VM resolver
			} else {
				SamplingAgentThread sat = new SamplingAgentThread(cp, connParams);
				sat.start();
			}
		}
	}

	/**
	 * Returns map of all registered samplers.
	 *
	 * @return map of all registered samplers
	 */
	public static List<Sampler> getAllSamplers() {
		List<Sampler> samplers = new ArrayList<>(5);
		for (SamplingAgent agent : ALL_AGENTS) {
			if (agent != null) {
				samplers.addAll(agent.getSamplers().values());
			}
		}
		return samplers;
	}

	/**
	 * Returns collection of all currently running sampling agents.
	 * 
	 * @return collection of all running sampling agents
	 */
	public static Collection<SamplingAgent> getAllAgents() {
		return ALL_AGENTS;
	}

	private static boolean parseArgs(Properties props, String... args) {
		LOGGER.log(OpLevel.INFO, "SamplingAgent.parseArgs(): args={0}", Arrays.toString(args));
		boolean ac = StringUtils.equalsAnyIgnoreCase(args[0], AGENT_MODE_ATTACH, AGENT_MODE_CONNECT);
		boolean local = AGENT_MODE_LOCAL.equalsIgnoreCase(args[0]);
		boolean external = false;

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
							connParams = new HashMap<>(5);
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
					} else if (arg.startsWith(PARAM_AGENT_CONNECTIONS_CONFIG_FILE)) {
						if (StringUtils.isNotEmpty(props.getProperty(AGENT_ARG_VM))) {
							LOGGER.log(OpLevel.WARNING,
									"SamplingAgent.parseArgs: JVM descriptor already defined. Can not use argument [{0}] multiple times.",
									PARAM_AGENT_CONNECTIONS_CONFIG_FILE);
							return false;
						}

						external = true;
						setProperty(props, arg, PARAM_AGENT_CONNECTIONS_CONFIG_FILE, AGENT_ARG_VM,
								FileVMResolver.PREFIX);
					} else if (arg.equalsIgnoreCase(PARAM_AGENT_DISABLE_SSL_VALIDATION)) {
						props.setProperty(AGENT_ARG_DISABLE_SSL, "true");
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

			if (StringUtils.isEmpty(props.getProperty(AGENT_ARG_VM)) && ac && !external) {
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
			if (args.length > 5) {
				props.setProperty(AGENT_ARG_BATCH_SIZE, args[5]);
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

	private static void setProperty(Properties props, String arg, String argName, String agentArgName, String prefix)
			throws IllegalArgumentException {
		setProperty(props, arg, argName, agentArgName);

		String pValue = props.getProperty(agentArgName);
		if (!pValue.startsWith(prefix)) {
			props.setProperty(agentArgName, prefix + pValue);
		}
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public void sample() throws IOException {
		SamplingArgs sa = new SamplingArgs();
		Map<String, Object> samplerCfg = sa.getAsMap();

		sample(samplerCfg);
	}

	/**
	 * Schedule sample with default MBean server instance as well as all registered MBean servers within the JVM.
	 *
	 * @param samplerConfig
	 *            sampler configuration map
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	public void sample(Map<String, Object> samplerConfig) throws IOException {
		SamplerFactory sFactory = initPlatformJMX(samplerConfig, null);

		// find other registered MBean servers and initiate sampling for all
		ArrayList<MBeanServer> mbsList = MBeanServerFactory.findMBeanServer(null);
		for (MBeanServer server : mbsList) {
			Sampler jmxSampler = STREAM_SAMPLERS.get(server);
			if (jmxSampler == null) {
				jmxSampler = sFactory.newInstance(new JMXMBeanServerConnection(server));
				scheduleSampler(samplerConfig, sFactory, jmxSampler, STREAM_SAMPLERS);
			}
		}
	}

	/**
	 * Schedule sample using defined JMX connector to get MBean server connection instance to monitored JVM.
	 *
	 * @param samplerConfig
	 *            sampler configuration map
	 * @param conn
	 *            JMX connector to get MBean server connection instance
	 *
	 * @throws IOException
	 *             if IO exception occurs while initializing MBeans sampling
	 */
	protected SamplerFactory initPlatformJMX(Map<String, Object> samplerConfig, JMXConnector conn) throws IOException {
		// obtain a default sample factory
		SamplerFactory sFactory = DefaultSamplerFactory
				.getInstance(Utils.getConfProperty(DEFAULTS, "com.jkoolcloud.tnt4j.stream.jmx.sampler.factory"));

		if (platformJmx == null) {
			synchronized (STREAM_SAMPLERS) {
				// create new sampler with default MBeanServer instance
				platformJmx = conn == null ? sFactory.newInstance()
						: sFactory.newInstance(new JMXMBeanServerConnection(conn.getMBeanServerConnection()));
				// schedule sample with a given filter and sampling period
				scheduleSampler(samplerConfig, sFactory, platformJmx, STREAM_SAMPLERS);
			}
		}

		return sFactory;
	}

	private void scheduleSampler(Map<String, Object> samplerConfig, SamplerFactory sFactory, Sampler sampler,
			Map<JMXServerConnection, Sampler> agents) throws IOException {
		agents.put(sampler.getMBeanServer(), sampler);
		samplerConfig.put(SampleHandler.CFG_SOURCE, JMXSourceUtils.getSource(getClass(), LOGGER));
		sampler.setSchedule(samplerConfig).addListener(sFactory.newListener(LISTENER_PROPERTIES))
				.addListener(new SamplerFailureListener(this));

		if (synchronousSamplers) {
			sampler.run();
		} else {
			Thread t = new Thread(sampler, "Sampler-thread-" + ALL_AGENTS.size() + "-" + STREAM_SAMPLERS.size());
			t.start();
		}
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
		if (StringUtils.isEmpty(vmDescr)) {
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
		if (StringUtils.isNotEmpty(tnt4jConf)) {
			String tnt4jPropPath = new File(tnt4jConf).getAbsolutePath();
			agentOptions += "!" + SYS_PROP_TNT4J_CFG + "=" + tnt4jPropPath;
		}
		String log4jConf = System.getProperty(LOG4J_PROPERTIES_KEY);
		if (StringUtils.isNotEmpty(log4jConf)) {
			String log4jPropPath = new File(log4jConf).getAbsolutePath();
			agentOptions += "!" + SYS_PROP_LOG4J_CFG + "=" + log4jPropPath;
		}

		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.attach: attaching JVM using vmDescr={0}, agentJarPath={1}, agentOptions={2}", vmDescr,
				agentJarPath, agentOptions);
		JDKToolsVMResolver.attachVM(vmDescr, agentPath, agentOptions);
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
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link JMXServerConnection} to collect
	 * samples.
	 *
	 * @param vmDescr
	 *            JVM descriptor: JMX service URI, local JVM name fragment or pid
	 * @param options
	 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
	 *            separated list of mbean filters and {@code initDelay} is optional
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see #connectAll(java.util.List, java.util.Properties)
	 */
	public void connect(String vmDescr, String options) throws Exception {
		VMParams<String> cp = new VMDescriptorParams(vmDescr).setAgentOptions(options);
		List<VMParams<JMXServiceURL>> jmxConns = vmResolverFactory.getJmxServiceURLs(cp);

		connectAll(jmxConns, null);
	}

	/**
	 * Connects to {@code vmDescr} defined JVM over {@link JMXConnector} an uses {@link JMXServerConnection} to collect
	 * samples.
	 *
	 * @param connectionParams
	 *            JMX service connection parameters
	 * @param connParams
	 *            map of additional JMX connection parameters, defined by {@link javax.naming.Context}
	 * @throws Exception
	 *             if any exception occurs while connecting to JVM
	 *
	 * @see JMXConnectorFactory#connect(javax.management.remote.JMXServiceURL, java.util.Map)
	 * @see javax.naming.Context
	 */
	public void connect(VMParams<JMXServiceURL> connectionParams, Map<String, ?> connParams) throws Exception {
		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.connect(): url={0}, user={1}, pass={2}, options={3}, connParams={4}, connRetryInterval={5}",
				connectionParams.getVMRef(), connectionParams.getUser(),
				Utils.hideEnd(connectionParams.getPass(), "*", 0), connectionParams.getAgentOptions(), connParams,
				connectionParams.getReconnectInterval());

		if (connectionParams.getVMRef() == null) {
			throw new RuntimeException("JMX service URL is null!..");
		}

		Map<String, Object> params = new HashMap<>(connParams == null ? 1 : connParams.size() + 1);

		if (StringUtils.isNotEmpty(connectionParams.getUser()) && StringUtils.isNotEmpty(connectionParams.getPass())) {
			LOGGER.log(OpLevel.INFO,
					"SamplingAgent.connect: adding user login and password to connection credentials...");
			String[] credentials = new String[] { connectionParams.getUser(), connectionParams.getPass() };
			params.put(JMXConnector.CREDENTIALS, credentials);
		}

		if (connParams != null) {
			params.putAll(connParams);
		}

		do {
			VMParams.ReconnectRule reconnectRule = connectionParams.getReconnectRule();
			long connRetryInterval = connectionParams.getReconnectInterval();
			stopSampling = reconnectRule.shouldStopSampling() || connRetryInterval < 0;

			try {
				LOGGER.log(OpLevel.INFO, "SamplingAgent.connect: connecting JMX service using URL={0}",
						connectionParams.getVMRef());
				connector = JMXConnectorFactory.connect(connectionParams.getVMRef(), params);

				try {
					NotificationListener cnl = new NotificationListener() {
						@Override
						public void handleNotification(Notification notification, Object key) {
							if (notification.getType().contains("closed") || notification.getType().contains("failed")
									|| notification.getType().contains("lost")) {
								LOGGER.log(OpLevel.INFO,
										"NotificationListener.handleNotification: JMX connection status change: {0}",
										notification.getType());
								stopSampler();
							}
						}
					};
					connector.addConnectionNotificationListener(cnl, null, null);

					startSamplerAndWait(connectionParams.getAgentOptions(), connector);

					connector.removeConnectionNotificationListener(cnl);

					for (Sampler sampler : STREAM_SAMPLERS.values()) {
						Throwable le = sampler.getContext().getLastError();
						if (le instanceof IOException) {
							throw (IOException) le;
						}
					}
				} finally {
					shutdownSamplers();

					Utils.close(connector);
				}
			} catch (IOException exc) {
				LOGGER.log(OpLevel.ERROR, "SamplingAgent.connect: failed to connect JMX service");
				LOGGER.log(OpLevel.ERROR, "            Exception: {0}", Utils.getExceptionMessages(exc));
				LOGGER.log(OpLevel.ERROR, "            Reconnect rule: {0}", reconnectRule);

				if (!stopSampling && connRetryInterval > 0) {
					LOGGER.log(OpLevel.INFO, "SamplingAgent.connect: will retry connect attempt in {0} seconds...",
							connRetryInterval);
					TimeUnit.SECONDS.sleep(connRetryInterval);
				}

				// TODO: if connection was obtained from ZooKeeper, after 3 retries it shall redo VMs resolution from ZK
				// again since ZK and kafka service may have been restarted.
			}
		} while (!stopSampling);
	}

	/**
	 * Returns JMX connector instance used by this sampler.
	 *
	 * @return jmx connector instance used by this sampler
	 */
	public JMXConnector getConnector() {
		return connector;
	}

	/**
	 * Releases sampler lock.
	 */
	protected void stopSampler() {
		this.stopSamplerLatch.set(true);

		if (platformJmx != null) {
			synchronized (platformJmx) {
				LOGGER.log(OpLevel.INFO, "SamplingAgent.stopSampler: releasing sampler lock: {0}...", platformJmx);

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
		SamplingAgent samplingAgent = newSamplingAgent(true);
		if (wait) {
			samplingAgent.startSamplerAndWait(options);
		} else {
			samplingAgent.startSampler(options);
		}
	}

	private void startSamplerAndWait(String options) throws Exception {
		startSamplerAndWait(options, null);
	}

	private void startSamplerAndWait(String options, JMXConnector connector) throws Exception {
		startSampler(options, connector);

		lockSampler();
	}

	private void lockSampler() throws InterruptedException {
		if (stopSamplerLatch.get()) {
			stopSamplerLatch.set(false);
			return;
		}

		if (platformJmx != null) {
			synchronized (platformJmx) {
				LOGGER.log(OpLevel.INFO, "SamplingAgent.startSamplerAndWait: locking on sampler: {0}...", platformJmx);

				platformJmx.wait();
			}
		}

		LOGGER.log(OpLevel.INFO, "SamplingAgent.startSamplerAndWait: stopping Stream-JMX...");
	}

	private void startSampler(String options) throws Exception {
		startSampler(options, null);
	}

	private void startSampler(String options, JMXConnector connector) throws Exception {
		SamplingArgs sa = SamplingArgs.parse(options);
		Map<String, Object> samplerCfg = sa.getAsMap();
		samplerCfg.put(SampleHandler.CFG_JMX_CONNECTOR, connector);

		if (connector == null) {
			sample(samplerCfg);
		} else {
			initPlatformJMX(samplerCfg, connector);
		}

		LOGGER.log(OpLevel.INFO,
				"SamplingAgent.startSampler: sampler.config={0}, listener.properties={1}, tnt4j.config={2}, jmx.sample.list={3}",
				samplerCfg, LISTENER_PROPERTIES, System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY),
				STREAM_SAMPLERS);
	}

	/**
	 * Obtain a map of all scheduled JMXServerConnections and associated sample references.
	 *
	 * @return map of all scheduled JMXServerConnections and associated sample references.
	 */
	public Map<JMXServerConnection, Sampler> getSamplers() {
		HashMap<JMXServerConnection, Sampler> copy = new HashMap<>(89);
		copy.putAll(STREAM_SAMPLERS);
		return copy;
	}

	/**
	 * Cancel and close all outstanding {@link Sampler} instances and stop all sampling for all {@link MBeanServer}
	 * instances.
	 */
	public void cancel() {
		for (Sampler sampler : STREAM_SAMPLERS.values()) {
			sampler.cancel();
		}
		STREAM_SAMPLERS.clear();
	}

	/**
	 * Cancel and close all sampling for a given {@link JMXServerConnection} instance.
	 *
	 * @param mServerConn
	 *            JMXServerConnection instance
	 */
	public void cancel(JMXServerConnection mServerConn) {
		Sampler sampler = STREAM_SAMPLERS.remove(mServerConn);
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
		for (SamplingAgent agent : ALL_AGENTS) {
			synchronized (agent.STREAM_SAMPLERS) {
				agent.stopSampling = true;
				agent.stopSampler();
				agent.shutdownSamplers();
			}
		}
		ALL_AGENTS.clear();
		DefaultEventSinkFactory.shutdownAll();

		vmResolverFactory.shutdown();
		clProps.clear();
	}

	private void shutdownSamplers() {
		cancel();
		if (platformJmx != null) {
			synchronized (platformJmx) {
				platformJmx = null;
			}
		}
	}

	private static void initDefaults(Properties defProps) {
		Map<String, Properties> defPropsMap = new HashMap<>();

		try {
			Properties p = Utils.loadPropertiesResources("sjmx-defaults.properties"); // NON-NLS

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

				Properties dpe = defPropsMap.get(key);

				if (dpe == null) {
					dpe = new Properties();
					defPropsMap.put(key, dpe);
				}

				dpe.setProperty(pKey, (String) pe.getValue());
			}
		} catch (Exception exc) {
		}

		if (!defPropsMap.isEmpty()) {
			List<Properties> fPropList = new ArrayList<>(defPropsMap.size());
			for (Map.Entry<String, Properties> dpe : defPropsMap.entrySet()) {
				String apiLevel = dpe.getKey(); // NON-NLS
				int level = 0;
				if (StringUtils.isNotEmpty(apiLevel)) {
					try {
						level = Integer.parseInt(apiLevel);
					} catch (NumberFormatException e) {
					}
				}

				fPropList.add(level, dpe.getValue());
			}

			for (Properties fProps : fPropList) {
				defProps.putAll(fProps);
			}
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

	private static class SamplingArgs {
		/**
		 * Included MBeans filter string.
		 */
		String incFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.include.filter", Sampler.JMX_FILTER_ALL);
		/**
		 * Excluded MBeans filter string.
		 */
		String excFilter = System.getProperty("com.jkoolcloud.tnt4j.stream.jmx.exclude.filter",
				Sampler.JMX_FILTER_NONE);
		/**
		 * Sampling period in milliseconds.
		 */
		int period = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.period", Sampler.JMX_SAMPLE_PERIOD);
		/**
		 * Initial sampling delay in milliseconds.
		 */
		int initDelay = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.init.delay", period);
		/**
		 * Sampled activity snapshots count to post over single batch. {@code -1} means unlimited.
		 */
		int batchSize = Integer.getInteger("com.jkoolcloud.tnt4j.stream.jmx.batch.size", -1);
		/**
		 * Time units for sampling period and initial delay values.
		 */
		TimeUnit timeUnit = TimeUnit.MILLISECONDS;

		private SamplingArgs() {
		}

		/**
		 * Parses sampling arguments set from provided {@code options} string.
		 * 
		 * @param options
		 *            '!' separated list of options mbean-filter!sample.ms!initDelay.ms, where mbean-filter is semicolon
		 *            separated list of mbean filters and {@code initDelay} is optional
		 * @return sampling arguments instance with pased values
		 */
		static SamplingArgs parse(String options) {
			SamplingArgs sa = new SamplingArgs();

			if (options != null) {
				int nextIdx = 0;
				String[] args = options.split("!");
				try {
					sa.incFilter = args[nextIdx++];
				} catch (IndexOutOfBoundsException exc) {
				}
				boolean periodFound = false;
				try {
					sa.period = Integer.parseInt(args[nextIdx]);
					nextIdx++;
					periodFound = true;
				} catch (NumberFormatException | IndexOutOfBoundsException exc) {
					try {
						sa.excFilter = args[nextIdx++];
					} catch (IndexOutOfBoundsException bexc) {
					}
				}

				if (!periodFound) {
					try {
						sa.period = Integer.parseInt(args[nextIdx++]);
					} catch (NumberFormatException | IndexOutOfBoundsException exc) {
					}
				}
				try {
					sa.initDelay = Integer.parseInt(args[nextIdx++]);
				} catch (NumberFormatException | IndexOutOfBoundsException exc) {
				}
				try {
					sa.batchSize = Integer.parseInt(args[nextIdx++]);
				} catch (NumberFormatException | IndexOutOfBoundsException exc) {
				}
			}

			return sa;
		}

		private Map<String, Object> getAsMap() {
			Map<String, Object> cfgMap = new HashMap<>(6);
			cfgMap.put(SampleHandler.CFG_INCLUDE_FILTER, incFilter);
			cfgMap.put(SampleHandler.CFG_EXCLUDE_FILTER, excFilter);
			cfgMap.put(SampleHandler.CFG_BATCH_SIZE, batchSize);

			cfgMap.put(Sampler.CFG_INITIAL_DELAY, initDelay);
			cfgMap.put(Sampler.CFG_SAMPLING_PERIOD, period);
			cfgMap.put(Sampler.CFG_TIME_UNIT, timeUnit);

			return cfgMap;
		}
	}

	/**
	 * Disables SSL context verification.
	 */
	protected static void disableSslVerification() {
		try {
			// Create a trust manager that does not validate certificate chains
			TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
				@Override
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				@Override
				public void checkClientTrusted(X509Certificate[] certs, String authType) {
				}

				@Override
				public void checkServerTrusted(X509Certificate[] certs, String authType) {
				}
			} };

			// Install the all-trusting trust manager
			SSLContext sc = SSLContext.getInstance("TLS"); // NON-NLS
			sc.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

			// Create all-trusting host name verifier
			HostnameVerifier allHostsValid = new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
					return true;
				}
			};

			// Install the all-trusting host verifier
			HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
		} catch (GeneralSecurityException exc) {
			LOGGER.log(OpLevel.WARNING, "Failed to disable SSL verification: {0}", exc);
		}
	}

	static String pkgVersion() {
		Package sPkg = SamplingAgent.class.getPackage();
		return sPkg.getImplementationVersion();
	}

	static String runEnv() {
		String[] envProps = new String[] { // set of interesting runtime environment properties
				"java.version", "java.vendor", "java.vm.name", "java.vm.version", // JVM props
				"os.name", "os.version" // OS props
		};

		StringBuilder sb = new StringBuilder();
		sb.append(System.lineSeparator());
		sb.append("------------------------------------------------------------------------") // NON-NLS
				.append(System.lineSeparator());
		for (String property : envProps) {
			sb.append(String.format("%20s: %s", // NON-NLS
					property, System.getProperty(property)));
			sb.append(System.lineSeparator());
		}
		sb.append("------------------------------------------------------------------------") // NON-NLS
				.append(System.lineSeparator());

		return sb.toString();
	}
}