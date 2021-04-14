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

package com.jkoolcloud.tnt4j.stream.jmx.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

/**
 * General utility methods used by TNT4J-Stream-JMX.
 *
 * @version $Revision: 1 $
 */
public class Utils extends com.jkoolcloud.tnt4j.utils.Utils {
	/**
	 * Definition of MBeans provided property {@value}.
	 */
	public static final String OBJ_NAME_PROP = "ObjectName";
	/**
	 * Definition of stream-jmx internally used snapshot property to have referenced MBean
	 * {@link javax.management.ObjectName} value.
	 */
	public static final String OBJ_NAME_OBJ_PROP = "ObjectNameObjProp";

	/**
	 * Loads properties from resource with given name.
	 *
	 * @param name
	 *            the resource name
	 * @return properties loaded from resource
	 * @throws java.io.IOException
	 *             if an error occurred when reading properties file
	 *
	 * @see java.lang.ClassLoader#getResourceAsStream(String)
	 * @see java.util.Properties#load(java.io.InputStream)
	 */
	public static Properties loadPropertiesResource(String name) throws IOException {
		Properties rProps = new Properties();

		ClassLoader loader = getClassLoader();
		InputStream ins = loader.getResourceAsStream(name);

		try {
			rProps.load(ins);
		} finally {
			close(ins);
		}

		return rProps;
	}

	/**
	 * Loads properties from all resource with given name.
	 *
	 * @param name
	 *            the resource name
	 * @return properties loaded from all found resources
	 * @throws java.io.IOException
	 *             if an error occurred when reading properties file
	 *
	 * @see java.lang.ClassLoader#getResources(String)
	 * @see java.util.Properties#load(java.io.InputStream)
	 */
	public static Properties loadPropertiesResources(String name) throws IOException {
		Properties rProps = new Properties();

		ClassLoader loader = getClassLoader();
		Enumeration<URL> rEnum = loader.getResources(name);

		while (rEnum.hasMoreElements()) {
			InputStream ins = rEnum.nextElement().openStream();

			try {
				rProps.load(ins);
			} finally {
				close(ins);
			}
		}

		return rProps;
	}

	/**
	 * Resolves value from {@link System} defined property or {@code dProps} properties. First is checked in
	 * {@link System} properties and if not found, then value is taken from {@code dProps} instance. If none is set in
	 * both properties instances - {@code null} is returned.
	 *
	 * @param dProps
	 *            default properties values
	 * @param propName
	 *            property name
	 * @return system or default properties resolved value
	 *
	 * @see #getConfProperty(Properties, String, String)
	 */
	public static String getConfProperty(Properties dProps, String propName) {
		return getConfProperty(dProps, propName, null);
	}

	/**
	 * Resolves value from {@link System} defined property or {@code dProps} properties. First is checked in
	 * {@link System} properties and if not found, then value is taken from {@code dProps} instance. If none is set in
	 * both properties instances - {@code defValue} is returned.
	 *
	 * @param dProps
	 *            default values properties instance
	 * @param propName
	 *            property name
	 * @param defValue
	 *            default value if none is set in properties
	 * @return system or default properties resolved value
	 *
	 * @see System#getProperty(String)
	 */
	public static String getConfProperty(Properties dProps, String propName, String defValue) {
		String pValue = System.getProperty(propName);

		if (StringUtils.isEmpty(pValue) && dProps != null) {
			pValue = dProps.getProperty(propName, defValue);
		}

		return pValue;
	}

	/**
	 * Copies property <tt>sKey</tt> value from <tt>sProperties</tt> to <tt>tProperties</tt> using key <tt>tKey</tt>.
	 * 
	 * @param sKey
	 *            source property key
	 * @param sProperties
	 *            source properties map
	 * @param tKey
	 *            target property key
	 * @param tProperties
	 *            source properties map
	 */
	public static void copyProperty(String sKey, Map<?, ?> sProperties, String tKey, Map<String, Object> tProperties) {
		Object sPropValue = sProperties.get(sKey);
		if (sPropValue != null) {
			tProperties.put(tKey, sPropValue);
		}
	}

	/**
	 * Copies property <tt>sKey</tt> value from <tt>sProperties</tt> to <tt>tProperties</tt> using key <tt>tKey</tt>. If
	 * source property does not exist, default value <tt>defValue</tt> is set.
	 *
	 * @param sKey
	 *            source property key
	 * @param sProperties
	 *            source properties map
	 * @param tKey
	 *            target property key
	 * @param tProperties
	 *            source properties map
	 * @param defValue
	 *            default property value to set when source property is not defined
	 */
	public static void copyProperty(String sKey, Map<?, ?> sProperties, String tKey, Map<String, Object> tProperties,
			Object defValue) {
		Object sPropValue = sProperties.get(sKey);
		tProperties.put(tKey, sPropValue == null ? defValue : sPropValue);
	}

	/**
	 * Returns top cause of exception.
	 *
	 * @param exc
	 *            exception instance to get top cause
	 * @return top cause of exception
	 */
	public static Throwable getTopCause(Throwable exc) {
		if (exc == null) {
			return exc;
		}

		while (exc.getCause() != null) {
			exc = exc.getCause();
		}

		return exc;
	}

	/**
	 * Generates a string representing exception and all exception root cause messages.
	 *
	 * @param ex
	 *            exception to process
	 * @return exception messages string
	 */
	public static String getAllExceptionMessages(Throwable ex) {
		StringBuilder msgsSb = new StringBuilder(ex == null ? "" : ex.toString());
		if (ex != null) {
			while (ex.getCause() != null) {
				ex = ex.getCause();
				msgsSb.append("\n\t caused by: ").append(ex.toString());
			}
		}

		return msgsSb.toString();
	}

	/**
	 * Generates a string representing exception message.
	 *
	 * @param ex
	 *            exception to process
	 * @return exception message string
	 */
	public static String getExceptionMessage(Throwable ex) {
		if (ex == null) {
			return "";
		}

		String msg = ex.getMessage();
		if (StringUtils.isEmpty(msg)) {
			return ex.getClass().getName();
		}

		return msg;
	}

	/**
	 * Checks if provided string contains wildcard characters.
	 *
	 * @param str
	 *            string to check if it contains wildcard characters
	 * @return {@code true} if string contains wildcard characters
	 */
	public static boolean isWildcardString(String str) {
		if (StringUtils.isNotEmpty(str)) {
			return str.contains("*") || str.contains("?"); // NON-NLS
		}
		return false;
	}

	/**
	 * Transforms wildcard mask string to regex ready string.
	 *
	 * @param str
	 *            wildcard string
	 * @return regex ready string
	 */
	public static String wildcardToRegex(String str) {
		return StringUtils.isEmpty(str) ? str : str.replace("?", ".?").replace("*", ".*?"); // NON-NLS
	}

	/**
	 * Checks if string is wildcard mask string and if {@code true} then transforms it to regex ready string.
	 *
	 * @param str
	 *            string to check and transform
	 * @return regex ready string
	 *
	 * @see #wildcardToRegex(String)
	 */
	public static String wildcardToRegex2(String str) {
		return isWildcardString(str) ? wildcardToRegex(str) : str;
	}
}
