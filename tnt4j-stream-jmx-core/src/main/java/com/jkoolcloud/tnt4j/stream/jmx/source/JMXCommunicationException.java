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

package com.jkoolcloud.tnt4j.stream.jmx.source;

/**
 * This wrapper exception is thrown when communication with JMX server gets failed while resolving values for
 * {@link com.jkoolcloud.tnt4j.source.Source} definition.
 * <p>
 * This class is needed to properly handle {@link com.jkoolcloud.tnt4j.stream.jmx.source.JMXSourceFactoryImpl} occurred
 * I/O exceptions, since {@link com.jkoolcloud.tnt4j.source.SourceFactory} methods originally does not throw exceptions.
 * 
 * @version $Revision: 1 $
 */
public class JMXCommunicationException extends RuntimeException {
	private static final long serialVersionUID = 6063017017807258485L;

	/**
	 * Create exception with a given cause exception.
	 * 
	 * @param cause
	 *            the cause of communication failure
	 */
	public JMXCommunicationException(Throwable cause) {
		super(cause);
	}
}
