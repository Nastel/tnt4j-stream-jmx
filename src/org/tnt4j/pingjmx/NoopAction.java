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
package org.tnt4j.pingjmx;

import org.tnt4j.pingjmx.conditions.AttributeAction;
import org.tnt4j.pingjmx.conditions.AttributeSample;
import org.tnt4j.pingjmx.conditions.Condition;


/**
 * <p> 
 * This class defines a NOOP action. Action that does nothing.
 * </p>
 * 
 * @see Condition
 * @see AttributeSample
 * @see AttributeAction
 * @version $Revision: 1 $
 * 
 */
public class NoopAction implements AttributeAction {
	public static final NoopAction NOOP = new NoopAction();
	
	@Override
	public Object action(Condition cond, AttributeSample sample) {
		return null;
	}
}
