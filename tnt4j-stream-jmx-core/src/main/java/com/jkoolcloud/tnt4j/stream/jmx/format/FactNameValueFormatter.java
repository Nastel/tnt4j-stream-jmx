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
package com.jkoolcloud.tnt4j.stream.jmx.format;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.ObjectName;

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

	private static final String SNAP_NAME_PROP = "JMX_SNAP_NAME";

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

		Snapshot selfSnapshot = getSelfSnapshot(event.getOperation());
		if (event.getTag() != null) {
			Set<String> tags = event.getTag();
			if (!tags.isEmpty()) {
				selfSnapshot.add("tag", tags);
			}
		}

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

		Snapshot selfSnapshot = getSelfSnapshot(activity);
		selfSnapshot.add("id.count", activity.getIdCount());

		activity.addSnapshot(selfSnapshot);

		Collection<Snapshot> sList = getSnapshots(activity);
		for (Snapshot snap : sList) {
			toString(nvString, snap);
		}

		return nvString.append(END_SEP).toString();
	}

	private Snapshot getSelfSnapshot(Operation op) {
		Snapshot selfSnapshot = new PropertySnapshot("Self");

		if (op.getCorrelator() != null) {
			Set<String> cids = op.getCorrelator();
			if (!cids.isEmpty()) {
				selfSnapshot.add("corrid", cids);
			}
		}
		if (op.getUser() != null) {
			selfSnapshot.add("user", op.getUser());
		}
		if (op.getLocation() != null) {
			selfSnapshot.add("location", op.getLocation());
		}
		selfSnapshot.add("level", op.getSeverity());
		selfSnapshot.add("pid", op.getPID());
		selfSnapshot.add("tid", op.getTID());
		selfSnapshot.add("snap.count", op.getSnapshotCount());
		selfSnapshot.add("elapsed.usec", op.getElapsedTimeUsec());

		return selfSnapshot;
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
	 * @see Utils#replace(String, Map)
	 */
	protected String getSourceNameStr(String sourceName) {
		// return replace(sourceName, FIELD_SEP, FS_REP);

		return Utils.replace(sourceName, keyReplacements);
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
			if (p.isTransient()) {
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

	private String getSnapNameStr(Object nameObj) {
		if (nameObj instanceof ObjectName) {
			return getSnapNameStr((ObjectName) nameObj);
		}

		return getSnapNameStr(String.valueOf(nameObj));
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
			Property pObjName = Utils.getSnapPropertyIgnoreCase(snap, Utils.OBJ_NAME_PROP);
			String snapNameStr = pObjName == null ? getSnapNameStr(snap.getName())
					: getSnapNameStr(pObjName.getValue());
			pSnapName = new Property(SNAP_NAME_PROP, snapNameStr, true);

			snap.add(pSnapName);
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
	 * @see Utils#replace(String, Map)
	 */
	protected String getKeyStr(String sName, String pKey) {
		String keyStr = sName + PATH_DELIM + pKey;

		return Utils.replace(keyStr, keyReplacements);
	}

	/**
	 * Makes decorated string representation of argument attribute value.
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
	 * @see com.jkoolcloud.tnt4j.utils.Utils#toString(Object)
	 * @see #initDefaultValueReplacements()
	 * @see Utils#replace(String, Map)
	 */
	protected String getValueStr(Object value) {
		String valStr = Utils.toString(value);

		return Utils.replace(valStr, valueReplacements);
	}

	@Override
	public void setConfiguration(Map<String, Object> settings) {
		super.setConfiguration(settings);

		String pValue = Utils.getString("KeyReplacements", settings, "");
		if (StringUtils.isEmpty(pValue)) {
			initDefaultKeyReplacements();
		} else {
			Utils.parseReplacements(pValue, keyReplacements);
		}

		pValue = Utils.getString("ValueReplacements", settings, "");
		if (StringUtils.isEmpty(pValue)) {
			initDefaultValueReplacements();
		} else {
			Utils.parseReplacements(pValue, valueReplacements);
		}

		uniqueSuffix = Utils.getString("DuplicateKeySuffix", settings, uniqueSuffix);
	}

	/**
	 * Initializes default set symbol replacements for a attribute keys.
	 * <p>
	 * Default keys string replacements mapping is:
	 * <ul>
	 * <li>{@code " "} to {@code "_"}</li>
	 * <li>{@code "\""} to {@code "'"}</li>
	 * <li>{@code "/"} to {@code "%"}</li>
	 * </ul>
	 */
	protected void initDefaultKeyReplacements() {
		keyReplacements.put(" ", "_");
		keyReplacements.put("\"", "'");
		keyReplacements.put("/", "%");
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
}
