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

import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

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
 * possibility to fill in source field values from JMX MBean attributes. To map define source field to MBean attribute
 * use pattern {@code "@bean:MBean_ObjectName/?AttributeName"}, e.g.:
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
 * @version $Revision: 1 $
 */
public class JMXSourceFactoryImpl extends SourceFactoryImpl {
	private static final EventSink LOGGER = DefaultEventSinkFactory.defaultEventSink(JMXSourceFactoryImpl.class);

	private static final String MBEAN_PREFIX = "@bean:";
	private static final String MBEAN_ATTR_DELIM = "/?";

	@Override
	protected String getNameFromType(String name, SourceType type) {
		if (name.startsWith(MBEAN_PREFIX)) {
			try {
				String[] paths = name.substring(MBEAN_PREFIX.length()).split(Pattern.quote(MBEAN_ATTR_DELIM));
				if (paths.length != 2) {
					throw new IllegalArgumentException(Utils.format(
							"MBean attribute descriptor ''{0}'' should contain valid MBean object name and attribute name delimited by ''{1}'', e.g.: {0}JMImplementation:type=MBeanServerDelegate{1}MBeanServerId",
							MBEAN_PREFIX, MBEAN_ATTR_DELIM));
				}
				String objectNamePart = paths[0];
				String attributeNamePart = paths[1];

				Thread thread = Thread.currentThread();
				if (thread instanceof SamplingAgentThread) {
					Map<MBeanServerConnection, Sampler> samplers = ((SamplingAgentThread) thread).getSamplingAgent()
							.getSamplers();
					if (!Utils.isEmpty(samplers)) {
						MBeanServerConnection mBeanServer = samplers.entrySet().iterator().next().getKey();
						return Utils
								.toString(mBeanServer.getAttribute(new ObjectName(objectNamePart), attributeNamePart));
					}
				}
			} catch (Exception e) {
				LOGGER.log(OpLevel.ERROR, "Failed to resolve MBean attribute ''{0}'' value for source field {1}", name,
						type, e);
			}

			return Utils.resolve(name, UNKNOWN_SOURCE);
		}

		return super.getNameFromType(name, type);
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
