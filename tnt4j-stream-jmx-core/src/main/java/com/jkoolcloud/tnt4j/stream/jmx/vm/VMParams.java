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

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.StreamJMXConstants;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;
import com.jkoolcloud.tnt4j.utils.SecurityUtils;

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
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(VMParams.class);

	/**
	 * Constant defining default VM reconnect interval in seconds: {@value}.
	 */
	public static final long CONN_RETRY_INTERVAL = 10;

	private T vmRef;

	private String pass;
	private ReconnectRule reconnectRule = RECONNECT;
	private long reconnectInterval = CONN_RETRY_INTERVAL;

	private Map<String, String> optionsMap = new HashMap<>(5);

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
	 * Sets VM access user password.
	 *
	 * @param pass
	 *            VM access user password
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setPass(String pass) {
		this.pass = SecurityUtils.getPass2(pass);
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
	 * Sets VM descriptor options line.
	 *
	 * @param optionsLine
	 *            VM descriptor options line
	 *
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setOptionsLine(String optionsLine) {
		if (optionsLine != null) {
			String[] opts = optionsLine.split(StreamJMXConstants.MULTI_VALUE_DELIM);
			for (String opt : opts) {
				String[] optionTokens = opt.split(StreamJMXConstants.KV_DELIM);

				if (optionTokens.length == 2) {
					String optKey = optionTokens[0];
					optionsMap.put(optKey, optionTokens[1]);
				} else {
					LOGGER.log(OpLevel.WARNING, "VMParams.setOptionsLine: invalid property definition ''{0}''", opt);
				}
			}
		}

		return this;
	}

	public Map<String, String> getOptionsScopeMap(String scope) {
		Map<String, String> optionsScopeMap = new HashMap<>(5);

		String optKey;
		for (Map.Entry<String, String> ome : optionsMap.entrySet()) {
			if (ome.getKey().startsWith(scope)) {
				optKey = ome.getKey().substring(scope.length());
				optionsScopeMap.put(optKey, ome.getValue());
			}
		}

		return optionsScopeMap;
	}

	/**
	 * Sets VM descriptor option value.
	 *
	 * @param opName
	 *            name of the option to set
	 * @param opValue
	 *            option value
	 * @return instance of this VM descriptor parameters package
	 */
	public VMParams<T> setOption(String opName, String opValue) {
		optionsMap.put(opName, opValue);
		return this;
	}

	/**
	 * Returns VM descriptor option value.
	 *
	 * @param opName
	 *            name of the option to get
	 * @return VM descriptor option value
	 */
	public String getOption(String opName) {
		return optionsMap.get(opName);
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
			this.pass = bvmp.pass;
			this.reconnectRule = bvmp.reconnectRule;
			this.reconnectInterval = bvmp.reconnectInterval;
			this.optionsMap = optionsMap;
		}

		return this;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", getClass().getSimpleName() + "[", "]") // NON-NLS
				.add("vmRef=" + vmRef) // NON-NLS
				.add("optionsMap='" + optionsMap + "'") // NON-NLS
				.add("pass='" + Utils.hideEnd(pass, "*", 0) + "'") // NON-NLS
				.add("reconnectRule=" + reconnectRule) // NON-NLS
				.add("reconnectInterval=" + reconnectInterval) // NON-NLS
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
