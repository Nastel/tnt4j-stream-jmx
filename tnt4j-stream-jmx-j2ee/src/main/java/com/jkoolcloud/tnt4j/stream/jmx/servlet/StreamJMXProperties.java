/*
 * Copyright 2015-2023 JKOOL, LLC.
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
import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXProperty.Scope.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.stream.jmx.core.PropertyNameBuilder;
import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;

/**
 * Common Stream-JMX servlet used properties enumeration.
 *
 * @version $Revision: 1 $
 */
public enum StreamJMXProperties implements StreamJMXProperty {
	/**
	 * Agent options sampling {@link javax.management.ObjectName}s include filter.
	 */
	AO_INCLUDE("com.jkoolcloud.tnt4j.stream.jmx.agent.options.include", Sampler.JMX_FILTER_ALL, EDITABLE, INTERIM, LOCAL),
	/**
	 * Agent options sampling {@link javax.management.ObjectName}s exclude filter.
	 */
	AO_EXCLUDE("com.jkoolcloud.tnt4j.stream.jmx.agent.options.exclude", "", EDITABLE, INTERIM, LOCAL),
	/**
	 * Agent options sampling period value.
	 */
	AO_PERIOD("com.jkoolcloud.tnt4j.stream.jmx.agent.options.period", TimeUnit.SECONDS.toMillis(60), EDITABLE, INTERIM,
			LOCAL),
	/**
	 * Agent options initial delay value.
	 */
	AO_DELAY("com.jkoolcloud.tnt4j.stream.jmx.agent.options.init.delay", "0", EDITABLE, INTERIM, LOCAL),
	/**
	 * JVM descriptor to collect JMX samples.
	 */
	VM("com.jkoolcloud.tnt4j.stream.jmx.agent.vm", "", HIDDEN, LOCAL),
	/**
	 * Agent list of user chosen attribute names (may have wildcards {@code '*'} and {@code '?'}) to exclude, pattern:
	 * {@code "attr1,attr2,...,attrN@MBean1_ObjectName;...;attr1,attr2,...,attrN@MBeanN_ObjectName"}
	 */
	USER_EXCLUDED_ATTRIBUTES("com.jkoolcloud.tnt4j.stream.jmx.agent.excludedAttributes", "", EDITABLE, SYSTEM),
	/**
	 * TNT4J properties editor title.
	 */
	TNT4J_CONFIG_CONT("tnt4j.properties", "TNT4J config", FILE_EDITOR, FILE),
	/**
	 * Host name to send sampled data.
	 */
	HOST("com.jkoolcloud.tnt4j.stream.jmx.tnt4j.out.host", "localhost", EDITABLE, SYSTEM, LOCAL),
	/**
	 * .
	 */
	PORT("com.jkoolcloud.tnt4j.stream.jmx.tnt4j.out.port", "6000", EDITABLE, SYSTEM, LOCAL),
	/**
	 * Agent force object name as fact flag.
	 */
	FORCE_OBJ_NAME("com.jkoolcloud.tnt4j.stream.jmx.agent.forceObjectName", "true", EDITABLE, SYSTEM),
	/**
	 * Agent add statistic metadata values as facts flag.
	 */
	ADD_STATISTIC_METADATA("com.jkoolcloud.tnt4j.stream.jmx.agent.addStatisticMetadata", "false", EDITABLE, SYSTEM),
	/**
	 * Agent MBean attribute composite/tabular data tokens delimiter.
	 */
	COMPOSITE_DELIMITER("com.jkoolcloud.tnt4j.stream.jmx.agent.compositeDelimiter", PropertyNameBuilder.DEFAULT_COMPOSITE_DELIMITER, EDITABLE, SYSTEM),
	/**
	 * Agent resolve ObjectName properties flag.
	 */
	USE_OBJ_NAME_PROPERTIES("com.jkoolcloud.tnt4j.stream.jmx.agent.useObjectNameProperties", "true", EDITABLE, SYSTEM),
	/**
	 * Agent exclude attributes on error flag.
	 */
	EXCLUDE_ON_ERROR("com.jkoolcloud.tnt4j.stream.jmx.agent.excludeOnError", "true", EDITABLE, SYSTEM),
	/**
	 * User login mame.
	 */
	USERNAME("com.jkoolcloud.tnt4j.stream.jmx.agent.user", "", HIDDEN, LOCAL),
	/**
	 * User password.
	 */
	PASSWORD("com.jkoolcloud.tnt4j.stream.jmx.agent.pass", "", HIDDEN, LOCAL),

