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

import org.tnt4j.pingjmx.PingSample;

/**
 * <p> 
 * This class defines a simple numeric condition that supports =, !=, >, >=, <, <=
 * operators for a given JMX object, property.
 * </p>
 * 
 * @see Condition
 * @see PingSample
 * @version $Revision: 1 $
 * 
 */
public class SimpleCondition implements Condition {
	String name;
	String objName;
	String attrName;
	Number value;
	String op;
	
	public SimpleCondition(String objName, 
			String attrName, Number value, String op) {
		this.objName = objName;
		this.attrName = attrName;
		this.value = value;
		this.op = op;
		this.name = attrName + "@" + objName;
	}
	
	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean evaluate(PingSample sample) {
		if (sample.getAttributeInfo().getName().equals(attrName)
				&& sample.getObjetName().getCanonicalName().equals(objName)) {
			if (op.endsWith("=")) {
				return sample.get().equals(value);
			} else if (op.endsWith("!=")) {
				Object val = sample.get();
				if (val instanceof Number) {
					return ((Number) val).doubleValue() != value.doubleValue();
				}
			} else if (op.endsWith(">")) {
				Object val = sample.get();
				if (val instanceof Number) {
					return ((Number) val).doubleValue() > value.doubleValue();
				}
			} else if (op.endsWith(">=")) {
				Object val = sample.get();
				if (val instanceof Number) {
					return ((Number) val).doubleValue() >= value.doubleValue();
				}
			} else if (op.endsWith("<")) {
				Object val = sample.get();
				if (val instanceof Number) {
					return ((Number) val).doubleValue() < value.doubleValue();
				}
			} else if (op.endsWith("<=")) {
				Object val = sample.get();
				if (val instanceof Number) {
					return ((Number) val).doubleValue() <= value.doubleValue();
				}
			}
		}
		return false;
	}
}
