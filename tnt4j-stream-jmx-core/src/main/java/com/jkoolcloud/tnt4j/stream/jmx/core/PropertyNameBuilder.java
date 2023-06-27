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
package com.jkoolcloud.tnt4j.stream.jmx.core;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Utility class for building JMX attribute property names as paths tokenized by defined {@code delimiter} string.
 * Default delimiter is {@value #DEFAULT_COMPOSITE_DELIMITER}.
 * <p>
 * It uses {@link StringBuilder} to store property name string and {@link Deque} to mark string builder positions
 * allowing to rewind to particular path position by popping tokens out from built path.
 *
 * @version $Revision: 1 $
 */
public class PropertyNameBuilder {
	public static final String DEFAULT_COMPOSITE_DELIMITER = "\\";
	public static final String DEFAULT_COMPOSITE_DELIMITER_REPLACEMENT = "_";

	private StringBuilder sb;
	private Deque<Integer> marks;
	private final String delimiter;
	private String delimReplacement = DEFAULT_COMPOSITE_DELIMITER_REPLACEMENT;

	/**
	 * Constructs a new PropertyNameBuilder. Default delimiter is {@code "\"}.
	 * 
	 * @param initName
	 *            initial property name string
	 */
	public PropertyNameBuilder(String initName) {
		this(initName, DEFAULT_COMPOSITE_DELIMITER);
	}

	/**
	 * Constructs a new PropertyNameBuilder.
	 *
	 * @param initName
	 *            initial property name string
	 * @param delimiter
	 *            property tokens delimiter
	 */
	public PropertyNameBuilder(String initName, String delimiter) {
		this.marks = new ArrayDeque<>(5);
		this.delimiter = delimiter;
		this.sb = new StringBuilder(checkPropertyName(initName));
	}

	/**
	 * Resets property name builder setting new initial property name value.
	 * <p>
	 * Internal {@link StringBuilder} is reset to {@code 0} position and marks stack is cleared.
	 * 
	 * @param initName
	 *            initial property name string
	 */
	public void reset(String initName) {
		sb.setLength(0);
		sb.append(checkPropertyName(initName));
		marks.clear();
	}

	private String checkPropertyName(String pName) {
		return pName == null ? "null" : pName.replace(delimiter, delimReplacement);
	}

	/**
	 * Appends provided string to current property name in internal {@link StringBuilder}. Constructor defined
	 * {@code delimiter} is added before string to tokenize property name.
	 * <p>
	 * Before appending internal {@link StringBuilder}, current builder length is pushed to marks stack for later use.
	 *
	 * @param str
	 *            string to append to property name
	 * @return instance of this property name builder
	 */
	public PropertyNameBuilder append(String str) {
		marks.push(sb.length());
		if (!isEmpty()) {
			sb.append(delimiter);
		}
		sb.append(checkPropertyName(str));
		return this;
	}

	/**
	 * Resets internal {@link StringBuilder} to marked position of previous token. If marks stack is empty - nothing
	 * happens, leaving initial property name string in string builder.
	 */
	public void popLevel() {
		if (!marks.isEmpty()) {
			sb.setLength(marks.pop());
		}
	}

	/**
	 * Returns internal {@link StringBuilder} contained string and resets string builder to marked position of previous
	 * token.
	 * 
	 * @return complete property name string
	 *
	 * @see #popLevel()
	 */
	public String propString() {
		String str = sb.toString();
		popLevel();

		return str;
	}

	/**
	 * Checks whether internal {@link StringBuilder} contained property name is empty.
	 *
	 * @return {@code true} if property name is empty, {@code false} - otherwise
	 */
	public boolean isEmpty() {
		return sb.length() == 0;
	}

	@Override
	public String toString() {
		return sb.toString();
	}
}
