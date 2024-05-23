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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.URI;
import java.net.URL;
import java.util.*;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.StreamJMXConstants;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;

/**
 * Resolves VM descriptors and sampling context parameters from configuration file.
 *
 * @version $Revision: 2 $
 */
public class FileVMResolver implements VMResolver<String> {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(FileVMResolver.class);

	/**
	 * Constant defining VM descriptor prefix specific for this VM resolver: {@value}.
	 */
	public static final String PREFIX = "file:"; // NON-NLS

	private static final String GENERAL_PROPS_KEY = "*"; // NON-NLS

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
			if (isLineStartingWith(line, COMMENTS)) {
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

		postProcessVMs(allVMs);

		return allVMs;
	}

	private static final String[] COMMENTS = new String[] { ";", "#", "//" }; // NON-NLS
	private static final String[] COMMENTS_AND_STANZA = new String[] { ";", "#", "//", "{", "}" }; // NON-NLS

	private static boolean isLineStartingWith(String line, String[] symbols) {
		return line.isEmpty() || StringUtils.startsWithAny(line, symbols);
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
				.setOption(VMConstants.PROP_AGENT_OPTIONS,
						getByIndex(split, 1, baseParams.getOption(VMConstants.PROP_AGENT_OPTIONS))) //
				.setOption(VMConstants.PROP_VM_USER,
						getByIndex(split, 2, baseParams.getOption(VMConstants.PROP_VM_USER))) //
				.setPass(getByIndex(split, 3, baseParams.getPass())) //
				.setOption(VMConstants.PROP_SOURCE_FQN,
						getByIndex(split, 4, baseParams.getOption(VMConstants.PROP_SOURCE_FQN))) //
				.setOptionsLine(getByIndex(split, 5, baseParams.getOption(VMConstants.PROP_VM_ADDITIONAL_OPTIONS))) //
				.setOption(VMConstants.PROP_VM_HOST,
						getByIndex(split, 6, baseParams.getOption(VMConstants.PROP_VM_HOST))) //
				.setOption(VMConstants.PROP_VM_PORT,
						getByIndex(split, 7, baseParams.getOption(VMConstants.PROP_VM_PORT)));

		return jmxConnectionParams;
	}

	private VMParams<String> readStanza(LineNumberReader reader, VMParams<String> baseParams) throws IOException {
		String line;
		Map<String, Map<String, String>> propsMap = new HashMap<>(5);
		do {
			line = reader.readLine();
			if (line != null) {
				line = line.trim();
				if (isLineStartingWith(line, COMMENTS_AND_STANZA)) {
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
					case VMConstants.PROP_VM_VM:
					case VMConstants.PROP_VM_URL:
						params.setVMRef(pe.getValue());
						break;
					case VMConstants.PROP_VM_HOST:
					case VMConstants.PROP_VM_HOSTS:
						params.setOption(VMConstants.PROP_VM_HOST, pe.getValue());
						break;
					case VMConstants.PROP_VM_PORT:
					case VMConstants.PROP_VM_PORTS:
						params.setOption(VMConstants.PROP_VM_PORT, pe.getValue());
						break;
					case VMConstants.PROP_VM_PASS:
					case VMConstants.PROP_VM_PASSWORD:
						params.setPass(pe.getValue());
						break;
					case VMConstants.PROP_VM_RECONNECT:
						boolean reconnect = Boolean.parseBoolean(pe.getValue());
						params.setReconnectRule(reconnect ? VMParams.RECONNECT : VMParams.DONT_RECONNECT);
						break;
					case VMConstants.PROP_VM_RECONNECT_SEC:
						long cri = Long.parseLong(pe.getValue());
						params.setReconnectInterval(cri);
						break;
					case VMConstants.PROP_VM_USER:
					case VMConstants.PROP_AGENT_OPTIONS:
					case VMConstants.PROP_SOURCE_FQN:
					default:
						params.setOption(pe.getKey().toLowerCase(), pe.getValue());
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

	/**
	 * Performs post-processing of provided VM descriptor parameters list {@code vmList}.
	 * <p>
	 * Now post-processing performs VM descriptor parameters variable expressions evaluation.
	 *
	 * @param vmList
	 *            list of VM descriptor parameters
	 * @return list of post-processed VM descriptor parameters
	 */
	protected List<VMParams<String>> postProcessVMs(List<VMParams<String>> vmList) {
		if (CollectionUtils.isNotEmpty(vmList)) {
			int vmCount = vmList.size();
			for (int i = 0; i < vmCount; i++) {
				VMParams<String> vm = vmList.get(i);

				if (Utils.isVariableExpression(vm.getVMRef())) {
					List<VMParams<String>> vmListVar = fillVMVariables(vm);
					if (CollectionUtils.isNotEmpty(vmListVar)) {
						vmList.addAll(vmListVar);
						vmList.remove(i--);
						vmCount--;
					}
				}
			}
		}

		return vmList;
	}

	/**
	 * Fills in VM descriptor parameters variable expressions with corresponding values.
	 *
	 * @param varVM
	 *            VM descriptor parameters having variable expressions
	 * @return list of VM descriptor parameters having filled in variable expressions, or {@code null} if no defined
	 *         variables could be resolved
	 */
	protected List<VMParams<String>> fillVMVariables(VMParams<String> varVM) {
		List<String> vmVariables = new ArrayList<>();
		Utils.resolveExpressionVariables(vmVariables, varVM.getVMRef());

		LinkedHashMap<String, String[]> varValuesMap = new LinkedHashMap<>(vmVariables.size());

		int maxValuesCount = 0;
		Set<Integer> vLengthSet = new HashSet<>();

		for (String varAttr : vmVariables) {
			String value = varVM.getOption(Utils.getVarName(varAttr));
			if (value != null) {
				String[] values = value.split(StreamJMXConstants.MULTI_VALUE_DELIM);
				varValuesMap.put(varAttr, values);

				maxValuesCount = Math.max(maxValuesCount, values.length);
				vLengthSet.add(values.length);
			}
		}

		if (varValuesMap.isEmpty()) {
			return null;
		}

		int totalValues = 1;

		for (Integer vl : vLengthSet) {
			totalValues *= vl;
		}

		for (Map.Entry<String, String[]> vme : varValuesMap.entrySet()) {
			String[] values = vme.getValue();
			if (values.length < totalValues) {
				String[] varMatrixLine = new String[totalValues];
				int dp = 0;
				do {
					System.arraycopy(values, 0, varMatrixLine, dp, values.length);
					dp += values.length;
				} while (dp < totalValues);

				vme.setValue(varMatrixLine);
			}
		}

		List<VMParams<String>> varVMs = new ArrayList<>(5);

		for (int i = 0; i < totalValues; i++) {
			VMParams<String> vmParams = new VMDescriptorParams(varVM.getVMRef());
			vmParams.setBaseVMParams(varVM);
			for (Map.Entry<String, String[]> vme : varValuesMap.entrySet()) {
				vmParams.setVMRef(vmParams.getVMRef().replace(vme.getKey(), vme.getValue()[i]));
			}
			varVMs.add(vmParams);
		}

		return varVMs;
	}
}
