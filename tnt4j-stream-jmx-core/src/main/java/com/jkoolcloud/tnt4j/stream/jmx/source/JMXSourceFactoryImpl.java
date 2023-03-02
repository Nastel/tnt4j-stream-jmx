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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import javax.management.*;
import javax.management.remote.JMXAddressable;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.source.DefaultSource;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.source.SourceFactoryImpl;
import com.jkoolcloud.tnt4j.source.SourceType;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgentThread;
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
 * of wildcard symbols {@code *}, e.g.: <code>"@bean:org.apache.ZooKeeperService:name0=*&#47;?ClientPort"</code>
 * resolving {@code ClientPort} value for first available (if more than one is running on same JVM, but usually it
 * should be only one) ZooKeeper service instance.</li>
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
 * 	source.factory.DATACENTER: HQDC
 * 	source.factory.SERVER: @bean:JMImplementation:type=MBeanServerDelegate/?MBeanServerId
 * 	source.factory.SERVICE: @bean:org.apache.activemq:type=Broker,brokerName=localhost/?BrokerId
 * 	source.factory.RootFQN: SERVICE=?#SERVER=?#DATACENTER=?
 * }
 * </pre>
 *
 * for MBean value descriptor:
 * 
 * <pre>
 * {@code
 *  source: com.jkoolcloud.tnt4j.stream.jmx
 *  source.factory: com.jkoolcloud.tnt4j.stream.jmx.source.JMXSourceFactoryImpl
 *  source.factory.DATACENTER: HQDC
 *  source.factory.SERVER: @sjmx.serverName
 *  source.factory.SERVICE: @bean:org.apache.activemq:type=Broker,brokerName=localhost/?BrokerId
 *  source.factory.RootFQN: SERVICE=?#SERVER=?#DATACENTER=?
 * }
 * </pre>
 *
 * @version $Revision: 2 $
 */
public class JMXSourceFactoryImpl extends SourceFactoryImpl {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(JMXSourceFactoryImpl.class);

	public static final String SOURCE_SERVER_ADDRESS = "sjmx.serverAddress";
	public static final String SOURCE_SERVER_NAME = "sjmx.serverName";
	public static final String SOURCE_SERVICE_ID = "sjmx.serviceId";

	private static final String SJMX_PROP_PREFIX = "@sjmx.";
	private static final String MBEAN_PREFIX = "@bean:";
	private static final String MBEAN_ATTR_DELIM = "/?";

	public JMXSourceFactoryImpl() {
		super();
	}

	@Override
	protected String getNameFromType(String name, SourceType type) {
		if (name.startsWith(MBEAN_PREFIX)) {
			String beanAttrName = name.substring(MBEAN_PREFIX.length());

			try {
				if (isAttributeNamePattern(beanAttrName)) {
					return getAttributeValue(beanAttrName);
				} else {
					return getKeyPropertyValue(beanAttrName);
				}
			} catch (Exception e) {
				LOGGER.log(OpLevel.ERROR, "Failed to resolve MBean attribute ''{0}'' value for source field {1}", name,
						type, e);
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

	private static String getAttributeValue(String oNameStr) throws MBeanException, AttributeNotFoundException,
			InstanceNotFoundException, ReflectionException, IOException, MalformedObjectNameException {
		String[] paths = oNameStr.split(Pattern.quote(MBEAN_ATTR_DELIM));
		if (paths.length != 2) {
			throw new IllegalArgumentException(Utils.format(
					"MBean attribute descriptor ''{2}'' should contain valid MBean object name and attribute name delimited by ''{1}'', e.g.: {0}JMImplementation:type=MBeanServerDelegate{1}MBeanServerId",
					MBEAN_PREFIX, MBEAN_ATTR_DELIM, oNameStr));
		}
		String objectNamePart = paths[0];
		String attributeNamePart = paths[1];

		MBeanServerConnection mBeanServerConn = getMBeanServerConnection();
		Set<ObjectInstance> objects = mBeanServerConn.queryMBeans(new ObjectName(objectNamePart), null);

		if (Utils.isEmpty(objects)) {
			return UNKNOWN_SOURCE;
		} else {
			ObjectName objectName = objects.iterator().next().getObjectName();
			Object attribute = mBeanServerConn.getAttribute(objectName, attributeNamePart);
			return Utils.toString(attribute);
		}
	}

	private static MBeanServerConnection getMBeanServerConnection() {
		Thread thread = Thread.currentThread();

		if (thread instanceof SamplingAgentThread) {
			Map<MBeanServerConnection, Sampler> samplers = ((SamplingAgentThread) thread).getSamplingAgent()
					.getSamplers();
			if (!Utils.isEmpty(samplers)) {
				Set<Map.Entry<MBeanServerConnection, Sampler>> samplersSet = samplers.entrySet();
				Iterator<Map.Entry<MBeanServerConnection, Sampler>> samplersIter = samplersSet.iterator();
				return samplersIter.next().getKey();
			}
		}

		throw new IllegalStateException("MBean server connection not found for thread " + thread);
	}

	private static String getKeyPropertyValue(String oNameStr) throws MalformedObjectNameException, IOException {
		String queryName = oNameStr.replace("?", "*"); // NON-NLS
		ObjectName oName = new ObjectName(queryName);
		Set<ObjectInstance> objects = getMBeanServerConnection().queryMBeans(oName, null);

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

			if (thread instanceof SamplingAgentThread) {
				JMXConnector jmxConn = ((SamplingAgentThread) thread).getSamplingAgent().getConnector();

				// get MBeanServerConnection from JMX RMI connector
				if (jmxConn instanceof JMXAddressable) {
					host = getHost((JMXAddressable) jmxConn);
				}
			}

			if (SOURCE_SERVER_ADDRESS.equals(propName)) {
				return StringUtils.isEmpty(host) ? Utils.getLocalHostAddress() : Utils.resolveHostNameToAddress(host);
			} else if (SOURCE_SERVER_NAME.equals(propName)) {
				return StringUtils.isEmpty(host) ? Utils.getLocalHostName() : Utils.resolveAddressToHostName(host);
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
			DefaultSource source = new DefaultSource(this, getNameFromType(valueS, type), type, null,
					getNameFromType("?", SourceType.USER));
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
}
