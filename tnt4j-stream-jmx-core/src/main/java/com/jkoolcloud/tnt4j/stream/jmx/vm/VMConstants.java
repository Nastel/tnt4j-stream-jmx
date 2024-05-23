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

/**
 * JVM JMX connections/VM descriptors resolution API constants.
 *
 * @version $Revision: 2 $
 */
public interface VMConstants {

	/**
	 * Prefix for VM descriptor custom properties scope.
	 */
	String CUSTOM_OPTIONS_PREFIX = "custom."; // NON-NLS
	/**
	 * Prefix for VM descriptor JVM system properties scope.
	 */
	String SYS_OPTIONS_PREFIX = "sys."; // NON-NLS
	/**
	 * Prefix for VM descriptor OS environment variables scope.
	 */
	String ENV_OPTIONS_PREFIX = "env."; // NON-NLS

	/**
	 * VM descriptor property name for URL definition. Alias for {@value #PROP_VM_URL}.
	 */
	String PROP_VM_VM = "vm"; // NON-NLS
	/**
	 * VM descriptor property name for URL definition.
	 */
	String PROP_VM_URL = "vm.url"; // NON-NLS
	/**
	 * VM descriptor property name for host(s) definition.
	 */
	String PROP_VM_HOST = "vm.host"; // NON-NLS
	/**
	 * VM descriptor property name for host(s) definition. Alias for {@value #PROP_VM_HOST}.
	 */
	String PROP_VM_HOSTS = "vm.hosts"; // NON-NLS
	/**
	 * VM descriptor property name for port(s) definition.
	 */
	String PROP_VM_PORT = "vm.port"; // NON-NLS
	/**
	 * VM descriptor property name for port(s) definition. Alias for {@value #PROP_VM_PORT}.
	 */
	String PROP_VM_PORTS = "vm.ports"; // NON-NLS
	/**
	 * VM descriptor property name for user name definition.
	 */
	String PROP_VM_USER = "vm.user"; // NON-NLS
	/**
	 * VM descriptor property name for user password definition.
	 */
	String PROP_VM_PASS = "vm.pass"; // NON-NLS
	/**
	 * VM descriptor property name for user password definition. Alias for {@value #PROP_VM_PASS}.
	 */
	String PROP_VM_PASSWORD = "vm.password"; // NON-NLS
	/**
	 * VM descriptor property name for reconnection policy definition.
	 */
	String PROP_VM_RECONNECT = "vm.reconnect"; // NON-NLS
	/**
	 * VM descriptor property name for reconnection period in seconds definition.
	 */
	String PROP_VM_RECONNECT_SEC = "vm.reconnect.sec"; // NON-NLS
	/**
	 * VM descriptor property name for additional options string definition. NOTE: for internal use only!
	 */
	String PROP_VM_ADDITIONAL_OPTIONS = "vm.additional.options"; // NON-NLS
	/**
	 * VM descriptor property name for agent options definition.
	 */
	String PROP_AGENT_OPTIONS = "agent.options"; // NON-NLS
	/**
	 * VM descriptor property name for source FQN definition.
	 */
	String PROP_SOURCE_FQN = "source.fqn"; // NON-NLS
}
