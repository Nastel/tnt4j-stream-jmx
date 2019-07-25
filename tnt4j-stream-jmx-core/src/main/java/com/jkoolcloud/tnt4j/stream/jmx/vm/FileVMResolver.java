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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;

/**
 * Resolves VM descriptors and sampling context parameters from configuration file.
 *
 * @version $Revision: 1 $
 */
public class FileVMResolver implements VMResolver<String> {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(FileVMResolver.class);

	/**
	 * Constant defining VM descriptor prefix specific for this VM resolver: {@value}.
	 */
	public static final String PREFIX = "file:"; // NON-NLS

	/**
	 * Constructs new instance of VM descriptors resolver from file.
	 */
	public FileVMResolver() {
		LOGGER.log(OpLevel.INFO, "Loading support for Kafka JMX connections resolutions from file");
	}

	@Override
	public String getPrefix() {
		return PREFIX;
	}

	@Override
	public boolean isHandlingVMDescriptor(String vmDescr) {
		return vmDescr != null && vmDescr.toLowerCase().startsWith(getPrefix());
	}

	@Override
	public List<VMParams<String>> getVMConnAddresses(VMParams<String> vmDescrParams) throws IOException {
		String vmDescr = vmDescrParams.getVMRef();
		File file;

		if (vmDescr.startsWith(PREFIX)) {
			try {
				file = new File(URI.create(vmDescr));
			} catch (Exception urie) {
				try {
					file = new File(new URL(vmDescr).toURI());
				} catch (Exception urle) {
					file = new File(vmDescr.substring(PREFIX.length()));
				}
			}
		} else {
			file = new File(vmDescr);
		}

		if (!file.exists()) {
			LOGGER.log(OpLevel.CRITICAL,
					"FileVMResolver.getVMConnAddresses: JVM connections configuration file does not exist: {0}",
					vmDescr);
			return null;
		}

		LineNumberReader reader = new LineNumberReader(new FileReader(file));
		List<VMParams<String>> allVMs = new ArrayList<>();

		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.startsWith("#") || line.isEmpty()) {
				continue;
			}
			String[] split = line.split("\\s+");

			// vm
			if (split.length > 6) {
				LOGGER.log(OpLevel.WARNING,
						"FileVMResolver.getVMConnAddresses: JVM connections configuration file line {0} has invalid syntax: {1}",
						reader.getLineNumber(), line);
				continue;
			}

			String vm = getByIndex(split, 0);
			VMParams<String> jmxConnectionParams = new VMDescriptorParams(vm)
					.setAgentOptions(getByIndex(split, 1, vmDescrParams.getAgentOptions()))
					.setUser(getByIndex(split, 2, vmDescrParams.getUser()))
					.setPass(getByIndex(split, 3, vmDescrParams.getPass()))
					.setAdditionalSourceFQN(getByIndex(split, 4, vmDescrParams.getAdditionalSourceFQN()))
					.setAdditionalOptions(getByIndex(split, 5, vmDescrParams.getAdditionalOptions()));

			allVMs.add(jmxConnectionParams);
		}

		return allVMs;
	}

	private static String getByIndex(String[] a, int idx) {
		return a.length > idx ? a[idx] : null;
	}

	private static String getByIndex(String[] a, int idx, String defaultValue) {
		return a.length > idx ? a[idx] : defaultValue;
	}
}
