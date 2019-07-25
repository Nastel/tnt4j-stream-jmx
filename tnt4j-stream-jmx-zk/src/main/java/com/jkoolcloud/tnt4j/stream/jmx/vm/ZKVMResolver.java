/*
 * Copyright 2015-2019 JKOOL, LLC.
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

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

import javax.management.remote.JMXServiceURL;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;

/**
 * Base class for ZooKeeper orchestrated VMs JMX connections resolvers.
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.KafkaZKVMResolver
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.SolrZKVMResolver
 */
public abstract class ZKVMResolver implements VMResolver<JMXServiceURL>, Closeable {
	/**
	 * Constant for default ZooKeeper connection host - {@value}.
	 */
	static final String DEFAULT_CONN_HOST = "localhost"; // NON-NLS
	/**
	 * Constant for default ZooKeeper connection timeout - {@value}ms.
	 */
	static final int DEFAULT_CONN_TIMEOUT = 5000;

	/**
	 * Constant for name of TNT4J-Streams ZooKeeper configuration property {@value}.
	 */
	static final String PROP_ZK_CONN = "zk.conn"; // NON-NLS
	/**
	 * Constant for name of TNT4J-Streams ZooKeeper configuration property {@value}.
	 */
	static final String PROP_ZK_CONN_TIMEOUT = "zk.conn.timeout"; // NON-NLS

	// create static instance for ZKConnection class.
	private ZKConnection conn;

	private Properties zkConfigProperties = new Properties();

	/**
	 * Default VM URL pattern: {@value}.
	 */
	static final String DEFAULT_RMI_URL = "service:jmx:rmi:///jndi/rmi://{0}:{1,number,######}/jmxrmi"; // NON-NLS

	/**
	 * Defines empty VM descriptor properties token: {@value}.
	 */
	protected static final String EMPTY = "."; // NON-NLS

	private static HashMap<String, VMParams<JMXServiceURL>> connectionRegistry = new HashMap<>();

	private Map<String, String> additionalOptions = null;
	private VMParams<String> baseVMDescrParams;

	/**
	 * Constructs new instance of ZooKeeper VM resolver.
	 */
	protected ZKVMResolver() {
	}

	/**
	 * Returns logger instance for this VM resolver.
	 *
	 * @return logger instance
	 */
	protected abstract EventSink logger();

	/**
	 * Opens ZK ensemble connection using configuration properties defined values.
	 *
	 * @param zkConfProps
	 *            streams ZooKeeper configuration properties
	 * @return instance of opened ZK connection
	 * @throws IOException
	 *             if I/O exception occurs while initializing ZooKeeper connection
	 * @throws InterruptedException
	 *             if the current thread is interrupted while waiting
	 */
	public ZKConnection openConnection(Properties zkConfProps) throws IOException, InterruptedException {
		logger().log(OpLevel.DEBUG, "ZKVMResolver.openConnection: connecting to ZK ensemble...");

		if (Utils.isEmpty(zkConfProps)) {
			logger().log(OpLevel.DEBUG,
					"ZKVMResolver.openConnection: connection configuration properties are empty. Will use default values...");
		}

		conn = new ZKConnection();

		String zkConnStr = zkConfProps.getProperty(PROP_ZK_CONN, DEFAULT_CONN_HOST);
		int timeout = Utils.getInt(PROP_ZK_CONN_TIMEOUT, zkConfProps, DEFAULT_CONN_TIMEOUT);

		conn.connect(zkConnStr, timeout); // NON-NLS

		logger().log(OpLevel.DEBUG, "ZKVMResolver.openConnection: established ZK connection: {0}", zkConnStr);

		return conn;
	}

	/**
	 * Returns {@link ZooKeeper} instance connection was established to. If no connection is established, then opens new
	 * connection using last read streams ZK configuration properties.
	 *
	 * @return zookeeper instance
	 * @throws java.io.IOException
	 *             if I/O exception occurs while initializing ZooKeeper connection
	 * @throws InterruptedException
	 *             if the current thread is interrupted while waiting
	 */
	public ZooKeeper zk() throws IOException, InterruptedException {
		if (conn == null || !conn.isConnected()) {
			openConnection(zkConfigProperties);
		}

		return conn.zk();
	}

	/**
	 * Closes ZK ensemble connection.
	 */
	@Override
	public void close() {
		Utils.close(conn);
	}

	@Override
	public boolean isHandlingVMDescriptor(String vmDescr) {
		return vmDescr != null && vmDescr.toLowerCase().startsWith(getPrefix());
	}

	/**
	 * ZooKeeper node events watcher used by this VM resolver.
	 */
	Watcher watcher = new Watcher() {
		@Override
		public void process(WatchedEvent watchedEvent) {
			switch (watchedEvent.getType()) {
			case NodeCreated:
			case NodeDeleted:
			case NodeDataChanged:
				handleChange(watchedEvent.getPath().substring(0, watchedEvent.getPath().lastIndexOf("/"))); // NON-NLS
				break;
			case NodeChildrenChanged:
				handleChange(watchedEvent.getPath());
				break;
			case None:
			case DataWatchRemoved:
			case ChildWatchRemoved:
			default:
				break;
			}

		}
	};

	private VMParams<JMXServiceURL> initiateNewSample(String path) {
		try {
			return nodeToConnection(path);
		} catch (Throwable e) {
			logger().log(OpLevel.ERROR,
					"ZKVMResolver.initiateNewSample: failed to initiate JVM connection credentials for path ''{0}''",
					path, e);
		}

		return null;
	}

	/**
	 * Returns ZooKeeper nodes path monitored by this resolver.
	 *
	 * @return ZooKeeper nodes path monitored by this resolver
	 */
	protected abstract String getPath();

