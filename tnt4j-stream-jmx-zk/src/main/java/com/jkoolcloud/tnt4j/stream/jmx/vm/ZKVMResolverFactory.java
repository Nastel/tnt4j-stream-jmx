/*
 * Copyright 2014-2022 JKOOL, LLC.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * Resolvers factory capable to resolve ZooKeeper orchestrated VMs.
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.ZKVMResolver
 */
public class ZKVMResolverFactory extends CoreVMResolverFactory {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(ZKVMResolverFactory.class);

	private static Map<String, ZKVMResolver> ZK_VM_RESOLVERS = new HashMap<>();

	/**
	 * Constructs new instance of ZooKeeper VM resolvers factory.
	 */
	public ZKVMResolverFactory() {
	}

	@Override
	@SuppressWarnings("unchecked")
	protected List<VMParams<?>> resolve(String vmDescr, VMParams vmDescrParams) throws Exception {
		if (vmDescr != null && vmDescr.toLowerCase().contains("zk:")) { // NON-NLS
			ZKVMResolver vmResolver = ZK_VM_RESOLVERS.get(vmDescr);

			if (vmResolver == null) {
				vmResolver = ZKVMResolver.build(vmDescr);

				if (vmResolver == null) {
					LOGGER.log(OpLevel.WARNING, "ZKVMResolverFactory.resolve: Unknown ZooKeeper VM descriptor ''{0}''",
							vmDescr);
				} else {
					ZK_VM_RESOLVERS.put(vmDescr, vmResolver);
				}
			}

			if (vmResolver != null && vmResolver.isHandlingVMDescriptor(vmDescr)) {
				return vmResolver.getVMConnAddresses(vmDescrParams);
			}
		}

		return super.resolve(vmDescr, vmDescrParams);
	}

	@Override
	public void shutdown() {
		if (!ZK_VM_RESOLVERS.isEmpty()) {

			for (Map.Entry<String, ZKVMResolver> vre : ZK_VM_RESOLVERS.entrySet()) {
				Utils.close(vre.getValue());
			}

			ZK_VM_RESOLVERS.clear();
		}
	}
}
