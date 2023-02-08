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

package com.jkoolcloud.tnt4j.stream.jmx.vm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;

/**
 * General VM resolvers factory referencing basic resolvers.
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.FileVMResolver
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.URLVMResolver
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.JDKToolsVMResolver
 */
public class CoreVMResolverFactory implements VMResolverFactory {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(CoreVMResolverFactory.class);

	private static final JDKToolsVMResolver DEFAULT_VM_RESOLVER = new JDKToolsVMResolver();
	private static final URLVMResolver URL_VM_RESOLVER = new URLVMResolver();
	private static final FileVMResolver FILE_VM_RESOLVER = new FileVMResolver();

	/**
	 * Constructs new instance of core VM resolvers factory.
	 */
	public CoreVMResolverFactory() {
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<JMXURLConnectionParams> getJmxServiceURLs(VMParams vmDescrParams) throws Exception {
		String vmDescr = Utils.toString(vmDescrParams.getVMRef());
		if (StringUtils.isEmpty(vmDescr)) {
			throw new RuntimeException("Java VM descriptor must be not empty!..");
		}

		Collection<VMParams<?>> rvms = resolve(vmDescr, vmDescrParams);
		if (rvms == null) {
			rvms = DEFAULT_VM_RESOLVER.getVMConnAddresses(vmDescrParams);
		}

		if (Utils.isEmpty(rvms)) {
			LOGGER.log(OpLevel.WARNING,
					"CoreVMResolverFactory.getJmxServiceURLs: could not resolve any JVMs from VM descriptor ''{0}''",
					vmDescr);
		}

		List<JMXURLConnectionParams> returnList = new ArrayList<>();
		for (VMParams<?> rvm : rvms) {
			if (rvm instanceof JMXURLConnectionParams) {
				returnList.add((JMXURLConnectionParams) rvm);
			} else if (rvm instanceof VMDescriptorParams) {
				List<JMXURLConnectionParams> drl = getJmxServiceURLs(rvm);
				if (!Utils.isEmpty(drl)) {
					returnList.addAll(drl);
				}
			} else {
				LOGGER.log(OpLevel.WARNING, "CoreVMResolverFactory.getJmxServiceURLs: Unknown VM descriptor: {0}", rvm);
			}
		}

		return returnList;
	}

	/**
	 * Returns list of resoled JVM references matching provided VM descriptor.
	 *
	 * @param vmDescr
	 *            VM descriptor: display name fragment, pid, registry (e.g. ZooKeeper) URI, etc.
	 * @param vmDescrParams
	 *            VM descriptor and context parameters
	 * @return list of resoled JVM references
	 * @throws Exception
	 *             if JVM references resolution fails
	 */
	@SuppressWarnings("unchecked")
	protected List<VMParams<?>> resolve(String vmDescr, VMParams vmDescrParams) throws Exception {
		if (URL_VM_RESOLVER.isHandlingVMDescriptor(vmDescr)) {
			return URL_VM_RESOLVER.getVMConnAddresses(vmDescrParams);
		} else if (FILE_VM_RESOLVER.isHandlingVMDescriptor(vmDescr)) {
			return FILE_VM_RESOLVER.getVMConnAddresses(vmDescrParams);
		}

		return null;
	}

	@Override
	public void shutdown() {
	}
}
