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

package com.jkoolcloud.tnt4j.stream.jmx.source;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import javax.management.*;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.DefaultEventSinkFactory;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.source.DefaultSource;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.source.SourceFactoryImpl;
import com.jkoolcloud.tnt4j.source.SourceType;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgentThread;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * This class extends default implementation of {@link com.jkoolcloud.tnt4j.source.SourceFactoryImpl} by adding
 * possibility to fill in source field values from JMX MBean attributes and properties.
 * <p>
 * This factory can resolve two types of MBean provided values:
 * <ul>
 * <li>Mbean attribute value - descriptor pattern is {@code "@bean:MBean_ObjectName/?AttributeName"}. It also allows use
 * of wildcard symbols {@code *}, e.g.: {@code "@bean:org.apache.ZooKeeperService:name0=#&002A;/?ClientPort"} resolving
 * {@code ClientPort} value for first available (if more than one is running on same JVM, but usually it should be only
 * one) ZooKeeper service instance.</li>
 * <li>MBean key property - descriptor pattern is {@code "@bean:MBean_ObjectName:PropertyKey=?(,OtherProperties)"} (part
 * within {@code ()} is optional), e.g.: {@code "@bean:kafka.server:id=?,type=app-info"}.</li>
 * </ul>
 * <p>
 * TNT4J source factory configuration using this source factory implementation would be like this:
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
 * @version $Revision: 2 $
 */
public class JMXSourceFactoryImpl extends SourceFactoryImpl {
	private static final EventSink LOGGER = DefaultEventSinkFactory.defaultEventSink(JMXSourceFactoryImpl.class);

	private static final String MBEAN_PREFIX = "@bean:";
	private static final String MBEAN_ATTR_DELIM = "/?";

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

			return Utils.resolve(name, UNKNOWN_SOURCE);
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
		ObjectName objectName = objects.iterator().next().getObjectName();
		Object attribute = mBeanServerConn.getAttribute(objectName, attributeNamePart);
		return Utils.toString(attribute);
	}

	private static MBeanServerConnection getMBeanServerConnection() {
		Thread thread = Thread.currentThread();

		if (thread instanceof SamplingAgentThread) {
			Map<MBeanServerConnection, Sampler> samplers = ((SamplingAgentThread) thread).getSamplingAgent()
					.getSamplers();
			if (!Utils.isEmpty(samplers)) {
				return samplers.entrySet().iterator().next().getKey();
			}
		}

		throw new IllegalStateException("MBean server connection not found");
	}

	private static String getKeyPropertyValue(String oNameStr) throws MalformedObjectNameException, IOException {
		String queryName = oNameStr.replace("?", "*");
		ObjectName oName = new ObjectName(queryName);
		Set<ObjectInstance> objects = getMBeanServerConnection().queryMBeans(oName, null);
		ObjectInstance first = objects.iterator().next();

		for (Map.Entry<String, String> a : oName.getKeyPropertyList().entrySet()) {
			String key = a.getKey();
			if (oName.isPropertyValuePattern(key)) {
				return first.getObjectName().getKeyProperty(key);
			}
		}

		throw new IllegalStateException("MBean key property not found for " + oNameStr);
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
