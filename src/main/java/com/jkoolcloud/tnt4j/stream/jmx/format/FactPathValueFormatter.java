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

package com.jkoolcloud.tnt4j.stream.jmx.format;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;

import com.jkoolcloud.tnt4j.core.Operation;
import com.jkoolcloud.tnt4j.core.Property;
import com.jkoolcloud.tnt4j.core.Snapshot;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * This class provides key/value formatting for tnt4j activities, events and snapshots. The output format follows the
 * following format:
 * <p>
 * {@code "OBJ:object-path\name1=value1,....,object-path\nameN=valueN"}.
 * </p>
 * Newline is added at the end of each line.
 *
 * @version $Revision: 1 $
 */
public class FactPathValueFormatter extends FactNameValueFormatter {

	private static final String[][] PATH_KEYS = new String[][] { { "domain" }, { "type" }, { "name", "brokerName" },
			{ "service", "connector", "destinationType" }, { "instanceName", "connectorName", "destinationName" } };

	private Comparator<Snapshot> snapshotComparator;
	private Comparator<Property> propertyComparator;

	public FactPathValueFormatter() {
		super();
	}

	@Override
	protected Collection<Snapshot> getSnapshots(Operation op) {
		Collection<Snapshot> sList = super.getSnapshots(op);

		Snapshot[] sa = new Snapshot[sList.size()];
		sa = sList.toArray(sa);
		Arrays.sort(sa, getSnapshotComparator());

		return Arrays.asList(sa);
	}

	private Comparator<Snapshot> getSnapshotComparator() {
		if (snapshotComparator == null) {
			snapshotComparator = new Comparator<Snapshot>() {
				@Override
				public int compare(Snapshot s1, Snapshot s2) {
					String s1Path = getSnapNameStr(s1.getName());
					String s2Path = getSnapNameStr(s2.getName());

					return s1Path.compareTo(s2Path);
				}
			};
		}

		return snapshotComparator;
	}

	@Override
	protected Collection<Property> getProperties(Snapshot snap) {
		Collection<Property> pList = super.getProperties(snap);

		Property[] pa = new Property[pList.size()];
		pa = pList.toArray(pa);
		Arrays.sort(pa, getPropertyComparator());

		return Arrays.asList(pa);
	}

	private Comparator<Property> getPropertyComparator() {
		if (propertyComparator == null) {
			propertyComparator = new Comparator<Property>() {
				@Override
				public int compare(Property p1, Property p2) {
					return p1.getKey().compareTo(p2.getKey());
				}
			};
		}

		return propertyComparator;
	}

	@Override
	protected String getSnapNameStr(String objCanonName) {
		if (Utils.isEmpty(objCanonName)) {
			return objCanonName;
		}

		int ddIdx = objCanonName.indexOf(':');

		if (ddIdx == -1) {
			return objCanonName;
		}

		String domainStr = objCanonName.substring(0, ddIdx);

		Properties props = new Properties();
		props.setProperty("domain", domainStr);

		String propsStr = objCanonName.substring(ddIdx + 1);

		if (propsStr.length() > 0) {
			propsStr = propsStr.replaceAll(FIELD_SEP, END_SEP);
			Reader rdr = new StringReader(propsStr);
			try {
				props.load(rdr);
			} catch (IOException exc) {
			} finally {
				try {
					rdr.close();
				} catch (IOException exc) {
				}
			}
		}

		StringBuilder pathBuilder = new StringBuilder();

		for (String[] levelKeys : PATH_KEYS) {
			for (String pKey : levelKeys) {
				String pp = (String) props.remove(pKey);

				if (!Utils.isEmpty(pp)) {
					pathBuilder.append(pathBuilder.length() > 0 ? PATH_DELIM : "").append(pp);
				}
			}
		}

		if (!props.isEmpty()) {
			for (Map.Entry<Object, Object> pe : props.entrySet()) {
				pathBuilder.append(pathBuilder.length() > 0 ? PATH_DELIM : "").append(String.valueOf(pe.getKey()))
						.append(":").append(String.valueOf(pe.getValue()));
			}
		}

		return pathBuilder.toString();
	}

	@Override
	protected String toString(Object value) {
		if (value instanceof int[]) {
			return Arrays.toString((int[]) value);
		}
		if (value instanceof byte[]) {
			return Arrays.toString((byte[]) value);
		}
		if (value instanceof char[]) {
			return Arrays.toString((char[]) value);
		}
		if (value instanceof long[]) {
			return Arrays.toString((long[]) value);
		}
		if (value instanceof float[]) {
			return Arrays.toString((float[]) value);
		}
		if (value instanceof short[]) {
			return Arrays.toString((short[]) value);
		}
		if (value instanceof double[]) {
			return Arrays.toString((double[]) value);
		}
		if (value instanceof boolean[]) {
			return Arrays.toString((boolean[]) value);
		}
		if (value instanceof Object[]) {
			return toStringDeep((Object[]) value);
		}
		if (value instanceof Collection) {
			Collection<?> c = (Collection<?>) value;
			return toString(c.toArray());
		}
		if (value instanceof Map) {
			Map<?, ?> m = (Map<?, ?>) value;
			return toString(m.entrySet());
		}

		return super.toString(value);
	}

	/**
	 * Returns the appropriate string representation for the specified array.
	 *
	 * @param a array to convert to string representation
	 *
	 * @return string representation of array
	 */
	private String toStringDeep(Object[] a) {
		if (a == null) {
			return "null"; // NON-NLS
		}

		int iMax = a.length - 1;
		if (iMax == -1) {
			return "[]"; // NON-NLS
		}

		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0;; i++) {
			b.append(toString(a[i]));
			if (i == iMax) {
				return b.append(']').toString();
			}
			b.append(", "); // NON-NLS
		}
	}
}
