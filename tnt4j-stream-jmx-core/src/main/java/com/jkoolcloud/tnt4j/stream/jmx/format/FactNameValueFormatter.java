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

import java.util.*;

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
 * @version $Revision: 2 $
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

	private static final String SELF_SNAP_NAME = "Self";
	private static final String SELF_SNAP_ID = SELF_SNAP_NAME + "@" + PropertySnapshot.CATEGORY_DEFAULT;

	private static final String SNAP_NAME_PROP = "JMX_SNAP_NAME";

	/**
	 * Mapping of attribute key string symbol replacements.
	 */
	protected Map<String, String> keyReplacements = new LinkedHashMap<>();
	/**
	 * Mapping of attribute value string symbol replacements.
	 */
	protected Map<String, String> valueReplacements = new LinkedHashMap<>();

	private boolean addSelfSnapshot = true;
	/**
	 * Flag indicating whether to add AutoPilot fact value type prefixes for fact names. Default is {@code false}.
	 */
	protected boolean addAPValueTypePrefix = false;

	public FactNameValueFormatter() {
		super("time.stamp={2},level={1},source={3},msg=\"{0}\"");

		// adding mandatory value symbols replacements
		valueReplacements.put(CR, "\\r");
		valueReplacements.put(LF, "\\n");
	}

	@Override
	public String format(TrackingEvent event) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:");
		// ------------------------------------------------------------- name
		toString(nvString, event.getSource()).append(PATH_DELIM).append(event.getName()).append(PATH_DELIM)
				.append("Events").append(FIELD_SEP);

		if (addSelfSnapshot && event.getOperation().getSnapshot(SELF_SNAP_ID) == null) {
			Snapshot selfSnapshot = getSelfSnapshot(event.getOperation());
			if (event.getTag() != null) {
				Set<String> tags = event.getTag();
				if (!tags.isEmpty()) {
					selfSnapshot.add("tag", tags);
				}
			}

			event.getOperation().addSnapshot(selfSnapshot);
		}

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

		nvString.append("OBJ:");
		toString(nvString, activity.getSource()).append(PATH_DELIM).append("Activities").append(FIELD_SEP);

		if (addSelfSnapshot && activity.getSnapshot(SELF_SNAP_ID) == null) {
			Snapshot selfSnapshot = getSelfSnapshot(activity);
			selfSnapshot.add("id.count", activity.getIdCount());

			activity.addSnapshot(selfSnapshot);
		}

		Collection<Snapshot> sList = getSnapshots(activity);
		for (Snapshot snap : sList) {
			toString(nvString, snap);
		}

		return nvString.append(END_SEP).toString();
	}

	private Snapshot getSelfSnapshot(Operation op) {
		Snapshot selfSnapshot = new PropertySnapshot(SELF_SNAP_NAME);

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

		// ------------------------------------------------------ category, id or name
		nvString.append("OBJ:");
		toString(nvString, snapshot.getSource()).append(PATH_DELIM).append(snapshot.getCategory()).append(FIELD_SEP);
		toString(nvString, snapshot).append(END_SEP);

		return nvString.toString();
	}

	@Override
	public String format(long ttl, Source source, OpLevel level, String msg, Object... args) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:");
		toString(nvString, source).append(PATH_DELIM).append("Message").append(FIELD_SEP);
		nvString.append(SELF_SNAP_NAME).append(PATH_DELIM).append("level");
		formatValue(nvString, level, FIELD_SEP);
		nvString.append(SELF_SNAP_NAME).append(PATH_DELIM).append("msg-text");
		formatValue(nvString, Utils.format(msg, args), END_SEP);
		return nvString.toString();
	}

	/**
	 * Formats provided {@code value} and appends to provided string builder.
	 * 
	 * @param nvString
	 *            string builder instance to append
	 * @param value
	 *            value to append
	 * @param sep
	 *            fields separator symbol
	 * @return string builder instance
	 */
	protected StringBuilder formatValue(StringBuilder nvString, Object value, String sep) {
		return nvString.append(EQ).append(getValueStr(value)).append(sep);
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
		return snap.getProperties();
	}

	/**
	 * Makes string representation of snapshot and appends it to provided string builder.
	 *
	 * @param nvString
	 *            string builder instance to append
	 * @param snap
	 *            snapshot instance to represent as string
	 * @return appended string builder reference
	 */
	protected StringBuilder toString(StringBuilder nvString, Snapshot snap) {
		Collection<Property> list = getProperties(snap);
		String sName = getSnapName(snap);
		for (Property p : list) {
			if (p.isTransient()) {
				continue;
			}

			String pKey = p.getKey();
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
	 * Returns provided value AutoPilot type string to prefix fact name.
	 * 
	 * @param value
	 *            value to determine type
	 * @return AutoPilot value type prefix string
	 */
	protected String getAPValueType(Object value) {
		if (value instanceof Integer) {
			return "$I$";
		}
		if (value instanceof Long) {
			return "$L$";
		}
		if (value instanceof Float) {
			return "$F$";
		}
		if (value instanceof Double) {
			return "$D$";
		}
		if (value instanceof Boolean) {
			return "$B$";
		}
		if (value instanceof String) {
			return "$S$";
		}
		if (value instanceof Date) {
			return "$T$";
		}
		// if (value instanceof Date) {
		// return "$M$"; //timestamp numeric long
		// }
		if (value instanceof byte[]) {
			return "$X$";
		}

		return "$V$";
	}

	/**
	 * Makes decorated string representation of snapshot name.
	 * <p>
	 * Snapshot name string gets symbols replaced using ones defined in {@link #keyReplacements} map.
	 * 
	 * @param snapName
	 *            snapshot name
	 * @return decorated string representation of snapshot name
	 */
	protected String getSnapNameStr(String snapName) {
		return Utils.replace(snapName, keyReplacements);
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
			Property pObjName = Utils.getSnapPropertyIgnoreCase(snap, Utils.OBJ_NAME_OBJ_PROP);
			String snapNameStr = isEmpty(pObjName) ? getSnapNameStr(snap.getName())
					: getSnapNameStr(pObjName.getValue());
			pSnapName = new Property(SNAP_NAME_PROP, snapNameStr, true);

			snap.add(pSnapName);
		}

		return (String) pSnapName.getValue();
	}

	private boolean isEmpty(Property p) {
		return p == null || p.getValue() == null;
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
	 * @see com.jkoolcloud.tnt4j.utils.Utils#base64EncodeStr(byte[])
	 * @see com.jkoolcloud.tnt4j.utils.Utils#toString(Object)
	 * @see #initDefaultValueReplacements()
	 * @see Utils#replace(String, Map)
	 */
	protected String getValueStr(Object value) {
		String valStr;
		if (value instanceof byte[]) {
			valStr = Utils.base64EncodeStr((byte[]) value);
		} else {
			valStr = Utils.toString(value);
		}

		return Utils.replace(valStr, valueReplacements);
	}

	@Override
	public void setConfiguration(Map<String, ?> settings) {
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

		addSelfSnapshot = com.jkoolcloud.tnt4j.utils.Utils.getBoolean("AddSelfSnapshot", settings, addSelfSnapshot);
		addAPValueTypePrefix = com.jkoolcloud.tnt4j.utils.Utils.getBoolean("AddAPValueTypePrefix", settings,
				addAPValueTypePrefix);
	}

	/**
	 * Initializes default set symbol replacements for an attribute keys.
	 * <p>
	 * Default keys string replacements mapping is:
	 * <ul>
	 * <li>{@code " "} to {@code "_"}</li>
	 * <li>{@code "\""} to {@code "'"}</li>
	 * <li>{@code "/"} to {@code "%"}</li>
	 * <li>{@value #EQ} to {@value #PATH_DELIM}</li>
	 * <li>{@value #FIELD_SEP} to {@value #FS_REP}</li>
	 * </ul>
	 */
	protected void initDefaultKeyReplacements() {
		keyReplacements.put(" ", "_");
		keyReplacements.put("\"", "'");
		keyReplacements.put("/", "%");
		keyReplacements.put(EQ, PATH_DELIM);
		keyReplacements.put(FIELD_SEP, FS_REP);
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
