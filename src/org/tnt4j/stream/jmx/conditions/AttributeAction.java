/*
 * Copyright 2015 JKOOL, LLC.
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
package org.tnt4j.stream.jmx.conditions;

import org.tnt4j.stream.jmx.core.SampleContext;


/**
 * <p> 
 * This interface defines a way to run actions on specific MBean attributes during a sample.
 * </p>
 * 
 * 
 * @version $Revision: 1 $
 * 
 */
public interface AttributeAction {
	/**
	 * Run action instance
	 * 
	 * @param context current sample context
	 * @param cond condition that triggered this action
	 * @param sample current sample that was evaluated
	 * @return a user defined return value
	 */
	Object action(SampleContext context, AttributeCondition cond, AttributeSample sample);
}
