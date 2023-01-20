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

package com.jkoolcloud.tnt4j.stream.jmx.servlet;

/**
 * Interface defining common servlets used properties features.
 *
 * @version $Revision: 1 $
 */
public interface StreamJMXProperty {

	/**
	 * Returns property key.
	 *
	 * @return property key
	 */
	String key();

	/**
	 * Returns property default value.
	 *
	 * @return default value
	 */
	String defaultValue();

	/**
	 * Returns property display type.
	 *
	 * @return property display type
	 */
	Display display();

	/**
	 * Check if property has provided scope.
	 *
	 * @param scope
	 *            scope value to check
	 * @return {@code true} if property has provided scope, {@code false} - otherwise
	 */
	boolean isInScope(Scope scope);

	/**
	 * All possible property scopes.
	 */
	public enum Scope {
		LOCAL, INTERIM, SYSTEM, FILE
	}

	/**
	 * All property display types.
	 */
	public enum Display {
		EDITABLE, READ_ONLY, HIDDEN, EDITABLE_PW, FILE_EDITOR
	}
}
