/*
 * Copyright 2014-2022 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx.vm;

import javax.management.remote.JMXServiceURL;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;

/**
 * Resolves ZooKeeper orchestrated "Apache Kafka" VMs JMX connections.
 *
 * @version $Revision: 1 $
 */
public class KafkaZKVMResolver extends ZKVMResolver {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(KafkaZKVMResolver.class);

	/**
	 * Constant defining VM descriptor prefix specific for this VM resolver: {@value}.
	 */
	static final String PREFIX = "kafka:zk:"; // NON-NLS

	/**
	 * Constructs new instance of ZooKeeper orchestrated "Apache Kafka" VMs resolver.
	 */
	public KafkaZKVMResolver() {
		logger().log(OpLevel.INFO, "Loading support for Apache Kafka JMX connections resolutions from ZooKeeper");
	}

	@Override
	protected EventSink logger() {
		return LOGGER;
	}

	@Override
	protected String getPath() {
		return "/brokers/ids"; // NON-NLS
	}

	@Override
	public String getPrefix() {
		return PREFIX;
	}

	@Override
	VMParams<JMXServiceURL> nodeToConnection(String path) throws Exception {
		logger().log(OpLevel.DEBUG, "KafkaZKVMResolver.nodeToConnection: accessing node: {0}", path);
		byte[] data = zk().getData(path, watcher, null);
		String dataStr = new String(data);
		logger().log(OpLevel.DEBUG, "KafkaZKVMResolver.nodeToConnection: received data: {0}", dataStr);
		DocumentContext doc = JsonPath.parse(dataStr);
		Object host = doc.read("$.host"); // NON-NLS
		Object port = doc.read("$.jmx_port"); // NON-NLS

		if (port instanceof Integer && (Integer) port != -1) {
			logger().log(OpLevel.DEBUG, "KafkaZKVMResolver.nodeToConnection: found exposed JMX: {0}:{1}", host, port);

			String serviceName = path.substring(path.lastIndexOf("/") + 1);
			String serviceURL = Utils.format(getURLPattern(), host, port);

			return buildURLConnectionParams(serviceName, serviceURL);
		}

		return null;
	}

}
