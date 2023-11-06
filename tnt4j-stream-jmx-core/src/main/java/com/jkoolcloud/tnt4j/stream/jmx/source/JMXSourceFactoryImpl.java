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

package com.jkoolcloud.tnt4j.stream.jmx.source;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.net.InetAddressUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.source.DefaultSource;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.source.SourceFactoryImpl;
import com.jkoolcloud.tnt4j.source.SourceType;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgentThread;
import com.jkoolcloud.tnt4j.stream.jmx.core.JMXServerConnection;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * This class extends default implementation of {@link com.jkoolcloud.tnt4j.source.SourceFactoryImpl} by adding
 * possibility to fill in source field values from JMX MBean attributes and properties.
 * <p>
 * This factory can resolve two types of MBean provided values:
 * <ul>
 * <li>Mbean attribute value - descriptor pattern is {@code "@bean:MBean_ObjectName/?AttributeName"}. It also allows use
 * of wildcard symbols {@code *}, e.g.: {@code "@bean:org.apache.ZooKeeperService:name0=*&#47;?ClientPort"} resolving
 * {@code ClientPort} value for first available (if more than one is running on same JVM, but usually it should be only
 * one) ZooKeeper service instance.</li>
 * <li>MBean key property - descriptor pattern is {@code "@bean:MBean_ObjectName:PropertyKey=?(,OtherProperties)"} (part
 * within {@code ()} is optional), e.g.: {@code "@bean:kafka.server:id=?,type=app-info"}.</li>
 * </ul>
 * <p>
 * Also this factory resolves {@link javax.management.remote.JMXAddressable} provided address host name or IP using
 * source value placeholders:
 * <ul>
 * <li>{@code @sjmx.serverAddress} - JMX server IP address</li>
 * <li>{@code @sjmx.serverName} - JMX server host name</li>
 * </ul>
 * <p>
 * TNT4J source factory configuration using this source factory implementation would be like this:
 *
 * for MBean value descriptor:
 * 
 * <pre>
 * {@code
 *  source: com.jkoolcloud.tnt4j.stream.jmx
 * 	source.factory: com.jkoolcloud.tnt4j.stream.jmx.source.JMXSourceFactoryImpl
 * 	source.factory.GENERIC: Streams
 * 	source.factory.DATACENTER: HQDC
 * 	source.factory.SERVER: @bean:JMImplementation:type=MBeanServerDelegate/?MBeanServerId
 * 	source.factory.SERVICE: @bean:org.apache.activemq:type=Broker,brokerName=localhost/?BrokerId
 * 	source.factory.RootFQN: SERVICE=?#SERVER=?#DATACENTER=?#GENERIC=?
 * }
 * </pre>
 *
 * for MBean value descriptor:
 * 
 * <pre>
 * {@code
 *  source: com.jkoolcloud.tnt4j.stream.jmx
 *  source.factory: com.jkoolcloud.tnt4j.stream.jmx.source.JMXSourceFactoryImpl
 *  source.factory.GENERIC: Streams
 *  source.factory.DATACENTER: HQDC
 *  source.factory.SERVER: @sjmx.serverName
 *  source.factory.SERVICE: @bean:org.apache.activemq:type=Broker,brokerName=localhost/?BrokerId
 *  source.factory.RootFQN: SERVICE=?#SERVER=?#DATACENTER=?#GENERIC=?
 * }
 * </pre>
 *
 * @version $Revision: 2 $
 */
public class JMXSourceFactoryImpl extends SourceFactoryImpl {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(JMXSourceFactoryImpl.class);

	private static JMXSourceFactoryImpl factory = new JMXSourceFactoryImpl();

	public static final String SOURCE_SERVER_ADDRESS = "sjmx.serverAddress";
	public static final String SOURCE_SERVER_NAME = "sjmx.serverName";
	public static final String SOURCE_SERVICE_ID = "sjmx.serviceId";

	private static final String SJMX_PROP_PREFIX = "@sjmx.";
	private static final String MBEAN_PREFIX = "@bean:";
	private static final String MBEAN_ATTR_DELIM = "/?";

	/**
	 * Constructs a new instance of JMXSourceFactoryImpl.
	 */
	public JMXSourceFactoryImpl() {
		super();
	}

	/**
	 * Obtain a default instance of this source factory.
	 * 
	 * @return default instance of this factory
	 */
	public static JMXSourceFactoryImpl getInstance() {
		return factory;
	}

	@Override
	protected String getNameFromType(String name, SourceType type) {
		return getNameFromType(name, type, false);
	}

