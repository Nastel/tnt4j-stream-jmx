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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.jkoolcloud.tnt4j.stream.jmx.utils.ConsoleOutputCaptor;

/**
 * Utility class used to manage {@link SamplingAgent} workflow within servlet container.
 * 
 * @version $Revision: 1 $
 */
public class StreamJMXManager {

	public enum StreamJMXProperties {
		JMX_SAMPLER_FACTORY("com.jkoolcloud.tnt4j.stream.jmx.sampler.factory", "com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory", Display.READ_ONLY  , Scope.SYSTEM),
		VALIDATE_TYPES("com.jkoolcloud.tnt4j.stream.jmx.agent.validate.types", "false"                                                 , Display.READ_ONLY  , Scope.SYSTEM),
		TRACE("com.jkoolcloud.tnt4j.stream.jmx.agent.trace"                  , "true"                                                  , Display.READ_ONLY  , Scope.SYSTEM),
		AO("com.jkoolcloud.tnt4j.stream.jmx.agent.options"                   , "*:*!!60000!0"                                          , Display.READ_ONLY  , Scope.LOCAL),
		AO_INCLUDE("com.jkoolcloud.tnt4j.stream.jmx.agent.options.include"   , "*:*"                                                   ,                      Scope.SYNTETIC, Scope.LOCAL),
		AO_EXCLUDE("com.jkoolcloud.tnt4j.stream.jmx.agent.options.exclude"   , ""                                                      ,                      Scope.SYNTETIC, Scope.LOCAL),
		AO_PERIOD("com.jkoolcloud.tnt4j.stream.jmx.agent.options.period"     , String.valueOf(TimeUnit.SECONDS.toMillis(60))  ,                      Scope.SYNTETIC, Scope.LOCAL),
		AO_DELAY("com.jkoolcloud.tnt4j.stream.jmx.agent.options.init.delay"  , "0"                                                     ,                      Scope.SYNTETIC, Scope.LOCAL),
		VM("com.jkoolcloud.tnt4j.stream.jmx.agent.vm"                        , ""                                                      ,                      Scope.LOCAL),
		TNT4J_CONFIG("tnt4j.config"                                          , "tnt4j.properties"                                      ,                      Scope.SYSTEM, Scope.LOCAL),
		TNT4J_CONFIG_CONTS("tnt4j.config.contents"                           , ""                                                      , Display.HIDDEN     , Scope.SYNTETIC),
		USERNAME("com.jkoolcloud.tnt4j.stream.jmx.agent.user"                , ""                                                      , Display.EDITABLE   , Scope.LOCAL),
		PASSWORD("com.jkoolcloud.tnt4j.stream.jmx.agent.pass"                , ""                                                      , Display.EDITABLE_PW, Scope.LOCAL),
		HOST("com.jkoolcloud.tnt4j.stream.jmx.tnt4j.out.host"                , "localhost"                                             , Display.EDITABLE   , Scope.SYSTEM, Scope.LOCAL),
		PORT("com.jkoolcloud.tnt4j.stream.jmx.tnt4j.out.port"                , "6000"                                                  , Display.EDITABLE   , Scope.SYSTEM, Scope.LOCAL);

		String key;
		String defaultValue;
		Display display;
		Scope[] scope;

		private StreamJMXProperties(String key, String defaultValue, Scope... scopes) {
			this(key, defaultValue, Display.EDITABLE, scopes);
		}

		private StreamJMXProperties(String key, String defaultValue, Display display, Scope... scopes) {
			this.key = key;
			this.defaultValue = defaultValue;
			this.display = display;
			this.scope = scopes;
		}

		static StreamJMXProperties[] values(Display... filters) {
			List<StreamJMXProperties> result = new ArrayList<StreamJMXProperties>(StreamJMXProperties.values().length);
			for (Display filter : filters) {
				for (StreamJMXProperties property : StreamJMXProperties.values()) {
					if (property.display.equals(filter)) {
						result.add(property);
					}
				}
			}
			return result.toArray(new StreamJMXProperties[result.size()]);
		}

		public boolean isInScope(Scope scope) {
			return ArrayUtils.contains(this.scope, scope);
		}

	}

	public enum Scope {
		LOCAL, SYNTETIC, SYSTEM
	}

	public enum Display {
		EDITABLE, READ_ONLY, HIDDEN, EDITABLE_PW
	}

	private Properties inAppCfgProperties = new Properties();

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

	String getVM() {
		return inAppCfgProperties.getProperty(StreamJMXProperties.VM.key, StreamJMXProperties.VM.defaultValue);
	}

	String getAO() {
		return inAppCfgProperties.getProperty(StreamJMXProperties.AO.key, StreamJMXProperties.AO.defaultValue);
	}

