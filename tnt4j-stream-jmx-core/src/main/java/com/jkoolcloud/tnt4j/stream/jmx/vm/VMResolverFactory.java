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

package com.jkoolcloud.tnt4j.stream.jmx.vm;

import java.util.List;

import javax.management.remote.JMXServiceURL;

/**
 * Base interface for classes managing JMV JMX connections resolution from provided VM descriptors.
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.VMResolver
 */
public interface VMResolverFactory {

	/**
	 * Returns list of resolved JMX service connections using available VM resolvers.
	 *
	 * @param vmDescrParams
	 *            VM descriptor: display name fragment, pid, registry (e.g. ZooKeeper) URI, etc.
	 * @return list of resoled JVM JMX service URLs
	 * @throws Exception
	 *             if JVM JMX service URLs resolution fails
	 *
	 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.VMResolver#getVMConnAddresses(VMParams)
	 */
	List<VMParams<JMXServiceURL>> getJmxServiceURLs(VMParams<String> vmDescrParams) throws Exception;

	/**
	 * Shuts down factory. May reset or clean internal states, storage and opened resources.
	 */
	void shutdown();
}
