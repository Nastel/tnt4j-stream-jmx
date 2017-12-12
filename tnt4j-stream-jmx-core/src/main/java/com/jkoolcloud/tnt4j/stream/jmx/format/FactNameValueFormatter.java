/*
 * Copyright 2015-2017 JKOOL, LLC.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.ObjectName;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.*;
import com.jkoolcloud.tnt4j.format.DefaultFormatter;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;
import com.jkoolcloud.tnt4j.tracker.TrackingActivity;
import com.jkoolcloud.tnt4j.tracker.TrackingEvent;

/**
 * This class provides key/value formatting for tnt4j activities, events and snapshots. The output format follows the
 * following format:
 * <p>
 * {@code "OBJ:name-value-prefix,name1=value1,....,nameN=valueN"}.
 * </p>
 * Newline is added at the end of each line.
 *
 * @version $Revision: 1 $
 * 
 * @see com.jkoolcloud.tnt4j.stream.jmx.scheduler.SchedulerImpl
 */
public class FactNameValueFormatter extends DefaultFormatter {
	public static final String LF = "\n";
	public static final String CR = "\r";
	public static final String FIELD_SEP = ",";
	public static final String END_SEP = LF;
	public static final String PATH_DELIM = "\\";
	public static final String EQ = "=";
	public static final String FS_REP = "!";
	public static final String UNIQUE_SUFFIX = "_";

	private String[] replaceable = new String[] { EQ, FIELD_SEP };
	private String[] replacement = new String[] { PATH_DELIM, FS_REP };

	private static final String SNAP_NAME_PROP = Utils.makeInternalPropKey("JMX_SNAP_NAME");

	// NOTE: group 1 - original, group 2 - replacement
	// protected static final Pattern REP_CFG_PATTERN = Pattern.compile("\"(\\s*[^\"]+\\s*)\"->\"(\\s*[^\"]+\\s*)\"");
	// NOTE: group 1 - original, group 3 - replacement. Properly handles escaped quotes within quotes.
	protected static final Pattern REP_CFG_PATTERN = Pattern
			.compile("\"(\\s*([^\"\\\\]|\\\\.)+\\s*)\"->\"(\\s*([^\"\\\\]|\\\\.)+\\s*)\"");

	protected boolean serializeSimpleTypesOnly = false;
	protected String uniqueSuffix = UNIQUE_SUFFIX;

	protected Map<String, String> keyReplacements = new HashMap<String, String>();
	protected Map<String, String> valueReplacements = new HashMap<String, String>();

	public FactNameValueFormatter() {
		super("time.stamp={2},level={1},source={3},msg=\"{0}\"");

		// adding mandatory value symbols replacements
		valueReplacements.put(CR, "\\r");
		valueReplacements.put(LF, "\\n");
	}

	@Override
	public String format(TrackingEvent event) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:Streams");
		toString(nvString, event.getSource()).append(event.getName()).append("\\Events").append(FIELD_SEP);

		Snapshot selfSnapshot = new PropertySnapshot("Self");

		if (event.getCorrelator() != null) {
			Set<String> cids = event.getCorrelator();
			if (!cids.isEmpty()) {
				selfSnapshot.add("corrid", cids);
			}
		}
		if (event.getTag() != null) {
			Set<String> tags = event.getTag();
			if (!tags.isEmpty()) {
				selfSnapshot.add("tag", tags);
			}
		}
		if (event.getOperation().getUser() != null) {
			selfSnapshot.add("user", event.getOperation().getUser());
		}
		if (event.getLocation() != null) {
			selfSnapshot.add("location", event.getLocation());
		}
		selfSnapshot.add("level", event.getOperation().getSeverity());
		selfSnapshot.add("pid", event.getOperation().getPID());
		selfSnapshot.add("tid", event.getOperation().getTID());
		selfSnapshot.add("elapsed.usec", event.getOperation().getElapsedTimeUsec());

		event.getOperation().addSnapshot(selfSnapshot);

		Collection<Snapshot> sList = getSnapshots(event.getOperation());
		for (Snapshot snap : sList) {
			toString(nvString, snap);
		}

