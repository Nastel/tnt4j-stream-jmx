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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Resolves locally running JVMs using "sun.com.tools" library.
 *
 * @version $Revision: 1 $
 */
public class JDKToolsVMResolver implements VMResolver<String> {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(JDKToolsVMResolver.class);

	private static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress"; // NON-NLS

	/**
	 * Constant defining wildcard VM descriptor to pick all found running VMs: {@value}.
	 */
	public static final String JVM_DESCR_WILDCARD = "*"; // NON-NLS

	/**
	 * Constant defining VM descriptor prefix specific for this VM resolver: {@value}.
	 */
	public static final String PREFIX = "";

	/**
	 * Constructs new instance of JVM descriptors resolver using "sun.com.tools" library.
	 */
	public JDKToolsVMResolver() {
		LOGGER.log(OpLevel.INFO, "Loading support for JMX connections resolutions using JDK Tools library");
	}

	@Override
	public String getPrefix() {
		return PREFIX;
	}

	@Override
	public boolean isHandlingVMDescriptor(String vmDescr) {
		return true;
	}

	/**
	 * Resolves running JMX server connection address strings list for JVMs matching defined VM descriptor.
	 * <p>
	 * JVM has to have defined agent property {@code "com.sun.management.jmxremote.localConnectorAddress"}.
	 * 
	 * @param vmDescrParams
	 *            connection parameters defining JVM descriptor: display name fragment or pid
	 * @return collection of resolved JVM connection address strings
	 *
	 * @see #findVMs(String)
	 * @see com.sun.tools.attach.VirtualMachine#getAgentProperties()
	 */
	@Override
	public List<VMParams<String>> getVMConnAddresses(VMParams<String> vmDescrParams) {
		List<VirtualMachineDescriptor> descriptors = findVMs(vmDescrParams.getVMRef());
		List<VMParams<String>> connectorAddresses = new ArrayList<>();
		for (VirtualMachineDescriptor descriptor : descriptors) {
			try {
				LOGGER.log(OpLevel.INFO, "JDKToolsVMResolver.getVMConnAddresses: VM descriptor matched JVM [{0}]",
						vmString(descriptor));
				VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());
				String connectorAddress;

				try {
					connectorAddress = virtualMachine.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
					if (connectorAddress == null) {
						LOGGER.log(OpLevel.INFO,
								"JDKToolsVMResolver.getVMConnAddresses: initializing JVM [{0}] management agent...",
								vmString(descriptor));
						String agent = virtualMachine.getSystemProperties().getProperty("java.home") + File.separator
								+ "lib" + File.separator + "management-agent.jar";
						virtualMachine.loadAgent(agent);

						connectorAddress = virtualMachine.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
						if (connectorAddress == null) {
							throw new IOException(
									"JVM [" + vmString(descriptor) + "] does not support JMX connection...");
						}
					}
					connectorAddresses.add(new VMDescriptorParams(connectorAddress).setBaseVMParams(vmDescrParams));
				} finally {
					virtualMachine.detach();
				}
			} catch (Exception exc) {
				LOGGER.log(OpLevel.WARNING,
						"JDKToolsVMResolver.getVMConnAddresses: failed to retrieve JVM [{0}] connection address...",
						vmString(descriptor), exc);
			}
		}

		return connectorAddresses;
	}

	/**
	 * Attaches to running JVMs process.
	 *
	 * @param vmDescr
	 *            JVM descriptor: display name fragment or pid
	 * @param agentPath
	 *            agent library path
	 * @param agentOptions
	 *            agent options
	 * @throws Exception
	 *             if any exception occurs while attaching to JVM
	 *
	 * @see #findVMs(String)
	 * @see com.sun.tools.attach.VirtualMachine#attach(String)
	 */
	public static void attachVM(String vmDescr, String agentPath, String agentOptions) throws Exception {
		Collection<VirtualMachineDescriptor> descriptors = findVMs(vmDescr);

		for (VirtualMachineDescriptor descriptor : descriptors) {
			LOGGER.log(OpLevel.INFO, "JDKToolsVMResolver.attachVM: attaching agent {0} to {1}", agentPath,
					vmString(descriptor));
			VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());

			try {
				LOGGER.log(OpLevel.INFO,
						"JDKToolsVMResolver.attachVM: VM loading agent agent.path={0}, agent.options={1}", agentPath,
						agentOptions);

				virtualMachine.loadAgent(agentPath, agentOptions);

				LOGGER.log(OpLevel.INFO, "JDKToolsVMResolver.attachVM: attached and loaded...");
			} finally {
				virtualMachine.detach();
			}
		}
	}

	private static String vmString(VirtualMachineDescriptor descriptor) {
		String vmStr = descriptor.id();
		if (descriptor.displayName() != descriptor.id()) {
			vmStr += ":" + descriptor.displayName();
		}
		return vmStr;
	}

	/**
	 * Finds running JVMs matching defined VM descriptor string cotaining JVM process PID or display name fragment.
	 *
	 * @param vmDescr
	 *            JVM descriptor: display name fragment or pid
	 * @return list of found JVM descriptors
	 * @throws RuntimeException
	 *             if no running JVM found
	 *
	 * @see com.sun.tools.attach.VirtualMachine#list()
	 */
	public static List<VirtualMachineDescriptor> findVMs(String vmDescr) {
		List<VirtualMachineDescriptor> runningVMsList = VirtualMachine.list();

		List<VirtualMachineDescriptor> descriptors = new ArrayList<>(5);
		for (VirtualMachineDescriptor rVM : runningVMsList) {
			if (matchesDescriptor(rVM, vmDescr) && !isSamplingAgent(rVM)) {
				descriptors.add(rVM);
			}
		}

		if (descriptors.isEmpty()) {
			LOGGER.log(OpLevel.INFO, "JDKToolsVMResolver.findVMs: ----------- Available JVMs -----------");
			for (VirtualMachineDescriptor vmD : runningVMsList) {
				LOGGER.log(OpLevel.INFO, "JDKToolsVMResolver.findVMs: JVM.id={0}, name={1}", vmD.id(),
						vmD.displayName());
			}
			LOGGER.log(OpLevel.INFO, "JDKToolsVMResolver.findVMs: ---------------- END ----------------");
			throw new RuntimeException("Java VM not found using provided descriptor: [" + vmDescr + "]");
		}

		return descriptors;
	}

	private static boolean matchesDescriptor(VirtualMachineDescriptor vm, String vmDescr) {
		return vm.displayName().contains(vmDescr) //
				|| vm.id().equalsIgnoreCase(vmDescr) //
				|| JVM_DESCR_WILDCARD.equals(vmDescr);
	}

	private static boolean isSamplingAgent(VirtualMachineDescriptor vm) {
		return vm.displayName().contains(SamplingAgent.class.getSimpleName());
	}
}
