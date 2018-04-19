/*
 * Copyright 2015-2018 JKOOL, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jkoolcloud.tnt4j.stream.jmx.utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.DefaultEventSinkFactory;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Running JVM attaching/connecting related utility methods.
 * 
 * @version $Revision: 1 $
 */
public class VMUtils {
	private static final EventSink LOGGER = DefaultEventSinkFactory.defaultEventSink(VMUtils.class);

	private static final String CONNECTOR_ADDRESS = "com.sun.management.jmxremote.localConnectorAddress";

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
	 */
	public static void attachVM(String vmDescr, String agentPath, String agentOptions) throws Exception {
		Collection<VirtualMachineDescriptor> descriptors = findVMs(vmDescr);

		for (VirtualMachineDescriptor descriptor : descriptors) {
			LOGGER.log(OpLevel.INFO, "SamplingAgent.attachVM: attaching agent {0} to {1}", agentPath,
					vmString(descriptor));
			VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());

			try {
				LOGGER.log(OpLevel.INFO, "SamplingAgent.attachVM: VM loading agent agent.path={0}, agent.options={1}",
						agentPath, agentOptions);

				virtualMachine.loadAgent(agentPath, agentOptions);

				LOGGER.log(OpLevel.INFO, "SamplingAgent.attachVM: attached and loaded...");
			} finally {
				virtualMachine.detach();
			}
		}
	}

	/**
	 * Resolves running JVM JMX server connection address string. JVM has to have defined agent property
	 * {@code "com.sun.management.jmxremote.localConnectorAddress"}.
	 * 
	 * @param vmDescr
	 *            JVM descriptor: display name fragment or pid
	 * @return resolved JVM connection address string
	 * @throws IOException
	 *             if any exception occurs while retrieving JVM connection address
	 *
	 * @see #findVMs(String)
	 */
	public static String getVMConnAddress(String vmDescr) throws IOException {
		try {
			VirtualMachineDescriptor descriptor = findVMs(vmDescr).get(0);
			VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());
			String connectorAddress;

			try {
				connectorAddress = virtualMachine.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
				if (connectorAddress == null) {
					LOGGER.log(OpLevel.INFO,
							"SamplingAgent.getVMConnAddress: initializing JVM [{0}] management agent...",
							vmString(descriptor));
					String agent = virtualMachine.getSystemProperties().getProperty("java.home") + File.separator
							+ "lib" + File.separator + "management-agent.jar";
					virtualMachine.loadAgent(agent);

					connectorAddress = virtualMachine.getAgentProperties().getProperty(CONNECTOR_ADDRESS);
					if (connectorAddress == null) {
						throw new IOException("JVM [" + vmString(descriptor) + "] does not support JMX connection...");
					}
				}
			} finally {
				virtualMachine.detach();
			}

			return connectorAddress;
		} catch (IOException exc) {
			throw exc;
		} catch (Exception exc) {
			throw new IOException(exc);
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
	 */
	public static List<VirtualMachineDescriptor> findVMs(String vmDescr) {
		List<VirtualMachineDescriptor> runningVMsList = VirtualMachine.list();

		List<VirtualMachineDescriptor> descriptors = new ArrayList<VirtualMachineDescriptor>(5);
		for (VirtualMachineDescriptor rVM : runningVMsList) {
			if ((rVM.displayName().contains(vmDescr)
					&& !rVM.displayName().contains(SamplingAgent.class.getSimpleName()))
					|| rVM.id().equalsIgnoreCase(vmDescr)) {
				descriptors.add(rVM);
			}
		}

		if (descriptors.isEmpty()) {
			LOGGER.log(OpLevel.INFO, "SamplingAgent.findVMs: ----------- Available JVMs -----------");
			for (VirtualMachineDescriptor vmD : runningVMsList) {
				LOGGER.log(OpLevel.INFO, "SamplingAgent.findVMs: JVM.id={0}, name={1}", vmD.id(), vmD.displayName());
			}
			LOGGER.log(OpLevel.INFO, "SamplingAgent.findVMs: ---------------- END ----------------");
			throw new RuntimeException("Java VM not found using provided descriptor: [" + vmDescr + "]");
		}

		return descriptors;
	}

}
