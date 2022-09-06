/*
 * Copyright 2015-2022 JKOOL, LLC.
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

import java.net.MalformedURLException;

import javax.management.remote.JMXServiceURL;

/**
 * JMX service URL {@link javax.management.remote.JMXServiceURL} and sampling context parameters package.
 *
 * @version $Revision: 1 $
 */
public class JMXURLConnectionParams extends VMParams<JMXServiceURL> {

	/**
	 * Defines asynchronous JMX service connections resolution result.
	 */
	public static final JMXURLConnectionParams ASYNC_CONN = new JMXURLConnectionParams();

	private JMXURLConnectionParams() {
	}

	/**
	 * Constructs a new instance of JMX service connection parameters.
	 *
	 * @param serviceURL
	 *            JMX service connection URL
	 */
	public JMXURLConnectionParams(JMXServiceURL serviceURL) {
		super(serviceURL);
	}

	/**
	 * Constructs a new instance of JMX service connection parameters.
	 *
	 * @param serviceURL
	 *            JMX service connection URL string
	 * @throws MalformedURLException
	 *             if provided string defines malformd JMX service URL
	 */
	public JMXURLConnectionParams(String serviceURL) throws MalformedURLException {
		super(serviceURL == null ? null : new JMXServiceURL(serviceURL));
	}
}
