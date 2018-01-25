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
package com.jkoolcloud.tnt4j.stream.jmx.conditions;

/**
 * <p>
 * This interface defines a way to evaluate user defined conditions for a given attribute sample.
 * </p>
 * 
 * @see AttributeSample
 * @version $Revision: 1 $
 * 
 */
public interface AttributeCondition {
	/**
	 * Name associated with this condition
	 * 
	 * @return condition name
	 */
	String getName();

	/**
	 * Evaluate current condition
	 * 
	 * @param sample
	 *            current sample that was evaluated
	 * @return true if condition evaluated, false otherwise
	 */
	boolean evaluate(AttributeSample sample);
}
