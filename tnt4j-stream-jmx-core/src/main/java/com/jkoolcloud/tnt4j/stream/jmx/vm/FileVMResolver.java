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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

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

	private static final String GENERAL_PROPS_KEY = "*"; // NON-NLS
	private static final String OTHER_OPTIONS_PTREFIX = "other."; // NON-NLS

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
		} else {
			LOGGER.log(OpLevel.INFO,
					"FileVMResolver.getVMConnAddresses: reading JVM connections configuration file: {0}",
					file.getAbsolutePath());
		}

		LineNumberReader reader = new LineNumberReader(new FileReader(file));
		List<VMParams<String>> allVMs = new ArrayList<>();

		String line;
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.startsWith("#") || line.isEmpty()) {
				continue;
			}

			VMParams<String> vmParams;
			if (line.startsWith("{")) { // NON-NLS
				vmParams = readStanza(reader, vmDescrParams);
			} else {
				vmParams = readTokensLine(line, reader, vmDescrParams);
			}

			if (vmParams != null) {
				allVMs.add(vmParams);
			}
		}

		return allVMs;
	}

	private VMParams<String> readTokensLine(String line, LineNumberReader reader, VMParams<String> baseParams) {
		String[] split = line.split("\\s+"); // NON-NLS

		// vm
		if (split.length > 6) {
			LOGGER.log(OpLevel.WARNING,
					"FileVMResolver.readTokensLine: invalid JVM connections configuration line {0} has {2} tokens: {1}",
					reader.getLineNumber(), line, split.length);
			return null;
		}

		String vm = getByIndex(split, 0);
		VMParams<String> jmxConnectionParams = new VMDescriptorParams(vm)
				.setAgentOptions(getByIndex(split, 1, baseParams.getAgentOptions()))
				.setUser(getByIndex(split, 2, baseParams.getUser())).setPass(getByIndex(split, 3, baseParams.getPass()))
				.setAdditionalSourceFQN(getByIndex(split, 4, baseParams.getAdditionalSourceFQN()))
				.setAdditionalOptions(getByIndex(split, 5, baseParams.getAdditionalOptions()));

		return jmxConnectionParams;
	}

	private VMParams<String> readStanza(LineNumberReader reader, VMParams<String> baseParams) throws IOException {
		String line;
		Map<String, Map<String, String>> propsMap = new HashMap<>(5);
		do {
			line = reader.readLine();
			if (line != null) {
				line = line.trim();
				if (line.isEmpty() || StringUtils.startsWithAny(line, ";", "#", "//", "{", "}")) { // NON-NLS
					continue;
				}
				int sepIndex = StringUtils.indexOfAny(line, ":="); // NON-NLS
				if (sepIndex <= 0) {
					LOGGER.log(OpLevel.WARNING,
							"FileVMResolver.readStanza: skipping invalid entry, having no key/value delimiter: {0}'",
							line);
					continue;
				}
				String key = line.substring(0, sepIndex).trim();
				String value = line.substring(sepIndex + 1).trim();

				addPropToMap(propsMap, key, value);
			}
		} while (line != null && !line.startsWith("}"));

		VMParams<String> params = new VMDescriptorParams();
		params.setBaseVMParams(baseParams);

		for (Map.Entry<String, Map<String, String>> pme : propsMap.entrySet()) {
			if (GENERAL_PROPS_KEY.equals(pme.getKey())) {
				// TODO;
			} else {
				Map<String, String> props = pme.getValue();

				for (Map.Entry<String, String> pe : props.entrySet()) {
					switch (pe.getKey().toLowerCase()) {
					case "vm": // NON-NLS
					case "vm.url": // NON-NLS
						params.setVMRef(pe.getValue());
						break;
					case "vm.user": // NON-NLS
						params.setUser(pe.getValue());
						break;
					case "vm.pass": // NON-NLS
					case "vm.password": // NON-NLS
						params.setPass(pe.getValue());
						break;
					case "agent.options":// NON-NLS
						params.setAgentOptions(pe.getValue());
						break;
					case "source.fqn":// NON-NLS
						params.setAdditionalSourceFQN(pe.getValue());
						break;
					case "vm.reconnect": // NON-NLS
						boolean reconnect = Boolean.parseBoolean(pe.getValue());
						params.setReconnectRule(reconnect ? VMParams.RECONNECT : VMParams.DONT_RECONNECT);
						break;
					case "vm.reconnect.sec": // NON-NLS
						long cri = Long.parseLong(pe.getValue());
						params.setReconnectInterval(cri);
						break;
					default:
						String aok = pe.getKey();
						if (aok.startsWith(OTHER_OPTIONS_PTREFIX)) {
							aok = aok.substring(OTHER_OPTIONS_PTREFIX.length());
						}
						params.addAdditionalOption(aok, pe.getValue());
						break;
					}
				}

				break;
			}
		}

		return params;
	}

	private static String getByIndex(String[] a, int idx) {
		return a.length > idx ? a[idx] : null;
	}

	private static String getByIndex(String[] a, int idx, String defaultValue) {
		return a.length > idx ? a[idx] : defaultValue;
	}

	private static void addPropToMap(Map<String, Map<String, String>> propsMap, String key, String value) {
		int gIdx = key.indexOf("."); // NON-NLS
		String gKey = GENERAL_PROPS_KEY;
		String vKey = key;

		if (gIdx > 0) {
			gKey = key.substring(0, gIdx);
			vKey = key.substring(gIdx + 1);
		}

		Map<String, String> props = propsMap.computeIfAbsent(gKey, k -> new HashMap<>());

		props.put(vKey.trim(), value);
	}
}
