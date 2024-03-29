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
package com.jkoolcloud.tnt4j.stream.jmx.conditions;

/**
 * <p>
 * This class defines a simple numeric condition that supports =, !=, &gt;, &gt;=, &lt;, &lt;= operators for a given JMX
 * object, property.
 * </p>
 * 
 * @see AttributeCondition
 * @see AttributeSample
 * @version $Revision: 1 $
 * 
 */
public class SimpleCondition implements AttributeCondition {
	String name;
	String objName;
	String attrName;
	Number value;
	String op;

	/**
	 * Create a condition on a numeric object/attribute and a given numeric value by applying an operator.
	 * 
	 * @param objName
	 *            canonical MBean object name
	 * @param attrName
	 *            attribute name
	 * @param value
	 *            numeric value to apply operator
	 * @param op
	 *            operator to apply ( =, !=, &gt;, &gt;=, &lt;, &lt;=)
	 */
	public SimpleCondition(String objName, String attrName, Number value, String op) {
		this.objName = objName;
		this.attrName = attrName;
		this.value = value;
		this.op = op;
		this.name = attrName + "@" + objName;
	}

	@Override
	public String toString() {
		return name + " " + op + " " + value;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public boolean evaluate(AttributeSample sample) {
		Object sValue = sample.getValue(attrName);
		if (sValue != null && sample.getObjectName().getCanonicalName().equals(objName)) {
			if (op.endsWith("=")) {
				return sValue.equals(value);
			} else if (sValue instanceof Number) {
				double sValueD = ((Number) sValue).doubleValue();
				if (op.endsWith("!=")) {
					return sValueD != value.doubleValue();
				} else if (op.endsWith(">")) {
					return sValueD > value.doubleValue();
				} else if (op.endsWith(">=")) {
					return sValueD >= value.doubleValue();
				} else if (op.endsWith("<")) {
					return sValueD < value.doubleValue();
				} else if (op.endsWith("<=")) {
					return sValueD <= value.doubleValue();
				}
			}
		}
		return false;
	}
}
