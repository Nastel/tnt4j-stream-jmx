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
package com.jkoolcloud.tnt4j.stream.jmx;

import java.util.Map;

import javax.management.remote.JMXServiceURL;

import com.jkoolcloud.tnt4j.stream.jmx.vm.VMConstants;
import com.jkoolcloud.tnt4j.stream.jmx.vm.VMParams;

/**
 * Monitored JVM JMX sampling agent runner thread.
 *
 * @version $Revision: 1 $
 */
public class SamplingAgentThread extends Thread {
	private String vmSourceFQN;
	private SamplingAgent samplingAgent;

	private final Runnable agentRunner;

	/**
	 * Constructs a new SamplingAgentThread.
	 *
	 * @param cp
	 *            VM connection parameters
	 * @param connParams
	 *            map of JMX connection parameters, defined by {@link javax.naming.Context}
	 */
	public SamplingAgentThread(VMParams<JMXServiceURL> cp, Map<String, ?> connParams) {
		super();

		agentRunner = new Runnable() {
			@Override
			public void run() {
				try {
					samplingAgent = SamplingAgent.newSamplingAgent();
					samplingAgent.connect(cp, connParams);
					samplingAgent.stopSampler();
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				}
			}
		};

		vmSourceFQN = cp.getOption(VMConstants.PROP_SOURCE_FQN);

		setName("SamplerAgentThread-" + getId());
	}

	@Override
	public void run() {
		if (agentRunner != null) {
			agentRunner.run();
		}
	}

	/**
	 * Returns JMX sampling agent instance run by this thread.
	 *
	 * @return sampling agent instance run by this thread
	 */
	public SamplingAgent getSamplingAgent() {
		return samplingAgent;
	}

	/**
	 * Returns VM specific source FQN addition to be used to build complete sample SourceFQN.
	 *
	 * @return VM specific source FQN addition string
	 */
	public String getVMSourceFQN() {
		return vmSourceFQN;
	}
}
