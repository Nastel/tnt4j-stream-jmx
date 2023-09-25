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

package com.jkoolcloud.tnt4j.stream.jmx.format;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;

import javax.management.ObjectName;

import org.apache.commons.lang3.StringUtils;

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

	/**
	 * Defines default unique property key suffix {@value}.
	 */
	public static final String UNIQUE_SUFFIX = "_";

	/**
	 * Unique property key suffix value.
	 */
	protected String uniqueSuffix = UNIQUE_SUFFIX;
	/**
	 * Levels attribute matrix used to build facts object path.
	 */
	protected String[][] pathLevelAttrKeys = null;

	private Comparator<Snapshot> snapshotComparator;
	private Comparator<Property> propertyComparator;

	/**
	 * Constructs a new instance of {@code FactPathValueFormatter}.
	 */
	public FactPathValueFormatter() {
		super();
	}

	@Override
	protected Collection<Snapshot> getSnapshots(Operation op) {
		Collection<Snapshot> sList = super.getSnapshots(op);

		return getSortedCollection(sList, getSnapshotComparator());
	}

	private Comparator<Snapshot> getSnapshotComparator() {
		if (snapshotComparator == null) {
			snapshotComparator = new Comparator<>() {
				@Override
				public int compare(Snapshot s1, Snapshot s2) {
					String s1Path = getSnapName(s1);
					String s2Path = getSnapName(s2);

					return s1Path.compareTo(s2Path);
				}
			};
		}

		return snapshotComparator;
	}

	@Override
	protected Collection<Property> getProperties(Snapshot snap) {
		Collection<Property> pList = super.getProperties(snap);

		return getSortedCollection(pList, getPropertyComparator());
	}

	private static <T> Collection<T> getSortedCollection(Collection<T> col, Comparator<T> comp) {
		List<T> cList;
		if (col instanceof List<?>) {
			cList = (List<T>) col;
		} else {
			cList = Collections.list(Collections.enumeration(col));
		}
		cList.sort(comp);

		return cList;
	}

	private Comparator<Property> getPropertyComparator() {
		if (propertyComparator == null) {
			propertyComparator = new Comparator<>() {
				@Override
				public int compare(Property p1, Property p2) {
					return p1.getKey().compareTo(p2.getKey());
				}
			};
		}

		return propertyComparator;
	}

	/**
	 * Makes string representation of snapshot and appends it to provided string builder.
	 * <p>
	 * Note: Custom internal use snapshot property named {@code 'JMX_SNAP_NAME'} is ignored.
	 * <p>
	 * In case snapshot properties have same key for "branch" and "leaf" nodes at same path level, than "leaf" node
	 * property key value is appended by configuration defined (cfg. key {@code "DuplicateKeySuffix"}, default value
	 * {@value #UNIQUE_SUFFIX}) suffix.
	 *
	 * @param nvString
	 *            string builder instance to append
	 * @param snap
	 *            snapshot instance to represent as string
	 * @return appended string builder reference
	 *
	 * @see #getUniquePropertyKey(String, com.jkoolcloud.tnt4j.core.Property[], int)
	 */
	@Override
	protected StringBuilder toString(StringBuilder nvString, Snapshot snap) {
		Collection<Property> list = getProperties(snap);
		Property[] pArray = list.toArray(new Property[list.size()]);
		String sName = getSnapName(snap);
		for (int i = 0; i < pArray.length; i++) {
			Property p = pArray[i];
			if (p.isTransient()) {
				continue;
			}

			String pKey = getUniquePropertyKey(p.getKey(), pArray, i);
			Object value = p.getValue();

			if (addAPValueTypePrefix) {
				nvString.append(getAPValueType(value));
			}
			nvString.append(getKeyStr(sName, pKey));
			formatValue(nvString, value, FIELD_SEP);
		}
		return nvString;
	}

	/**
	 * Gets property key value and makes it to be unique on same path level among all array properties.
	 * <p>
	 * In case of duplicate keys uniqueness is made by adding configuration defined (cfg. key
	 * {@code "DuplicateKeySuffix"}, default value {@value #UNIQUE_SUFFIX}) suffix to property key value.
	 *
	 * @param pKey
	 *            property key value
	 * @param pArray
	 *            properties array
	 * @param pIdx
	 *            property index in array
	 * @return unique property key value
	 */
	protected String getUniquePropertyKey(String pKey, Property[] pArray, int pIdx) {
		String ppKey;
		for (int i = pIdx + 1; i < pArray.length; i++) {
			ppKey = pArray[i].getKey();

			if (ppKey.startsWith(pKey + PATH_DELIM)) {
				pKey += uniqueSuffix;
			}
		}

		return pKey;
	}

	@Override
	protected String getSnapNameStr(String objCanonName) {
		if (StringUtils.isEmpty(objCanonName)) {
			return objCanonName;
		}

		int ddIdx = objCanonName.indexOf(':');

		if (ddIdx == -1) {
			return objCanonName;
		}

		Properties objNameProps = new Properties();
		loadProps(objNameProps, objCanonName, ddIdx);

		return getObjNameStr(objNameProps);
	}

	@Override
	protected String getSnapNameStr(ObjectName objName) {
		if (objName == null) {
			return "null";
		}

		Map<String, String> objNameProps = getKeyPropertyList(objName);

		return getObjNameStr(objNameProps);
	}

	/**
	 * Creates key object name properties map.
	 * 
	 * @param objName
	 *            object name instance
	 * @return key object name properties map
	 */
	protected Map<String, String> getKeyPropertyList(ObjectName objName) {
		Map<String, String> objNameProps = new LinkedHashMap<>(5);
		objNameProps.put("domain", objName.getDomain()); // NON-NLS

		String objNamePropsStr = objName.getKeyPropertyListString();
		String[] objNamePropsStrings = objNamePropsStr.split(FIELD_SEP);

		for (String prop : objNamePropsStrings) {
			String[] kv = prop.split(EQ);
			objNameProps.put(kv[0].trim(), kv.length > 1 ? kv[1] : "");
		}

		return objNameProps;
	}

	/**
	 * Builds object name string from provided object name properties.
	 * 
	 * @param objNameProps
	 *            object name properties map
	 * @return object name string built from provided properties
	 */
	protected String getObjNameStr(Map<?, ?> objNameProps) {
		StringBuilder pathBuilder = new StringBuilder(128);
		String pv;

		if (pathLevelAttrKeys != null) {
			for (String[] levelAttrKeys : pathLevelAttrKeys) {
				for (String pKey : levelAttrKeys) {
					pv = (String) objNameProps.remove(pKey);
					appendPath(pathBuilder, pv);
				}
			}
		}

		if (!objNameProps.isEmpty()) {
			for (Map.Entry<?, ?> pe : objNameProps.entrySet()) {
				pv = String.valueOf(pe.getValue());
				appendPath(pathBuilder, pv);
			}
		}

		return pathBuilder.toString();
	}

	/**
	 * Resolves properties map from provided canonical object name string and puts into provided {@link Properties}
	 * instance.
	 *
	 * @param props
	 *            properties to load into
	 * @param objCanonName
	 *            object canonical name string
	 * @param ddIdx
	 *            domain separator index
	 */
	protected static void loadProps(Properties props, String objCanonName, int ddIdx) {
		String domainStr = objCanonName.substring(0, ddIdx);

		props.clear();
		props.setProperty("domain", domainStr);

		String propsStr = objCanonName.substring(ddIdx + 1);

		if (!propsStr.isEmpty()) {
			propsStr = Utils.replace(propsStr, FIELD_SEP, LF);
			Reader rdr = new StringReader(propsStr);
			try {
				props.load(rdr);
			} catch (IOException exc) {
			} finally {
				Utils.close(rdr);
			}
		}
	}

	/**
	 * Appends provided path string builder with path token string.
	 *
	 * @param pathBuilder
	 *            path string builder to append
	 * @param pathToken
	 *            path token string
	 * @return appended path string builder instance
	 */
	protected StringBuilder appendPath(StringBuilder pathBuilder, String pathToken) {
		if (StringUtils.isNotEmpty(pathToken)) {
			pathBuilder.append(pathBuilder.length() > 0 ? PATH_DELIM : "").append(pathToken);
		}

		return pathBuilder;
	}

	@Override
	public void setConfiguration(Map<String, ?> settings) {
		super.setConfiguration(settings);

		uniqueSuffix = Utils.getString("DuplicateKeySuffix", settings, uniqueSuffix);

		String pValue = Utils.getString("PathLevelAttributes", settings, "");
		if (StringUtils.isNotEmpty(pValue)) {
			initPathLevelAttrKeys(pValue);
		}
	}

	private void initPathLevelAttrKeys(String levelsStr) {
		List<List<String>> levelList = new ArrayList<>();
		List<String> attrsList;

		String[] levels = levelsStr.split(";");

		for (String level : levels) {
			level = level.trim();

			if (!level.isEmpty()) {
				String[] levelAttrs = level.split(",");
				attrsList = new ArrayList<>(levelAttrs.length);

				for (String lAttr : levelAttrs) {
					lAttr = lAttr.trim();

					if (!lAttr.isEmpty()) {
						attrsList.add(lAttr);
					}
				}

				if (!attrsList.isEmpty()) {
					levelList.add(attrsList);
				}
			}
		}

		pathLevelAttrKeys = new String[levelList.size()][];
		String[] levelAttrs;
		int i = 0;
		for (List<String> level : levelList) {
			levelAttrs = level.toArray(new String[level.size()]);

			pathLevelAttrKeys[i++] = levelAttrs;
		}
	}
}
