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

import java.util.StringJoiner;

/**
 * Base class to define VM descriptor and context attributes.
 *
 * @param <T>
 *            the type of VM reference: descriptor or URL
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.VMDescriptorParams
 * @see com.jkoolcloud.tnt4j.stream.jmx.vm.JMXURLConnectionParams
 */
public abstract class VMParams<T> {

	/**
	 * Constant defining default VM reconnect interval in seconds: {@value}.
	 */
	public static final long CONN_RETRY_INTERVAL = 10;

	private T vmRef;

	private String user;
	private String pass;
	private String agentOptions;
	private String additionalSourceFQN;
	private ReconnectRule reconnectRule = RECONNECT;
	private long reconnectInterval = CONN_RETRY_INTERVAL;

	private String additionalOptions;

	/**
	 * Constructs a new instance of VM descriptor parameters package.
	 */
	protected VMParams() {
	}

	/**
	 * Constructs a new instance of VM descriptor parameters package.
	 *
	 * @param vmRef
	 *            VM reference
	 */
	protected VMParams(T vmRef) {
		this.vmRef = vmRef;
	}

	/**
	 * Sets VM reference.
	 *
	 * @param vmRef
	 *            the VM reference
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setVMRef(T vmRef) {
		this.vmRef = vmRef;
		return this;
	}

	/**
	 * Returns VM reference.
	 *
	 * @return the VM reference
	 */
	public T getVMRef() {
		return vmRef;
	}

	/**
	 * Sets VM access user login name.
	 *
	 * @param user
	 *            VM access user login name
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setUser(String user) {
		this.user = user;
		return this;
	}

	/**
	 * Returns VM access user login name.
	 *
	 * @return VM access user login name
	 */
	public String getUser() {
		return user;
	}

	/**
	 * Sets VM access user password.
	 *
	 * @param pass
	 *            VM access user password
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setPass(String pass) {
		this.pass = pass;
		return this;
	}

	/**
	 * Returns VM access user password.
	 *
	 * @return VM access user password
	 */
	public String getPass() {
		return pass;
	}

	/**
	 * Sets VM sampling agent options.
	 *
	 * @param agentOptions
	 *            VM sampling agent options
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setAgentOptions(String agentOptions) {
		this.agentOptions = agentOptions;
		return this;
	}

	/**
	 * Returns VM sampling agent options.
	 *
	 * @return VM sampling agent options
	 */
	public String getAgentOptions() {
		return agentOptions;
	}

	/**
	 * Sets additional source fqn.
	 *
	 * @param additionalSourceFQN
	 *            the additional source fqn
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setAdditionalSourceFQN(String additionalSourceFQN) {
		this.additionalSourceFQN = additionalSourceFQN;
		return this;
	}

	/**
	 * Returns additional source fqn.
	 *
	 * @return the additional source fqn
	 */
	public String getAdditionalSourceFQN() {
		return additionalSourceFQN;
	}

	/**
	 * Sets JMX service reconnect rule.
	 *
	 * @param reconnectRule
	 *            JMX service reconnect rule
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setReconnectRule(ReconnectRule reconnectRule) {
		this.reconnectRule = reconnectRule;
		return this;
	}

	/**
	 * Returns JMX service reconnect rule.
	 *
	 * @return the JMX service reconnect rule
	 */
	public ReconnectRule getReconnectRule() {
		return reconnectRule;
	}

	/**
	 * Sets JMX service reconnect interval in seconds.
	 *
	 * @param reconnectInterval
	 *            JMX service reconnect interval in seconds
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setReconnectInterval(long reconnectInterval) {
		this.reconnectInterval = reconnectInterval;
		this.reconnectRule = reconnectInterval < 0 ? DONT_RECONNECT : RECONNECT;

		return this;
	}

	/**
	 * Returns JMX service reconnect interval in seconds.
	 *
	 * @return the JMX service reconnect interval in seconds
	 */
	public long getReconnectInterval() {
		return reconnectInterval;
	}

	/**
	 * Sets VM descriptor additional options.
	 *
	 * @param additionalOptions
	 *            VM descriptor additional options
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setAdditionalOptions(String additionalOptions) {
		this.additionalOptions = additionalOptions;
		return this;
	}

	/**
	 * Returns VM descriptor additional options.
	 *
	 * @return VM descriptor additional options
	 */
	public String getAdditionalOptions() {
		return additionalOptions;
	}

	/**
	 * Adds VM descriptor additional option to additional options list.
	 * 
	 * @param key
	 *            additional option key
	 * @param value
	 *            additional option value
	 */
	public void addAdditionalOption(String key, String value) {
		if (additionalOptions == null) {
			additionalOptions = "";
		} else {
			additionalOptions += VMConstants.OTHER_OPTIONS_DELIM;
		}

		additionalOptions += key + VMConstants.OTHER_OPTIONS_KV_DELIM + value;
	}

	/**
	 * Propagates VM descriptor attributes from base VM descriptor properties into this VM descriptor instance.
	 *
	 * @param bvmp
	 *            base VM descriptor parameters
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	protected VMParams<T> setBaseVMParams(VMParams<?> bvmp) {
		if (bvmp != null) {
			this.user = bvmp.user;
			this.pass = bvmp.pass;
			this.agentOptions = bvmp.agentOptions;
			this.additionalSourceFQN = bvmp.additionalSourceFQN;
			this.reconnectRule = bvmp.reconnectRule;
			this.reconnectInterval = bvmp.reconnectInterval;
			this.additionalOptions = bvmp.additionalOptions;
		}

		return this;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", getClass().getSimpleName() + "[", "]") // NON-NLS
				.add("vmRef=" + vmRef) // NON-NLS
				.add("user='" + user + "'") // NON-NLS
				.add("pass='" + pass + "'") // NON-NLS
				.add("agentOptions='" + agentOptions + "'") // NON-NLS
				.add("additionalSourceFQN='" + additionalSourceFQN + "'") // NON-NLS
				.add("reconnectRule=" + reconnectRule) // NON-NLS
				.add("reconnectInterval=" + reconnectInterval) // NON-NLS
				.add("additionalOptions='" + additionalOptions + "'") // NON-NLS
				.toString();
	}

	/**
	 * Reconnection policy to reconnect.
	 */
	public static final ReconnectRule RECONNECT = new ReconnectRule() {
		@Override
		public boolean shouldStopSampling() {
			return false;
		}

		@Override
		public String toString() {
			return "RECONNECT"; // NON-NLS
		}
	};

	/**
	 * Reconnection policy to not reconnect.
	 */
	public static final ReconnectRule DONT_RECONNECT = new ReconnectRule() {
		@Override
		public boolean shouldStopSampling() {
			return true;
		}

		@Override
		public String toString() {
			return "DON'T RECONNECT"; // NON-NLS
		}
	};

	/**
	 * Interface defining VM JMX reconnection policy rules.
	 */
	public interface ReconnectRule {
		/**
		 * Returns flag indicating whether to not reconnect when connection fails or can't be established.
		 *
		 * @return {@code true} if should not reconnect using this connection, {@code false} - otherwise
		 */
		boolean shouldStopSampling();
	}
}
