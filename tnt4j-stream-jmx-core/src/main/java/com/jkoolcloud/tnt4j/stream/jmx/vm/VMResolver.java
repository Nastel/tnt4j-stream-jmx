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

import java.util.Collection;

/**
 * Base interface for classes capable to resolve JVMs running JMX servers using defined VM descriptor.
 *
 * @param <T>
 *            the type of resolved VM reference: descriptor or URL
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.VMResolverFactory
 */
public interface VMResolver<T> {

	/**
	 * Resolves VM references list for JVMs matching defined VM descriptor.
	 *
	 * @param vmDescrParams
	 *            VM descriptor: display name fragment, pid, registry (e.g. ZooKeeper) URI, etc.
	 * @return collection of resolved JVM references
	 * @throws Exception
	 *             if JVM connection addresses resolution fails
	 */
	Collection<VMParams<T>> getVMConnAddresses(VMParams<String> vmDescrParams) throws Exception;

	/**
	 * Returns handled VM descriptor prefix.
	 *
	 * @return handled VM descriptor prefix.
	 */
	String getPrefix();

	/**
	 * Checks if provided VM descriptor is handled by this resolver.
	 *
	 * @param vmDescr
	 *            VM descriptor: display name fragment, pid, registry (e.g. ZooKeeper) URI, etc.
	 * @return {@code true} if this resolver handles provided VM descriptor, {@code false} - otherwise
	 */
	boolean isHandlingVMDescriptor(String vmDescr);
}
