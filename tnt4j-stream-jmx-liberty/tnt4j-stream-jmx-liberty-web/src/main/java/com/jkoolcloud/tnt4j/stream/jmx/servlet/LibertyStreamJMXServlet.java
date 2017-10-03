/*
 * Copyright 2015-2017 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx.servlet;

import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXProperty.Display.*;
import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXProperty.Scope.*;

import org.apache.commons.lang3.ArrayUtils;

import com.jkoolcloud.tnt4j.config.TrackerConfigStore;

/**
 * Stream-JMX servlet implementation for IBM WebSphere Liberty server.
 *
 * @version $Revision: 1 $
 *
 * @see StreamJMXProperties
 */
public class LibertyStreamJMXServlet extends StreamJMXServlet {
	private static final long serialVersionUID = -5801839005330837514L;

	enum LibertyStreamJMXProperties implements StreamJMXProperty {
		TNT4J_CONFIG_CONT("tnt4j_liberty.properties"                          , "TNT4J config"                                               , FILE_EDITOR, FILE),
		SERVER_NAME("wlp.server.name"                                         , "UnknownLibertyServer"                                       , EDITABLE   , SYSTEM, LOCAL),

		JMX_SAMPLER_FACTORY("com.jkoolcloud.tnt4j.stream.jmx.sampler.factory" , "com.jkoolcloud.tnt4j.stream.jmx.impl.LibertySamplerFactory" , READ_ONLY  , SYSTEM),
		TNT4J_CONFIG(TrackerConfigStore.TNT4J_PROPERTIES_KEY                  , "file:./tnt4j_liberty.properties"                            , READ_ONLY  , SYSTEM, LOCAL);

		private String key;
		private String defaultValue;
		private Display display;
		private Scope[] scope;

		private LibertyStreamJMXProperties(String key, Object defaultValue, Display display, Scope... scopes) {
			this(key, String.valueOf(defaultValue), display, scopes);
		}

		private LibertyStreamJMXProperties(String key, String defaultValue, Display display, Scope... scopes) {
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
	}

	@Override
	@SuppressWarnings("unchecked")
	protected StreamJMXProperty[] initProperties() {
		StreamJMXProperty[] allProps = StreamJMXProperties.allValues(StreamJMXProperties.class, LibertyStreamJMXProperties.class);
		allProps = StreamJMXProperties.remove(allProps, StreamJMXProperties.TNT4J_CONFIG_CONT.key());

		return allProps;
	}
}
