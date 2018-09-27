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
package com.jkoolcloud.tnt4j.stream.jmx;

/**
 * Monitored JVM JMX sampling agent runner thread.
 *
 * @version $Revision: 1 $
 */
public class SamplingAgentThread extends Thread {
	private String vmSourceFQN;
	private SamplingAgent samplingAgent;

	/**
	 * Constructs a new SamplingAgentThread.
	 *
	 * @param samplingRunnable
	 *            runnable running JMX sampling
	 * @param samplingAgent
	 *            sampling agent instance
	 * @param vmSourceFQN
	 *            VM source FQN addition
	 * 
	 */
	public SamplingAgentThread(Runnable samplingRunnable, SamplingAgent samplingAgent, String vmSourceFQN) {
		super(samplingRunnable);

		this.samplingAgent = samplingAgent;
		this.vmSourceFQN = vmSourceFQN;

		setName("SamplerAgentThread-" + getId());
	}

	/**
	 * Returns monitored JVM JMX sampling agent.
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
