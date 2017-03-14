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

import java.util.Collection;
import java.util.Map;

import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.core.Operation;
import com.jkoolcloud.tnt4j.core.Property;
import com.jkoolcloud.tnt4j.core.Snapshot;
import com.jkoolcloud.tnt4j.format.DefaultFormatter;
import com.jkoolcloud.tnt4j.source.Source;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.SchedulerImpl;
import com.jkoolcloud.tnt4j.tracker.TrackingActivity;
import com.jkoolcloud.tnt4j.tracker.TrackingEvent;
import com.jkoolcloud.tnt4j.utils.Utils;

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
 * @see SchedulerImpl
 */
public class FactNameValueFormatter extends DefaultFormatter {
	public static final String FIELD_SEP = ",";
	public static final String END_SEP = "\n";

	protected boolean quoteStringValue = false;

	public FactNameValueFormatter() {
		super("time.stamp={2},level={1},source={3},msg=\"{0}\"");
	}

	@Override
	public String format(TrackingEvent event) {
		StringBuilder nvString = new StringBuilder(1024);
		nvString.append("OBJ:Streams");
		toString(nvString, event.getSource()).append(event.getOperation().getName()).append("\\Events").append(FIELD_SEP);

		if (event.getCorrelator() != null) {
			Object[] cids = event.getCorrelator().toArray();
			if (cids.length > 0) {
				nvString.append("corrid=").append(cids[0]).append(FIELD_SEP);
			}
		}
		if (event.getTag() != null) {
			Object[] cids = event.getTag().toArray();
			if (cids.length > 0) {
				nvString.append("tag=").append(cids[0]).append(FIELD_SEP);
			}
		}
		if (event.getOperation().getUser() != null) nvString.append("user=").append(event.getOperation().getUser()).append(FIELD_SEP);
		if (event.getLocation() != null) nvString.append("Self\\location=").append(event.getLocation()).append(FIELD_SEP);
		nvString.append("Self\\level=").append(event.getOperation().getSeverity()).append(FIELD_SEP);
		nvString.append("Self\\pid=").append(event.getOperation().getPID()).append(FIELD_SEP);
		nvString.append("Self\\tid=").append(event.getOperation().getTID()).append(FIELD_SEP);
		nvString.append("Self\\tid=").append(event.getOperation().getTID()).append(FIELD_SEP);
		nvString.append("Self\\elapsed.usec=").append(event.getOperation().getElapsedTimeUsec()).append(FIELD_SEP);

		Collection<Snapshot> slist = getSnapshots(event.getOperation());
		for (Snapshot snap : slist) {
			toString(nvString, snap);
		}
		return nvString.append(END_SEP).toString();
	}

	protected Collection<Snapshot> getSnapshots(Operation op) {
		return op.getSnapshots();
	}

	@Override
	public String format(TrackingActivity event) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:Streams");
		toString(nvString, event.getSource()).append("\\Activities").append(FIELD_SEP);

		if (event.getCorrelator() != null) {
			Object[] cids = event.getCorrelator().toArray();
			if (cids.length > 0) {
				nvString.append("Self\\corrid=").append(cids[0]).append(FIELD_SEP);
			}
		}
		if (event.getUser() != null) nvString.append("user=").append(event.getUser()).append(FIELD_SEP);
		if (event.getLocation() != null) nvString.append("Self\\location=").append(event.getLocation()).append(FIELD_SEP);
		nvString.append("Self\\level=").append(event.getSeverity()).append(FIELD_SEP);
		nvString.append("Self\\id.count=").append(event.getIdCount()).append(FIELD_SEP);
		nvString.append("Self\\pid=").append(event.getPID()).append(FIELD_SEP);
		nvString.append("Self\\tid=").append(event.getTID()).append(FIELD_SEP);
		nvString.append("Self\\snap.count=").append(event.getSnapshotCount()).append(FIELD_SEP);
		nvString.append("Self\\elapsed.usec=").append(event.getElapsedTimeUsec()).append(FIELD_SEP);

		Collection<Snapshot> slist = getSnapshots(event);
		for (Snapshot snap : slist) {
			toString(nvString, snap);
		}

		return nvString.append(END_SEP).toString();
	}

	@Override
	public String format(Snapshot event) {
		StringBuilder nvString = new StringBuilder(1024);
		String prefix = "OBJ:Metrics\\" + event.getCategory();
		nvString.append(prefix).append(FIELD_SEP);
		toString(nvString, event).append(END_SEP);
		return nvString.toString();
	}

	@Override
	public String format(long ttl, Source source, OpLevel level, String msg, Object... args) {
		StringBuilder nvString = new StringBuilder(1024);

		nvString.append("OBJ:Streams");
		toString(nvString, source).append("\\Message").append(FIELD_SEP);
		nvString.append("Self\\level=").append(level).append(FIELD_SEP);
		nvString.append("Self\\msg-text=\"").append(Utils.format(msg, args)).append("\"").append(END_SEP);
		return nvString.toString();
	}

	protected StringBuilder toString(StringBuilder nvString, Source source) {
		Source parent = source.getSource();
		if (parent != null) {
			toString(nvString, source.getSource());
		}
		nvString.append("\\").append(source.getName());
		return nvString;
	}

	protected StringBuilder toString(StringBuilder nvString, Snapshot snap) {
		Collection<Property> list = snap.getSnapshot();
		for (Property p : list) {
			Object value = p.getValue();
			if (isSerializable(value)) {
				String name = snap.getName().replace("=", "\\").replace(",", "!");
				nvString.append(name).append("\\").append(p.getKey());
				nvString.append("=").append(getValueStr(value)).append(FIELD_SEP);
			}
		}
		return nvString;
	}

	/**
	 * Determine if a given value can be meaningfully serialized to string.
	 *
	 * @param value
	 *            value to test for serialization
	 * @return {@code true} if a given value can be serialized to string meaningfully, {@code false} - otherwise
	 */
	public static boolean isSerializable(Object value) {
		return (value != null && !value.getClass().isArray()) 
			&& (value.getClass().isPrimitive() || value instanceof String || value instanceof Number || value instanceof Boolean);
	}

	/**
	 * Makes decorated string representation of argument attribute value.
	 * <p>
	 * If {@link #quoteStringValue} is set to {@code true} - {@link String} types values are surrounded with {@code "}
	 * symbol.
	 *
	 * @param value
	 *            attribute value
	 * @return decorated string representation of attribute value
	 *
	 * @see #toString(Object)
	 */
	protected String getValueStr(Object value) {
		if (quoteStringValue && (value instanceof String)) {
			return Utils.quote((String) value);
		}

		return toString(value);
	}

	/**
	 * Makes string representation of argument attribute value.
	 * 
	 * @param value
	 *            attribute value
	 * @return string representation of attribute value
	 */
	protected String toString(Object value) {
		return String.valueOf(value);
	}

	@Override
	public void setConfiguration(Map<String, Object> settings) {
		super.setConfiguration(settings);

		quoteStringValue = Utils.getBoolean("QuoteStringValues", settings, quoteStringValue);
	}
}