	/**
	 * Builds JMX service URL from {@code path} defined node data.
	 *
	 * @param path
	 *            ZooKeeper nodes path
	 * @return JMX service connection URL built from node data
	 * @throws java.lang.Exception
	 *             if fails to build JMX service URL from node data
	 */
	abstract VMParams<JMXServiceURL> nodeToConnection(String path) throws Exception;

	private void handleChange(String path) {
		List<VMParams<JMXServiceURL>> conns = collectVMs(path);

		try {
			SamplingAgent.connectAll(conns, null);
		} catch (Exception exc) {
			logger().log(OpLevel.ERROR, "ZKVMResolver.handleChange: could''t connect to ZooKeeper changed VMs", exc);
		}
	}

	private List<VMParams<JMXServiceURL>> collectVMs(String path) {
		List<VMParams<JMXServiceURL>> vmConnections = new ArrayList<>();
		try {
			List<String> dataNodes = zk().getChildren(path, watcher, null);
			logger().log(OpLevel.DEBUG, "ZKVMResolver.handleChange: found nodes: {0}", dataNodes);
			logger().log(OpLevel.DEBUG, "ZKVMResolver.handleChange: already watched nodes: {0}",
					connectionRegistry.keySet());

			Collection<String> added = CollectionUtils.removeAll(dataNodes, connectionRegistry.keySet());
			Collection<String> removed = CollectionUtils.removeAll(connectionRegistry.keySet(), dataNodes);

			for (String node : added) {
				logger().log(OpLevel.DEBUG, "ZKVMResolver.collectVMs: initiating monitoring of VM for node ''{0}''",
						node);
				VMParams<JMXServiceURL> conn = initiateNewSample(path + "/" + node); // NON-NLS
				if (conn != null) {
					vmConnections.add(conn);
				}
			}
			for (String node : removed) {
				logger().log(OpLevel.DEBUG, "ZKVMResolver.collectVMs: stopping monitoring of VM for node ''{0}''",
						node);
				connectionRegistry.remove(node).setReconnectRule(VMParams.DONT_RECONNECT);
			}
		} catch (Throwable e) {
			logger().log(OpLevel.ERROR, "ZKVMResolver.collectVMs: failed to collect VMs from path ''{0}''", path, e);
		}

		return vmConnections;
	}

	@Override
	public List<VMParams<JMXServiceURL>> getVMConnAddresses(VMParams<String> vmDescrParams) throws Exception {
		this.baseVMDescrParams = vmDescrParams;
		zkConfigProperties.put(PROP_ZK_CONN, vmDescrParams.getVMRef().substring(getPrefix().length() + 2));

		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				handleChange(getPath());
			}
		});
		t.start();
		return Collections.singletonList(JMXURLConnectionParams.ASYNC_CONN);
	}

	/**
	 * Returns base VM descriptor parameters package.
	 *
	 * @return base VM descriptor parameters package
	 */
	protected VMParams<?> getBaseVMDescrParams() {
		return baseVMDescrParams;
	}

	/**
	 * Gets VM descriptor additional option value.
	 *
	 * @param optKey
	 *            additional option key
	 *
	 * @return additional option value
	 */
	protected String getAdditionalOption(String optKey) {
		if (additionalOptions == null) {
			if (baseVMDescrParams != null) {
				String otherOptions = baseVMDescrParams.getAdditionalOptions();
				if (otherOptions != null) {
					additionalOptions = new HashMap<>();

					String[] opts = otherOptions.split(","); // NON-NLS
					for (String opt : opts) {
						String[] optionTokens = opt.split("="); // NON-NLS

						if (optionTokens.length == 2) {
							additionalOptions.put(optionTokens[0], optionTokens[1]);
						} else {
							logger().log(OpLevel.WARNING,
									"ZKVMResolver.getAdditionalOption: invalid property definition ''{0}''", opt);
						}
					}
				}
			}
		}

		return additionalOptions.get(optKey);
	}

	/**
	 * Builds JMX service connection URL parameters package.
	 *
	 * @param serviceName
	 *            service name
	 * @param serviceURL
	 *            JMX service URL string
	 * @return JMX service connection URL parameters package
	 * @throws IOException
	 *             if service URL string is malformed
	 */
	protected VMParams<JMXServiceURL> buildURLConnectionParams(String serviceName, String serviceURL)
			throws IOException {
		String additionalSourceFQN = baseVMDescrParams.getAdditionalSourceFQN();
		if (com.jkoolcloud.tnt4j.utils.Utils.isEmpty(additionalSourceFQN) || EMPTY.equals(additionalSourceFQN)) {
			additionalSourceFQN = "SERVICE=" + serviceName; // NON-NLS
		}

		VMParams<JMXServiceURL> connectionParam = new JMXURLConnectionParams(serviceURL)
				.setBaseVMParams(baseVMDescrParams).setAdditionalSourceFQN(additionalSourceFQN);

		connectionRegistry.put(serviceName, connectionParam);
		logger().log(OpLevel.INFO, "ZKVMResolver.buildURLConnectionParams: Made remote JVM JMX connection URL: {0}",
				serviceURL);

		return connectionParam;
	}

	/**
	 * Builds VM descriptor corresponding resolver.
	 *
	 * @param vmDescr
	 *            VM descriptor
	 * @return VM resolver instance
	 */
	static ZKVMResolver build(String vmDescr) {
		if (!Utils.isEmpty(vmDescr)) {
			if (vmDescr.toLowerCase().startsWith(KafkaZKVMResolver.PREFIX)) {
				return new KafkaZKVMResolver();
			} else if (vmDescr.toLowerCase().startsWith(SolrZKVMResolver.PREFIX)) {
				return new SolrZKVMResolver();
			}
		}

		return null;
	}
}