		return nvString.append(END_SEP).toString();
	}

	/**
	 * Returns operation contained snapshots collection.
	 * 
	 * @param op
	 *            operation instance
	 * @return collection of operation snapshots
	 */
	protected Collection<Snapshot> getSnapshots(Operation op) {
		return op.getSnapshots();
	}

	@Override
	public String format(TrackingActivity activity) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:Streams");
		toString(nvString, activity.getSource()).append("\\Activities").append(FIELD_SEP);

		Snapshot selfSnapshot = new PropertySnapshot("Self");

		if (activity.getCorrelator() != null) {
			Set<String> cids = activity.getCorrelator();
			if (!cids.isEmpty()) {
				selfSnapshot.add("corrid", cids);
			}
		}
		if (activity.getUser() != null) {
			selfSnapshot.add("user", activity.getUser());
		}
		if (activity.getLocation() != null) {
			selfSnapshot.add("location", activity.getLocation());
		}
		selfSnapshot.add("level", activity.getSeverity());
		selfSnapshot.add("id.count", activity.getIdCount());
		selfSnapshot.add("pid", activity.getPID());
		selfSnapshot.add("tid", activity.getTID());
		selfSnapshot.add("snap.count", activity.getSnapshotCount());
		selfSnapshot.add("elapsed.usec", activity.getElapsedTimeUsec());

		activity.addSnapshot(selfSnapshot);

		Collection<Snapshot> sList = getSnapshots(activity);
		for (Snapshot snap : sList) {
			toString(nvString, snap);
		}

		return nvString.append(END_SEP).toString();
	}

	@Override
	public String format(Snapshot snapshot) {
		StringBuilder nvString = new StringBuilder(1024);

		String prefix = "OBJ:Metrics\\" + snapshot.getCategory();
		nvString.append(prefix).append(FIELD_SEP);
		toString(nvString, snapshot).append(END_SEP);

		return nvString.toString();
	}

	@Override
	public String format(long ttl, Source source, OpLevel level, String msg, Object... args) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:Streams");
		toString(nvString, source).append("\\Message").append(FIELD_SEP);
		nvString.append("Self\\level=").append(getValueStr(level)).append(FIELD_SEP);
		nvString.append("Self\\msg-text=");
		Utils.quote(Utils.format(msg, args), nvString).append(END_SEP);
		return nvString.toString();
	}

	/**
	 * Makes string representation of source and appends it to provided string builder.
	 * 
	 * @param nvString
	 *            string builder instance to append
	 * @param source
	 *            source instance to represent as string
	 * @return appended string builder reference
	 */
	protected StringBuilder toString(StringBuilder nvString, Source source) {
		Source parent = source.getSource();
		if (parent != null) {
			toString(nvString, parent);
		}
		nvString.append(PATH_DELIM).append(getSourceNameStr(source.getName()));
		return nvString;
	}

	/**
	 * Makes decorated string representation of source name.
	 * <p>
	 * Source name representation string gets symbols replaced using ones defined in {@link #keyReplacements} map.
	 *
	 * @param sourceName
	 *            source name
	 * @return decorated string representation of source name
	 *
	 * @see #replace(String, Map)
	 */
	protected String getSourceNameStr(String sourceName) {
		// return replace(sourceName, FIELD_SEP, FS_REP);

		return replace(sourceName, keyReplacements);
	}

	/**
	 * Performs provided string contents replacement using symbols defined in {@code replacements} map.
	 *
	 * @param str
	 *            string to apply replacements
	 * @param replacements
	 *            replacements map
	 * @return string having applied replacements
	 *
	 * @see #replace(String, String, String)
	 */
	protected static String replace(String str, Map<String, String> replacements) {
		if (StringUtils.isNotEmpty(str)) {
			for (Map.Entry<String, String> kre : replacements.entrySet()) {
				str = replace(str, kre.getKey(), kre.getValue());
			}
		}

		return str;
	}

	/**
	 * Returns snapshot contained properties collection.
	 * 
	 * @param snap
	 *            snapshot instance
	 * @return collection of snapshot properties
	 */
	protected Collection<Property> getProperties(Snapshot snap) {
		return snap.getSnapshot();
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
	protected StringBuilder toString(StringBuilder nvString, Snapshot snap) {
		Collection<Property> list = getProperties(snap);
		Property[] pArray = new Property[list.size()];
		pArray = list.toArray(pArray);
		String sName = getSnapName(snap);
		for (int i = 0; i < pArray.length; i++) {
			Property p = pArray[i];
			if (p.getKey().startsWith(Utils.INTERNAl_PROP_PREFIX)) {
				continue;
			}

			String pKey = getUniquePropertyKey(p.getKey(), pArray, i);
			Object value = p.getValue();

			nvString.append(getKeyStr(sName, pKey));
			nvString.append(EQ).append(getValueStr(value)).append(FIELD_SEP);
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

	/**
	 * Determine if a given value can be meaningfully serialized to string.
	 *
	 * @param value
	 *            value to test for serialization
	 * @return {@code true} if a given value can be serialized to string meaningfully, {@code false} - otherwise
	 */
	protected static boolean isSerializable(Object value) {
		return value == null || value.getClass().isPrimitive() || value.getClass().isEnum() || value instanceof String
				|| value instanceof Number || value instanceof Boolean || value instanceof Character;

	}

	/**
	 * Makes decorated string representation of snapshot name.
	 * <p>
	 * Replaces {@value #EQ} to {@value #PATH_DELIM} and {@value #FIELD_SEP} to {@value #FS_REP}.
	 * 
	 * @param snapName
	 *            snapshot name
	 * @return decorated string representation of snapshot name
	 */
	protected String getSnapNameStr(String snapName) {
		return StringUtils.replaceEach(snapName, replaceable, replacement);
	}

	/**
	 * Makes decorated string representation of snapshot name by referenced object name using
	 * {@link ObjectName#getCanonicalName()}.
	 *
	 * @param objName
	 *            object name
	 * @return decorated string representation of snapshot name
	 *
	 * @see #getSnapNameStr(String)
	 */
	protected String getSnapNameStr(ObjectName objName) {
		return getSnapNameStr(objName.getCanonicalName());
	}

	/**
	 * Makes decorated string representation of {@link Snapshot} name and puts it as snapshot property
	 * {@code 'JMX_SNAP_NAME'} for a later use.
	 *
	 * @param snap
	 *            snapshot instance
	 * @return decorated string representation of snapshot name
	 *
	 * @see #getSnapNameStr(String)
	 */
	protected String getSnapName(Snapshot snap) {
		Property pSnapName = snap.get(SNAP_NAME_PROP);
		if (pSnapName == null) {
			Property objName = Utils.getSnapPropertyIgnoreCase(snap, Utils.OBJ_NAME_PROP);
			if (objName != null) {
				pSnapName = new Property(SNAP_NAME_PROP, getSnapNameStr((ObjectName) objName.getValue()));
			} else {
				pSnapName = new Property(SNAP_NAME_PROP, getSnapNameStr(snap.getName()));
			}

		}

		return (String) pSnapName.getValue();
	}

	/**
	 * Makes decorated string representation of argument attribute key.
	 * <p>
	 * Key representation string gets symbols replaced using ones defined in {@link #keyReplacements} map.
	 * 
	 * @param sName
	 *            snapshot name
	 * @param pKey
	 *            property key
	 * @return decorated string representation of attribute key
	 *
	 * @see #initDefaultKeyReplacements()
	 * @see #replace(String, Map)
	 */
	protected String getKeyStr(String sName, String pKey) {
		String keyStr = sName + PATH_DELIM + pKey;

		return replace(keyStr, keyReplacements);
	}

	/**
	 * Makes decorated string representation of argument attribute value.
	 * <p>
	 * If property {@link #serializeSimpleTypesOnly} is set to {@code true} - validates if value can be represented as
	 * simple type. If no, then actual value is replaced by dummy string {@code "<unsupported value type>"}.
	 * <p>
	 * Value representation string containing {@code "\n"} or {@code "\r"} symbols gets those replaced by escaped
	 * representations {@code "\\n"} amd {@code "\\r"}.
	 * <p>
	 * Value representation string gets symbols replaced using ones defined in {@link #valueReplacements} map.
	 *
	 * @param value
	 *            attribute value
	 * @return decorated string representation of attribute value
	 *
	 * @see #toString(Object)
	 * @see #initDefaultValueReplacements()
	 * @see #replace(String, Map)
	 */
	protected String getValueStr(Object value) {
		String valStr;

		if (serializeSimpleTypesOnly && !isSerializable(value)) {
			valStr = value == null ? "<null>" : "<unsupported value type>";// : " + value.getClass() + ">";
			// System.out.println("Unsupported value type=" + (value == null ? null : value.getClass()) + " value="
			// + toString(value));
		} else {
			valStr = toString(value);
		}

		return replace(valStr, valueReplacements);
	}

	/**
	 * Makes string representation of argument attribute value.
	 * 
	 * @param value
	 *            attribute value
	 * @return string representation of attribute value
	 */
	protected String toString(Object value) {
		if (value instanceof Collection) {
			Collection<?> c = (Collection<?>) value;
			Object[] dca = c.toArray();

			value = dca[0];
		}

		return String.valueOf(value);
	}

	@Override
	public void setConfiguration(Map<String, Object> settings) {
		super.setConfiguration(settings);

		serializeSimpleTypesOnly = Utils.getBoolean("SerializeSimplesOnly", settings, serializeSimpleTypesOnly);

		String pValue = Utils.getString("KeyReplacements", settings, "");
		if (StringUtils.isEmpty(pValue)) {
			initDefaultKeyReplacements();
		} else {
			Matcher m = REP_CFG_PATTERN.matcher(pValue);

			while (m.find()) {
				keyReplacements.put(StringEscapeUtils.unescapeJava(m.group(1)),
						StringEscapeUtils.unescapeJava(m.group(3)));
			}
		}

		pValue = Utils.getString("ValueReplacements", settings, "");
		if (StringUtils.isEmpty(pValue)) {
			initDefaultValueReplacements();
		} else {
			Matcher m = REP_CFG_PATTERN.matcher(pValue);

			while (m.find()) {
				valueReplacements.put(StringEscapeUtils.unescapeJava(m.group(1)),
						StringEscapeUtils.unescapeJava(m.group(3)));
			}
		}

		uniqueSuffix = Utils.getString("DuplicateKeySuffix", settings, uniqueSuffix);
	}

	/**
	 * Initializes default set symbol replacements for a attribute keys.
	 *
	 * <p>
	 * Default keys string replacements mapping is:
	 * <ul>
	 * <li>{@code " "} to {@code "_"}</li>
	 * <li>{@code "\""} to {@code "'"}</li>
	 * </ul>
	 */
	protected void initDefaultKeyReplacements() {
		keyReplacements.put(" ", "_");
		keyReplacements.put("\"", "'");
	}

	/**
	 * Initializes default set symbol replacements for a attribute values.
	 * <p>
	 * Default value string replacements mapping is:
	 * <ul>
	 * <li>{@code ";"} to {@code "|"}</li>
	 * <li>{@code ","} to {@code "|"}</li>
	 * <li>{@code "["} to {@code "{("}</li>
	 * <li>{@code "["} to {@code ")}"}</li>
	 * <li>{@code "\""} to {@code "'"}</li>
	 * </ul>
	 */
	protected void initDefaultValueReplacements() {
		valueReplacements.put(";", "|");
		valueReplacements.put(",", "|");
		valueReplacements.put("[", "{(");
		valueReplacements.put("]", ")}");
		valueReplacements.put("\"", "'");
	}

	/**
	 * Performs source string contents replacement with provided new fragment.
	 *
	 * @param source
	 *            source string
	 * @param os
	 *            fragment to be replaced
	 * @param ns
	 *            replacement fragment
	 * @return string having replaced content
	 */
	public static String replace(String source, String os, String ns) {
		if (source == null) {
			return null;
		}
		int i = 0;
		if ((i = source.indexOf(os, i)) >= 0) {
			char[] sourceArray = source.toCharArray();
			char[] nsArray = ns.toCharArray();
			int oLength = os.length();
			StringBuilder buf = new StringBuilder(sourceArray.length);
			buf.append(sourceArray, 0, i).append(nsArray);
			i += oLength;
			int j = i;
			// Replace all remaining instances of oldString with newString.
			while ((i = source.indexOf(os, i)) > 0) {
				buf.append(sourceArray, j, i - j).append(nsArray);
				i += oLength;
				j = i;
			}
			buf.append(sourceArray, j, sourceArray.length - j);
			source = Utils.getString(buf);
		}
		return source;
	}
}
