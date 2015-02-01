/*
 * Copyright 2015 Nastel Technologies, Inc.
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
package org.tnt4j.pingjmx.conditions;

import org.tnt4j.pingjmx.NestedHandler;
import org.tnt4j.pingjmx.SampleListener;
import org.tnt4j.pingjmx.SampleStats;

import com.nastel.jkool.tnt4j.core.ActivityListener;

/**
 * <p> 
 * This interface defines <code>ConditionalListener</code> which provides
 * a way to conditionally process activities.
 * </p>
 * 
 * @see Condition
 * @see AttributeAction
 * @version $Revision: 1 $
 * 
 */
public interface ConditionalListener extends ActivityListener, NestedHandler<ConditionalListener, SampleListener> {
	/**
	 * Register and associate condition with an action
	 * 
	 * @param cond user defined condition with <code>NoopAction</code>
	 */
	ConditionalListener register(Condition cond);
	
	/**
	 * Register and associate condition with an action
	 * 
	 * @param cond user defined condition
	 * @param action action to be triggered when condition evaluates to true
	 */
	ConditionalListener register(Condition cond, AttributeAction action);	
	
	/**
	 * Obtain latest sampling statistics for current instance
	 *
	 * @return latest sampling statistics
	 *  
	 */
	SampleStats getStats();	
}
