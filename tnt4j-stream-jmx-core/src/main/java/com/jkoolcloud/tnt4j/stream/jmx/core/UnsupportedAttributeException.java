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
package com.jkoolcloud.tnt4j.stream.jmx.core;

import javax.management.MBeanAttributeInfo;

/**
 * <p>
 * This exception is thrown when attribute type is not supported by the underlying sampler.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 */
public class UnsupportedAttributeException extends Exception {
	private static final long serialVersionUID = -7960293489472854680L;

	private Object value;
	private MBeanAttributeInfo info;

	/**
	 * Create exception with a given message, attribute info and its value
	 * 
	 * @param msg exception message
	 * @param mbAttrInfo MBean attribute info
	 * @param value MBean attribute value
	 */
	public UnsupportedAttributeException(String msg, MBeanAttributeInfo mbAttrInfo, Object value) {
		super(msg);
		this.value = value;
		this.info = mbAttrInfo;
	}

	/**
	 * Obtain MBean attribute info associated with this exception
	 * 
	 * @return value MBean attribute info associated with this exception
	 */
	public MBeanAttributeInfo getAttrInfo() {
		return info;
	}

	/**
	 * Obtain MBean attribute value associated with this exception
	 * 
	 * @return value MBean attribute value associated with this exception
	 */
	public Object getValue() {
		return value;
	}
}
