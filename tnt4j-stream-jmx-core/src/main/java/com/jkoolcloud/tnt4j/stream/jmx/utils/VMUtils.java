/*
 * Copyright 2015-2017 JKOOL, LLC.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Running JVM attaching/connecting related utility methods.
 * 
 * @version $Revision: 1 $
 */
public class VMUtils {

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
			System.out.println("SamplingAgent.attach: attaching agent " + agentPath + " to " + descriptor + "");
			VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());

			try {
				System.out.println("SamplingAgent.attach: VM loading agent agent.path=" + agentPath + ", agent.options="
						+ agentOptions);

				virtualMachine.loadAgent(agentPath, agentOptions);

				System.out.println("SamplingAgent.attach: attached and loaded...");
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
				Properties props = virtualMachine.getAgentProperties();
				connectorAddress = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
				if (connectorAddress == null) {
					throw new IOException("JVM does not support JMX connection...");
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
			System.out.println("SamplingAgent: ----------- Available JVMs -----------");
			for (VirtualMachineDescriptor vmD : runningVMsList) {
				System.out.println("SamplingAgent: JVM.id=" + vmD.id() + ", name=" + vmD.displayName());
			}
			System.out.println("SamplingAgent: ---------------- END ----------------");
			throw new RuntimeException("Java VM not found using provided descriptor: [" + vmDescr + "]");
		}

		return descriptors;
	}

}
