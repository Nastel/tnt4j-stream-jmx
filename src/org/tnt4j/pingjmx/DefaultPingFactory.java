/*
 * Copyright 2015 Nastel Technologies, Inc.
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
package org.tnt4j.pingjmx;

/**
 * <p> 
 * This class provides a generic way to get a default <code>PingFactory</code>
 * instance.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see PingFactory
 */
public class DefaultPingFactory {
	public static final String DEFAULT_PING_FACTORY = "org.tnt4j.pingjmx.PlatformPingFactory";
	private static PingFactory defaultFactory;
	
	static {
		String factoryClassName = System.getProperty("org.tnt4j.ping.factory", DEFAULT_PING_FACTORY);
		try {
			Class<?> factoryClass = Class.forName(factoryClassName);
			defaultFactory = (PingFactory) factoryClass.newInstance();
		} catch (Throwable ex) {
			defaultFactory = new PlatformPingFactory();			
			ex.printStackTrace();
		}
	}
	
	private DefaultPingFactory() {}
	
	/**
	 * Obtain a default instance of <code>PingFactory</code>
	 *
	 * @return default ping factory instance
	 */
	public static PingFactory getInstance() {
		return defaultFactory;
	}
}