	void samplerStart() {
		configure();
		sampler = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println("---------------------     Connecting Sampler Agent     ---------------------");
					String vm = getVM();
					String ao = getAO();
					String user = inAppCfgProperties.getProperty(StreamJMXProperties.USERNAME.key,
							StreamJMXProperties.USERNAME.defaultValue);
					String pass = inAppCfgProperties.getProperty(StreamJMXProperties.PASSWORD.key,
							StreamJMXProperties.PASSWORD.defaultValue);
					if (StringUtils.isEmpty(vm)) {
						System.out.println("==> Sampling from local process runner JVM: options=" + ao);
						SamplingAgent.sampleLocalVM(ao, true);
					} else {
						System.out.println("==> Connecting to remote JVM: vm=" + vm + ", options=" + ao + ", user="
								+ user + ", pass=" + pass.replaceAll(".", "*"));
						SamplingAgent.connect(vm, user, pass, ao);
					}
				} catch (Exception e) {
					System.out.println("!!!!!!!!!!!!     Failed to connect Sampler Agent    !!!!!!!!!!!!");
					e.printStackTrace(System.out);
				}
			}
		}, "Stream-JMX_servlet_sampler_thread");
		sampler.start();
	}

	private void configure() {
		for (StreamJMXProperties property : StreamJMXProperties.values()) {
			if (!property.isInScope(Scope.SYNTETIC)) {
				setProperty(property, getParam(property));
			}
		}
		expandAO(getParam(StreamJMXProperties.AO));
	}

	String compileAO() {
		StringBuilder sb = new StringBuilder(64);

		sb.append(inAppCfgProperties.getProperty(StreamJMXProperties.AO_INCLUDE.key));
		sb.append("!");
		sb.append(inAppCfgProperties.getProperty(StreamJMXProperties.AO_EXCLUDE.key));
		sb.append("!");
		sb.append(inAppCfgProperties.getProperty(StreamJMXProperties.AO_PERIOD.key));
		sb.append("!");
		sb.append(inAppCfgProperties.getProperty(StreamJMXProperties.AO_DELAY.key));
		return sb.toString();
	}

	void expandAO(String ao) {
		String[] args = ao.split("!");
		if (args.length > 0) {
			setProperty(StreamJMXProperties.AO_INCLUDE, args[0]);
		}
		if (args.length > 1) {
			setProperty(StreamJMXProperties.AO_EXCLUDE, args[1]);
			setProperty(StreamJMXProperties.AO_PERIOD, args[2]);
		}
		if (args.length > 2) {
			setProperty(StreamJMXProperties.AO_DELAY, args[3]);
		}
	}

	public String getProperty(String key) {
		for (StreamJMXProperties property : StreamJMXProperties.values()) {
			if (property.key.equals(key)) {
				if (ArrayUtils.contains(property.scope, Scope.SYSTEM))
					return System.getProperty(property.key);
				if (ArrayUtils.contains(property.scope, Scope.LOCAL))
					return inAppCfgProperties.getProperty(property.key);
			}
		}
		return null;
	}

	private String getParam(StreamJMXProperties property) {
		String systemPropertyKey = property.key;

		String pValue = null;
		if (pValue == null) {
			pValue = inAppCfgProperties.getProperty(systemPropertyKey);
		}

		if (pValue == null) {
			try {
				pValue = System.getProperty(systemPropertyKey);
			} catch (SecurityException e) {
				System.out.println(e.getMessage() + "\n " + systemPropertyKey);
			}
		}

		if (pValue == null) {
			pValue = String.valueOf(property.defaultValue);
		}

		if (pValue == null) {
			System.out.println("!!!!!!!!!!!!     Failed to get property " + systemPropertyKey + "    !!!!!!!!!!!!");
		}
		return pValue;
	}

	void samplerDestroy() {
		System.out.println("------------------------     Destroying Sampler Agent     ------------------------");
		SamplingAgent.destroy();
		try {
			sampler.join(TimeUnit.SECONDS.toMillis(2));
		} catch (InterruptedException exc) {
		}
	}

	public Properties getCurrentProperties() {
		return inAppCfgProperties;
	}

	public boolean setProperty(StreamJMXProperties property, String value) {
		boolean set = false;
		Object last = null;
		if (property.isInScope(Scope.LOCAL)) {
			last = inAppCfgProperties.setProperty(property.key, value);
		}
		if (property.isInScope(Scope.SYSTEM)) {
			last = System.setProperty(property.key, value);
		}
		if (property.isInScope(Scope.SYNTETIC)) {
			if (inAppCfgProperties.getProperty(StreamJMXProperties.AO_EXCLUDE.key) != null
					&& inAppCfgProperties.getProperty(StreamJMXProperties.AO_INCLUDE.key) != null
					&& inAppCfgProperties.getProperty(StreamJMXProperties.AO_DELAY.key) != null
					&& inAppCfgProperties.getProperty(StreamJMXProperties.AO_PERIOD.key) != null) {
				last = setProperty(StreamJMXProperties.AO, compileAO());
			}
		}
		if (last != null) {
			set = true;
		}
		return set;
	}
}
