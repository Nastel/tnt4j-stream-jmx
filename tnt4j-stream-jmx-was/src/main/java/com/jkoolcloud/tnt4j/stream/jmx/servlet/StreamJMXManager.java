/*
 * Copyright 2014-2017 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx.servlet;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.jkoolcloud.tnt4j.stream.jmx.utils.ConsoleOutputCaptor;

/**
 * Utility class used to manage {@link SamplingAgent} workflow within servlet container.
 * 
 * @version $Revision: 1 $
 */
public class StreamJMXManager {
	static final String TNT4JJMX_PROPERTIES_FILE_NAME = "tnt4jjmx.properties";

	static final String JMX_SAMPLER_FACTORY_KEY = "com.jkoolcloud.tnt4j.stream.jmx.sampler.factory";
	static final String VALIDATE_TYPES_KEY = "com.jkoolcloud.tnt4j.stream.jmx.agent.validate.types";
	static final String TRACE_KEY = "com.jkoolcloud.tnt4j.stream.jmx.agent.trace";
	static final String AO_KEY = "com.jkoolcloud.tnt4j.stream.jmx.agent.options";
	static final String VM_KEY = "com.jkoolcloud.tnt4j.stream.jmx.agent.vm";

	static final String DEFAULT_TNT4J_PROPERTIES = "tnt4j.properties";
	static final String DEFAULT_AO = "*:*!!10000!0";
	static final String DEFAULT_VM = "service:jmx:iiop://localhost:2809/jndi/JMXConnector";
	static final String TNT4J_CONFIG_KEY = "tnt4j.config";

	private String vm = null;
	private String ao = null;

	static ConsoleOutputCaptor console = new ConsoleOutputCaptor();

	private static Thread sampler;

	static {
		console.start();
	}

	private static StreamJMXManager instance;

	private StreamJMXManager() {
	}

	public static StreamJMXManager getInstance() {
		synchronized (StreamJMXManager.class) {
			if (instance == null) {
				instance = new StreamJMXManager();
			}

			return instance;
		}
	}

	void samplerStart() {
		configure();
		sampler = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println(
							"-------------------------------     Connecting Sampler Agent     -------------------------------");
					SamplingAgent.connect(vm, ao);
				} catch (Exception e) {
					System.out.println(
							"!!!!!!!!!!!!!!!!!!!!!!     Failed to connect Sampler Agent    !!!!!!!!!!!!!!!!!!!!!!");
					e.printStackTrace(System.out);
				}
			}
		}, "Stream-JMX_servlet_sampler_thread");
		sampler.start();
	}

	private void configure() {
		Properties persistedProperties = loadPersistedProperties();

		vm = getParam(persistedProperties, VM_KEY, DEFAULT_VM);
		ao = getParam(persistedProperties, AO_KEY, DEFAULT_AO);
	}

	Properties loadPersistedProperties() {
		Properties persistedProperties = new Properties();
		try {
			persistedProperties.load(getClass().getClassLoader().getResourceAsStream(TNT4JJMX_PROPERTIES_FILE_NAME));

			System.out.println("## Persisted properties start: ##");
			for (Object key : persistedProperties.keySet()) {
				final String keyName = key.toString();
				System.out.println(keyName + "=" + persistedProperties.getProperty(keyName));
			}
			System.out.println("## Persisted properties end: ##");
		} catch (Exception e1) {
			System.out.println("!!!!!!!!!!!!!!!!!!!!!!     Failed to load property file    !!!!!!!!!!!!!!!!!!!!!!");
			System.out.println(e1.getMessage());
		}
		return persistedProperties;
	}

	private String getParam(Properties persistedProperties, String systemPropertyKey, String defaultValue) {
		String argument = null;
		try {
			argument = System.getProperty(systemPropertyKey);
		} catch (SecurityException e) {
			System.out.println(e.getMessage() + "\n " + systemPropertyKey);
		}
		if (StringUtils.isEmpty(argument)) {
			try {
				argument = persistedProperties.getProperty(systemPropertyKey);
			} catch (Exception e) {
				System.out.println("No persisted property: " + systemPropertyKey);
			}
		}
		if (StringUtils.isEmpty(argument)) {
			argument = defaultValue;
		}
		if (StringUtils.isEmpty(argument)) {
			System.out.println("!!!!!!!!!!!!!!!!!!!!!!     Failed to set property " + systemPropertyKey
					+ "    !!!!!!!!!!!!!!!!!!!!!!");
		} else {
			System.out.println("Property " + systemPropertyKey + " set to " + argument);
			System.setProperty(systemPropertyKey, argument);
		}
		return argument;
	}

	void samplerDestroy() {
		System.out.println(
				"-------------------------------     Destroying Sampler Agent     -------------------------------");
		SamplingAgent.destroy();
		try {
			sampler.join(TimeUnit.SECONDS.toMillis(2));
		} catch (InterruptedException exc) {
		}
	}
}
