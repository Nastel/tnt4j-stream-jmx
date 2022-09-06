/*
 * Copyright 2015-2022 JKOOL, LLC.
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
package com.jkoolcloud.tnt4j.stream.jmx.conditions;

/**
 * <p>
 * This interface defines a nested handler of listeners with registration methods returning some user defined type.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 */
public interface NestedHandler<V, K> {
	/**
	 * Add a listener to this handler
	 * 
	 * @param listener
	 *            instance return instance of another object
	 * @return handler instance
	 */
	V addListener(K listener);

	/**
	 * Remove a listener.
	 * 
	 * @param listener
	 *            instance return instance of another object
	 * @return handler instance
	 */
	V removeListener(K listener);
}
