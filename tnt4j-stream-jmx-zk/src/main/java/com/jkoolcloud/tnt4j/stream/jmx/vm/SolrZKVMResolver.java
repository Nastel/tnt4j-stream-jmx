/*
 * Copyright 2014-2023 JKOOL, LLC.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.remote.JMXServiceURL;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * Resolves ZooKeeper orchestrated "Apache Solr" VMs JMX connections.
 *
 * @version $Revision: 1 $
 */
public class SolrZKVMResolver extends ZKVMResolver {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(SolrZKVMResolver.class);

	private static final String OO_PORT = "port"; // NON-NLS

	/**
	 * Constant defining VM descriptor prefix specific for this VM resolver: {@value}.
	 */
	static final String PREFIX = "solr:zk:"; // NON-NLS;

	/**
	 * Constructs new instance of ZooKeeper orchestrated "Apache Solr" VMs resolver.
	 */
	public SolrZKVMResolver() {
		logger().log(OpLevel.INFO, "Loading support for Apache Solr JMX connections resolutions from ZooKeeper");
	}

	@Override
	protected EventSink logger() {
		return LOGGER;
	}

	@Override
	protected String getPath() {
		return "/live_nodes"; // NON-NLS
	}

	@Override
	public String getPrefix() {
		return PREFIX;
	}

	@Override
	VMParams<JMXServiceURL> nodeToConnection(String path) throws Exception {
		Pattern compile = Pattern.compile(Matcher.quoteReplacement(getPath()) + "/" + "(?<host>.*):(?<port>\\d*)_solr"); // NON-NLS
		Matcher matcher = compile.matcher(path);

		if (matcher.matches()) {
			String host = matcher.group("host"); // NON-NLS
			String portProp = getAdditionalOption(OO_PORT);
			if (StringUtils.isEmpty(portProp)) {
				throw new RuntimeException("Apache Solr JMX port number is not defined");
			}

			Integer port = Integer.parseInt(portProp);
			String serviceURL = Utils.format(getURLPattern(), host, port);

			return buildURLConnectionParams(path, serviceURL);
		}

		return null;
	}

}
