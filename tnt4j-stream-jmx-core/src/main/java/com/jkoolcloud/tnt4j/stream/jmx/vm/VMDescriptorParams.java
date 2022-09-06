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

/**
 * JMX runner JVM descriptor and sampling context parameters package.
 *
 * @version $Revision: 1 $
 */
public class VMDescriptorParams extends VMParams<String> {

	/**
	 * Constructs a new instance of VM descriptor parameters.
	 */
	VMDescriptorParams() {
	}

	/**
	 * Constructs a new instance of VM descriptor parameters.
	 *
	 * @param vmDescriptor
	 *            JMX service runner VM descriptor string
	 */
	public VMDescriptorParams(String vmDescriptor) {
		super(vmDescriptor);
	}
}