	/**
	 * JMX sampler factory.
	 */
	JMX_SAMPLER_FACTORY("com.jkoolcloud.tnt4j.stream.jmx.sampler.factory", "com.jkoolcloud.tnt4j.stream.jmx.impl.J2EESamplerFactory", READ_ONLY, SYSTEM),
	/**
	 * Agent options.
	 */
	AO("com.jkoolcloud.tnt4j.stream.jmx.agent.options", "*:*!!60000!0", READ_ONLY, LOCAL),
	/**
	 * TNT4J configuration file.
	 */
	TNT4J_CONFIG(TrackerConfigStore.TNT4J_PROPERTIES_KEY, "file:./tnt4j.properties", EDITABLE, SYSTEM, LOCAL),
	/**
	 * LOG4J configuration file.
	 */
	LOG4J_CONFIG("log4j2.configurationFile", "file:./log4j2.xml", EDITABLE, SYSTEM, LOCAL);

	private String key;
	private String defaultValue;
	private Display display;
	private Scope[] scope;

	private StreamJMXProperties(String key, Object defaultValue, Display display, Scope... scopes) {
		this(key, String.valueOf(defaultValue), display, scopes);
	}

	private StreamJMXProperties(String key, String defaultValue, Display display, Scope... scopes) {
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
	 * Finds properties values having display value defined in {@code filters}.
	 *
	 * @param pValues
	 *            properties values array
	 * @param filters
	 *            display values
	 *
	 * @return array of properties having {@code filters} defined display values
	 */
	public static StreamJMXProperty[] values(StreamJMXProperty[] pValues, Display... filters) {
		List<StreamJMXProperty> result = new ArrayList<>(pValues.length);
		for (Display filter : filters) {
			for (StreamJMXProperty property : pValues) {
				if (property.display().equals(filter)) {
					result.add(property);
				}
			}
		}
		return result.toArray(new StreamJMXProperty[result.size()]);
	}

	/**
	 * Concatenates all properties values into single array from provided enumeration classes.
	 *
	 * @param enumClasses
	 *            properties values enumeration classes
	 *
	 * @return concatenated properties values array
	 */
	@SafeVarargs
	public static StreamJMXProperty[] allValues(Class<? extends StreamJMXProperty>... enumClasses) {
		Map<String, StreamJMXProperty> result = new LinkedHashMap<>();
		for (Class<? extends StreamJMXProperty> enumClass : enumClasses) {
			for (StreamJMXProperty sp : enumClass.getEnumConstants()) {
				result.put(sp.key(), sp);
			}
		}
		return result.values().toArray(new StreamJMXProperty[result.size()]);
	}

	/**
	 * Finds name {@code propName} identified property within {@code pValues} array content.
	 *
	 * @param pValues
	 *            properties values array
	 * @param propName
	 *            property name to find
	 *
	 * @return name {@code propName} identified property found within {@code pValues} array, or {@code null} if there is
	 *         no property with that name within array content
	 */
	public static StreamJMXProperty value(StreamJMXProperty[] pValues, String propName) {
		for (StreamJMXProperty property : pValues) {
			if (property.key().equals(propName)) {
				return property;
			}
		}

		return null;
	}

	/**
	 * Removes {@code propNames} identified properties from {@code pValues} array.
	 *
	 * @param pValues
	 *            properties values array
	 * @param propNames
	 *            property names to remove
	 *
	 * @return properties values array without name {@code propName} identified property
	 */
	public static StreamJMXProperty[] remove(StreamJMXProperty[] pValues, String... propNames) {
		if (ArrayUtils.isEmpty(propNames)) {
			return pValues;
		}

		List<StreamJMXProperty> values = new ArrayList<>(pValues.length);
		for (StreamJMXProperty property : pValues) {
			if (!StringUtils.equalsAny(property.key(), propNames)) {
				values.add(property);
			}
		}

		return values.toArray(new StreamJMXProperty[values.size()]);
	}
}