	protected String getNameFromType(String name, SourceType type, boolean throwFailure) {
		if (name.startsWith(MBEAN_PREFIX)) {
			String beanAttrName = name.substring(MBEAN_PREFIX.length());

			try {
				if (isAttributeNamePattern(beanAttrName)) {
					return getAttributeValue(beanAttrName);
				} else {
					return getKeyPropertyValue(beanAttrName);
				}
			} catch (Exception e) {
				if (e instanceof IOException) {
					LOGGER.log(OpLevel.ERROR,
							"Failed to resolve MBean attribute ''{0}'' value for source field {1}, reason: {2}", name,
							type, Utils.getExceptionMessages(e));
					if (throwFailure) {
						throw new JMXCommunicationException(e);
					}
				} else {
					LOGGER.log(OpLevel.ERROR, "Failed to resolve MBean attribute ''{0}'' value for source field {1}",
							name, type, e);
				}
			}

			return UNKNOWN_SOURCE;
		} else if (name.startsWith(SJMX_PROP_PREFIX)) {
			return resolveSJMXPropValue(name.substring(1));
		}

		return super.getNameFromType(name, type);
	}

	private static boolean isAttributeNamePattern(String oNameStr) {
		String[] split = oNameStr.split(Pattern.quote(MBEAN_ATTR_DELIM));
		return split.length == 2;
	}

	private static String getAttributeValue(String oNameStr) throws Exception {
		String[] paths = oNameStr.split(Pattern.quote(MBEAN_ATTR_DELIM));
		if (paths.length != 2) {
			throw new IllegalArgumentException(Utils.format(
					"MBean attribute descriptor ''{2}'' should contain valid MBean object name and attribute name delimited by ''{1}'', e.g.: {0}JMImplementation:type=MBeanServerDelegate{1}MBeanServerId",
					MBEAN_PREFIX, MBEAN_ATTR_DELIM, oNameStr));
		}
		String objectNamePart = paths[0];
		String attributeNamePart = paths[1];

		JMXServerConnection mBeanServerConn = getMBeanServerConnection();
		Set<ObjectInstance> objects = getRepeating(new RepeatingSupplier<>() {
			@Override
			public boolean isComplete(Set<ObjectInstance> value) {
				return !Utils.isEmpty(value);
			}

			@Override
			public Set<ObjectInstance> get() throws ExecutionException {
				try {
					return mBeanServerConn.queryMBeans(new ObjectName(objectNamePart), null);
				} catch (Exception exc) {
					throw new ExecutionException("Unrecoverable MBean query exception", exc);
				}
			}
		});

		if (Utils.isEmpty(objects)) {
			return UNKNOWN_SOURCE;
		} else {
			ObjectName objectName = objects.iterator().next().getObjectName();
			Object attribute = mBeanServerConn.getAttribute(objectName, attributeNamePart);
			return Utils.toString(attribute);
		}
	}

	private static JMXServerConnection getMBeanServerConnection() {
		Thread thread = Thread.currentThread();

		if (thread instanceof SamplingAgentThread) {
			Map<JMXServerConnection, Sampler> samplers = ((SamplingAgentThread) thread).getSamplingAgent()
					.getSamplers();
			if (!Utils.isEmpty(samplers)) {
				Set<JMXServerConnection> connSet = samplers.keySet();
				return Utils.getLast(connSet.iterator());
			}
		} else {
			List<Sampler> samplers = SamplingAgent.getAllSamplers();
			if (!Utils.isEmpty(samplers)) {
				return samplers.get(samplers.size() - 1).getMBeanServer();
			}
		}

		throw new IllegalStateException("MBean server connection not found for thread " + thread);
	}

	private static String getKeyPropertyValue(String oNameStr) throws Exception {
		String queryName = oNameStr.replace("?", "*"); // NON-NLS
		ObjectName oName = new ObjectName(queryName);
		Set<ObjectInstance> objects = getRepeating(new RepeatingSupplier<>() {
			@Override
			public boolean isComplete(Set<ObjectInstance> value) {
				return !Utils.isEmpty(value);
			}

			@Override
			public Set<ObjectInstance> get() throws ExecutionException {
				try {
					return getMBeanServerConnection().queryMBeans(oName, null);
				} catch (Exception exc) {
					throw new ExecutionException("Unrecoverable MBean query exception", exc);
				}
			}
		});

		if (!Utils.isEmpty(objects)) {
			ObjectInstance first = objects.iterator().next();
			Map<String, String> oNameProps = new ObjectName(oNameStr).getKeyPropertyList();

			for (Map.Entry<String, String> kProp : oNameProps.entrySet()) {
				String key = kProp.getKey();
				String value = kProp.getValue();
				if ("?".equals(value) && oName.isPropertyValuePattern(key)) { // NON-NLS
					return first.getObjectName().getKeyProperty(key);
				}
			}
		}

		throw new IllegalArgumentException("MBean key property not found for " + oNameStr);
	}

