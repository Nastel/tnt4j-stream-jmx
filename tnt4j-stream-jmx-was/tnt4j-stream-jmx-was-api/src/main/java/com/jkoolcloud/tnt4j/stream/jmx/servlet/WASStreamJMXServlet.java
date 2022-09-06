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

package com.jkoolcloud.tnt4j.stream.jmx.servlet;

import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXProperty.Display.*;
import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXProperty.Scope.LOCAL;
import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXProperty.Scope.SYSTEM;

import java.lang.reflect.Method;

import org.apache.commons.lang3.ArrayUtils;

import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.WASSecurityHelper;

/**
 * Stream-JMX servlet implementation for IBM WebSphere Application Server.
 *
 * @version $Revision: 1 $
 *
 * @see StreamJMXProperties
 */
public class WASStreamJMXServlet extends StreamJMXServlet {
	private static final long serialVersionUID = -8291650473147748942L;
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(WASStreamJMXServlet.class);

	/**
	 * WAS specific properties values enumeration.
	 */
	public enum WASStreamJMXProperties implements StreamJMXProperty {
		/**
		 * WAS subject user mame.
		 */
		USERNAME("com.jkoolcloud.tnt4j.stream.jmx.agent.user", "", EDITABLE, LOCAL),
		/**
		 * WAS subject password.
		 */
		PASSWORD("com.jkoolcloud.tnt4j.stream.jmx.agent.pass", "", EDITABLE, LOCAL),
		/**
		 * WAS server name.
		 */
		SERVER_NAME("was.server.node.name", getServerName(), EDITABLE, SYSTEM, LOCAL),

		/**
		 * JMX sampler factory used to collect WAS JMX samples.
		 */
		JMX_SAMPLER_FACTORY("com.jkoolcloud.tnt4j.stream.jmx.sampler.factory", "com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory", READ_ONLY, SYSTEM),
		/**
		 * TNT4J configuration file used to stream WAS JMX samples.
		 */
		TNT4J_CONFIG(TrackerConfigStore.TNT4J_PROPERTIES_KEY, "tnt4j_was.properties", EDITABLE, SYSTEM, LOCAL);
		/**
		 * LOG4J configuration file used by WAS JMX sampler.
		 */
		// LOG4J_CONFIG("log4j2.configurationFile", "log4j2_was.xml", HIDDEN, SYSTEM, LOCAL),
		/**
		 * JUL configuration file used by WAS JMX sampler.
		 */
		// LOGGER_CONFIG("jul.configuration", "logging_was.properties", HIDDEN, SYSTEM, LOCAL);

		private String key;
		private String defaultValue;
		private Display display;
		private Scope[] scope;

		private WASStreamJMXProperties(String key, Object defaultValue, Display display, Scope... scopes) {
			this(key, String.valueOf(defaultValue), display, scopes);
		}

		private WASStreamJMXProperties(String key, String defaultValue, Display display, Scope... scopes) {
			this.key = key;
			this.defaultValue = defaultValue;
			this.display = display;
			this.scope = scopes;
		}

		@Override
		public String key() {
			return key;
		}

		@Override
		public String defaultValue() {
			return defaultValue;
		}

		@Override
		public Display display() {
			return display;
		}

		@Override
		public boolean isInScope(Scope scope) {
			return ArrayUtils.contains(this.scope, scope);
		}

		/**
		 * Resolves WAS server name.
		 *
		 * @return resolved WAS server name
		 */
		public static String getServerName() {
			try {
				return com.ibm.websphere.runtime.ServerName.getFullName();
			} catch (Throwable e) {
				return "UnresolvedNode/UnresolvedServer";
			}
		}
	}

	@Override
	protected EventSink logger() {
		return LOGGER;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected StreamJMXProperty[] initProperties() {
		StreamJMXProperty[] allProps = StreamJMXProperties.allValues(StreamJMXProperties.class,
				WASStreamJMXProperties.class);
		allProps = StreamJMXProperties.remove(allProps, StreamJMXProperties.LOG4J_CONFIG.key());

		return allProps;
	}

	@Override
	protected void initSubject() {
		super.initSubject();

		String user = getProperty(WASStreamJMXProperties.USERNAME);
		String pass = getProperty(WASStreamJMXProperties.PASSWORD);

		WASSecurityHelper.acquireSubject(user, pass);
	}

	@Override
	protected void postDestroy() {
		Exception last = null;

		for (StreamJMXProperty prop : servletProperties) {
			if (prop.display() == HIDDEN || prop.display() == FILE_EDITOR) {
				continue;
			}

			String propertyValue = getProperty(prop.key(), null);

			try {
				logger().log(OpLevel.DEBUG, "==> Saving parameter ''{0}'' to servlet context...", prop.key());
				Method method = getServletConfig().getClass().getMethod("setInitParameter", String.class, String.class);

				method.invoke(getServletConfig(), prop.key(), propertyValue);
			} catch (Exception e) {
				logger().log(OpLevel.DEBUG, "!!!!   Save failed {0} {1}   !!!!", e.getClass().getName(),
						Utils.getExceptionMessages(e));
				last = e;
			}
		}

		if (last != null && !(last instanceof IllegalStateException)) {
			logger().log(OpLevel.WARNING, "Last exception caught: ", last.getCause());
		}
	}

	@Override
	protected void envCheck() {
		super.envCheck();

		logger().log(OpLevel.DEBUG, "==> IBM.ORB: {0}", getClassLocation("com.ibm.CORBA.MinorCodes"));
		logger().log(OpLevel.DEBUG, "==> IBM.EJB.THIN.CLIENT: {0}", getClassLocation("com.ibm.tx.TranConstants"));
		logger().log(OpLevel.DEBUG, "==> IBM.ADMIN.CLIENT: {0}", getClassLocation("com.ibm.ws.pmi.j2ee.StatisticImpl"));
	}

}
