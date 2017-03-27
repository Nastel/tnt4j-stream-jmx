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

package com.jkoolcloud.tnt4j.stream.jmx;

import java.util.List;
import java.util.Properties;

import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

/**
 * Running JVM attaching/connecting realated utility methods.
 * 
 * @version $Revision: 1 $
 */
public class VMUtils {

	/**
	 * Attaches to running JVM process.
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
	 * @see #findVM(String)
	 */
	public static void attachVM(String vmDescr, String agentPath, String agentOptions) throws Exception {
		VirtualMachineDescriptor descriptor = findVM(vmDescr);
		System.out.println("SamplingAgent.attach: attaching agent " + agentPath + " to " + descriptor + "");
		final VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());

		System.out.println(
				"SamplingAgent.attach: VM loading agent agent.path=" + agentPath + ", agent.options=" + agentOptions);

		virtualMachine.loadAgent(agentPath, agentOptions);

		System.out.println("SamplingAgent.attach: attached and loaded...");
		virtualMachine.detach();
	}

	/**
	 * Resolves running JVM JMX server connection address string. JVM has to have defined agent property
	 * {@code "com.sun.management.jmxremote.localConnectorAddress"}.
	 * 
	 * @param vmDescr
	 *            JVM descriptor: display name fragment or pid
	 * @return resolved JVM connection address string
	 * @throws Exception
	 *             if any exception occurs while retrieving JVM connection address
	 *
	 * @see #findVM(String)
	 */
	public static String getVMConnAddress(String vmDescr) throws Exception {
		VirtualMachineDescriptor descriptor = findVM(vmDescr);

		final VirtualMachine virtualMachine = VirtualMachine.attach(descriptor.id());

		Properties props = virtualMachine.getAgentProperties();
		String connectorAddress = props.getProperty("com.sun.management.jmxremote.localConnectorAddress");
		if (connectorAddress == null) {
			throw new RuntimeException("JVM does not support JMX connection...");
		}

		return connectorAddress;
	}

	/**
	 * Finds running JVM matching defined VM descriptor string cotaining JVM process PID or display name fragment.
	 * 
	 * @param vmDescr
	 *            JVM descriptor: display name fragment or pid
	 * @return found JVM descriptor object
	 * @throws RuntimeException
	 *             if no running JVM found
	 */
	public static VirtualMachineDescriptor findVM(String vmDescr) {
		List<VirtualMachineDescriptor> runningVMsList = VirtualMachine.list();

		VirtualMachineDescriptor descriptor = null;
		for (VirtualMachineDescriptor rVM : runningVMsList) {
			if ((rVM.displayName().contains(vmDescr)
					&& !rVM.displayName().contains(SamplingAgent.class.getSimpleName()))
					|| rVM.id().equalsIgnoreCase(vmDescr)) {
				descriptor = rVM;
				break;
			}
		}

		if (descriptor == null) {
			System.err.println("SamplingAgent: ----------- Available JVMs -----------");
			for (VirtualMachineDescriptor vmD : runningVMsList) {
				System.err.println("SamplingAgent: JVM.id=" + vmD.id() + ", name=" + vmD.displayName());
			}
			System.err.println("SamplingAgent: ---------------- END ----------------");
			throw new RuntimeException("Java VM not found using provided descriptor: [" + vmDescr + "]");
		}

		return descriptor;
	}

}
