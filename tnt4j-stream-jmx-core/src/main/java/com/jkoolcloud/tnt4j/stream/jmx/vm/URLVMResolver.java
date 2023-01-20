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

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;

import javax.management.remote.JMXServiceURL;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;

/**
 * Resolves JMX service URLs {@link javax.management.remote.JMXServiceURL} from string representation.
 *
 * @version $Revision: 1 $
 */
public class URLVMResolver implements VMResolver<JMXServiceURL> {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(URLVMResolver.class);

	/**
	 * Constant defining VM descriptor prefix specific for this VM resolver: {@value}.
	 */
	public static final String PREFIX = "service:jmx:"; // NON-NLS

	/**
	 * Constructs new instance of JMX service URL resolver from string.
	 */
	public URLVMResolver() {
		LOGGER.log(OpLevel.INFO, "Loading support for JMX connections resolutions URL string");
	}

	@Override
	public String getPrefix() {
		return PREFIX;
	}

	@Override
	public boolean isHandlingVMDescriptor(String vmDescr) {
		return vmDescr != null && vmDescr.toLowerCase().startsWith(getPrefix());
	}

	/**
	 * Makes single item list having JMX service URL made from provided {@code vmDescr} string.
	 * 
	 * @param vmDescrParams
	 *            connection parameters defining VM as JMX service URL string
	 * @return single item list having JMX connection URL instance
	 */
	@Override
	public List<VMParams<JMXServiceURL>> getVMConnAddresses(VMParams<String> vmDescrParams)
			throws MalformedURLException {
		VMParams<JMXServiceURL> ucp = new JMXURLConnectionParams(vmDescrParams.getVMRef())
				.setBaseVMParams(vmDescrParams);
		LOGGER.log(OpLevel.INFO, "URLVMResolver.getVMConnAddresses: made JMX service URL from address ''{0}''",
				vmDescrParams.getVMRef());

		return Collections.singletonList(ucp);
	}
}