	private static String resolveSJMXPropValue(String propName) {
		if (StringUtils.equalsAny(propName, SOURCE_SERVER_ADDRESS, SOURCE_SERVER_NAME)) {
			Thread thread = Thread.currentThread();
			String host = null;

			javax.management.remote.JMXConnector jmxConn = null;
			if (thread instanceof SamplingAgentThread) {
				jmxConn = ((SamplingAgentThread) thread).getSamplingAgent().getConnector();
			} else {
				Collection<SamplingAgent> agents = SamplingAgent.getAllAgents();
				if (!Utils.isEmpty(agents)) {
					jmxConn = Utils.getLast(agents.iterator()).getConnector();
				}
			}

			// get MBeanServerConnection from JMX RMI connector
			if (jmxConn instanceof JMXAddressable) {
				host = getHost((JMXAddressable) jmxConn);
			}

			if (SOURCE_SERVER_ADDRESS.equals(propName)) {
				return StringUtils.isEmpty(host) ? Utils.getLocalHostAddress() : Utils.resolveHostNameToAddress(host);
			} else if (SOURCE_SERVER_NAME.equals(propName)) {
				return StringUtils.isEmpty(host) ? Utils.getLocalHostName()
						: isInetAddress(host) ? Utils.resolveAddressToHostName(host) : host;
			}
		}

		return UNKNOWN_SOURCE;
	}

	private static String getHost(JMXAddressable jmxAddressable) {
		JMXServiceURL url = jmxAddressable.getAddress();
		String host = url == null ? null : url.getHost();

		if (url != null && host.isEmpty()) {
			String path = url.getURLPath();
			URI uri;
			if (path.startsWith("/jndi/")) {
				uri = URI.create(url.getURLPath().substring("/jndi/".length()));
			} else if (path.startsWith("/stub/")) {
				uri = URI.create(url.getURLPath().substring("/stub/".length()));
			} else if (path.startsWith("/ior/")) {
				uri = URI.create(url.getURLPath().substring("/ior/".length()));
			} else {
				uri = URI.create(url.getURLPath());
			}

			host = uri.getHost();
		}

		return host;
	}

	private static boolean isInetAddress(String address) {
		return InetAddressUtils.isIPv4Address(address);
	}

	@Override
	public Source fromFQN(String fqn, Source parent) {
		return createFromFQN(fqn, parent);
	}

	@Override
	public Source newFromFQN(String fqn) {
		return createFromFQN(fqn, null);
	}

	private Source createFromFQN(String fqn, Source parent) {
		StringTokenizer tk = new StringTokenizer(fqn, "#");
		DefaultSource child = null, root = null;

		while (tk.hasMoreTokens()) {
			String sName = tk.nextToken();
			int firstEqSign = sName.indexOf("=");
			String typeS = sName.substring(0, firstEqSign);
			String valueS = sName.substring(++firstEqSign);
			SourceType type = SourceType.valueOf(typeS);
			DefaultSource source = new DefaultSource(this, getNameFromType(valueS, type, true), type, null,
					getNameFromType("?", SourceType.USER, true));
			if (child != null) {
				child.setSource(source);
			}
			if (root == null) {
				root = source;
			}
			child = source;
		}

		if (child != null) {
			child.setSource(parent);
		}

		return root;
	}

	private static <T> T getRepeating(RepeatingSupplier<T> getter) throws Exception {
		int intervalSec = 1;
		int retryCount = 0;
		T value = null;

		do {
			if (retryCount > 0) {
				LOGGER.log(OpLevel.INFO, "Will retry to query MBeans after {0} sec.", intervalSec);
				try {
					Thread.sleep(TimeUnit.SECONDS.toMillis(intervalSec));
				} catch (InterruptedException exc) {
				}
			}
			try {
				value = getter.get();
			} catch (ExecutionException exc) {
				throw (Exception) exc.getCause();
			}
		} while (!getter.isComplete(value) && retryCount < 3);

		return value;
	}

	private interface RepeatingSupplier<T> {
		T get() throws ExecutionException;

		boolean isComplete(T value);
	}

}
