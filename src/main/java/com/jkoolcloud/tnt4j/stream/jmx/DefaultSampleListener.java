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
package com.jkoolcloud.tnt4j.stream.jmx;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import com.jkoolcloud.tnt4j.core.Activity;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.UnsupportedAttributeException;
import com.jkoolcloud.tnt4j.stream.jmx.format.FactNameValueFormatter;

/**
 * <p>
 * This class provide a default implementation of a {@link SampleListener}
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see SampleListener
 */
public class DefaultSampleListener implements SampleListener {
	public static String STAT_TRACE_MODE = "listener.trace.mode";
	public static String STAT_EXCLUDE_SET_COUNT = "listener.exclude.set.count";

	boolean trace = false;
	PrintStream out;
	boolean validateTypes = true;

	HashSet<MBeanAttributeInfo> excAttrs = new HashSet<MBeanAttributeInfo>(89);

	/**
	 * Create an instance of {@code DefaultSampleListener} with a a given print stream and trace mode
	 * 
	 * @param pstream print stream instance for tracing
	 * @param trace mode
	 */
	public DefaultSampleListener(PrintStream pstream, boolean trace) {
		this(pstream, trace, true);
	}

	/**
	 * Create an instance of {@code DefaultSampleListener} with a a given print stream and trace mode
	 *
	 * @param pstream print stream instance for tracing
	 * @param trace mode
	 * @param validateTypes flag indicating if attribute value type validation required
	 */
	public DefaultSampleListener(PrintStream pstream, boolean trace, boolean validateTypes) {
		this.trace = trace;
		this.out = pstream == null ? System.out : pstream;
		this.validateTypes = validateTypes;
	}

	/**
	 * Determine if a given attribute to be excluded from sampling.
	 * 
	 * @param attr MBean attribute info
	 * @return true when attribute should be excluded, false otherwise
	 */
	protected boolean isExcluded(MBeanAttributeInfo attr) {
		return excAttrs.contains(attr);
	}

	/**
	 * Mark a given attribute to be excluded from sampling.
	 * 
	 * @param attr MBean attribute info
	 */
	protected void exclude(MBeanAttributeInfo attr) {
		excAttrs.add(attr);
	}

	@Override
	public void pre(SampleContext context, Activity activity) {
		if (trace) {
			out.println("Pre: " + activity.getName() 
					+ ": sample.count=" + context.getSampleCount() 
					+ ", mbean.count=" + getMBeanCount(context) 
					+ ", sample.mbeans.count=" + context.getMBeanCount()
					+ ", exclude.attr.set=" + excAttrs.size() 
					+ ", total.noop.count=" + context.getTotalNoopCount()
					+ ", total.exclude.count=" + context.getExcludeAttrCount() 
					+ ", total.error.count=" + context.getTotalErrorCount() 
					+ ", trackind.id=" + activity.getTrackingId() 
					+ ", mbean.server=" + context.getMBeanServer()
					);
		}
	}

	@Override
	public void pre(SampleContext context, AttributeSample sample) {
		sample.excludeNext(!sample.getAttributeInfo().isReadable() || isExcluded(sample.getAttributeInfo()));
	}

	@Override
	public void post(SampleContext context, AttributeSample sample) throws UnsupportedAttributeException {
		MBeanAttributeInfo jinfo = sample.getAttributeInfo();
		PropertySnapshot snapshot = sample.getSnapshot();
		processAttrValue(snapshot, jinfo, jinfo.getName(), sample.get());
	}

	@Override
	public void post(SampleContext context, Activity activity) {
		if (trace) {
			out.println("Post: " + activity.getName() 
				+ ": sample.count=" + context.getSampleCount() 
				+ ", mbean.count="+ getMBeanCount(context) 
				+ ", elapsed.usec=" + activity.getElapsedTimeUsec() 
				+ ", snap.count=" + activity.getSnapshotCount() 
				+ ", id.count=" + activity.getIdCount() 
				+ ", sample.mbeans.count=" + context.getMBeanCount() 
				+ ", sample.metric.count=" + context.getLastMetricCount()
				+ ", sample.time.usec=" + context.getLastSampleUsec() 
				+ ", exclude.attr.set=" + excAttrs.size()
				+ ", total.noop.count=" + context.getTotalNoopCount() 
				+ ", total.exclude.count=" + context.getExcludeAttrCount() 
				+ ", total.error.count=" + context.getTotalErrorCount()
				+ ", trackind.id=" + activity.getTrackingId() 
				+ ", mbean.server=" + context.getMBeanServer()
				);
		}
	}

	private static String getMBeanCount(SampleContext context) {
		try {
			return String.valueOf(context.getMBeanServer().getMBeanCount());
		} catch (IOException exc) {
			return exc.getLocalizedMessage();
		}
	}

	@Override
	public void error(SampleContext context, AttributeSample sample) {
		sample.excludeNext(true);
		if (trace) {
			out.println("Failed to sample: " + sample.getAttributeInfo() + ", exclude=" + sample.excludeNext() + ", ex=" + sample.getError());
			sample.getError().printStackTrace(out);
		}
		if (sample.excludeNext()) {
			exclude(sample.getAttributeInfo());
		}
	}

	@Override
	public void getStats(SampleContext context, Map<String, Object> stats) {
		stats.put(STAT_TRACE_MODE, trace);
		stats.put(STAT_EXCLUDE_SET_COUNT, excAttrs.size());
	}

	@Override
	public void register(SampleContext context, ObjectName oname) {
		if (trace) {
			out.println("Register mbean: " + oname + ", mbean.server=" + context.getMBeanServer());
		}
	}

	@Override
	public void unregister(SampleContext context, ObjectName oname) {
		if (trace) {
			out.println("Unregister mbean: " + oname + ", mbean.server=" + context.getMBeanServer());
		}
	}

	@Override
	public void error(SampleContext context, Throwable ex) {
		out.println("Unexpected error when sampling mbean.server=" + context.getMBeanServer());
		ex.printStackTrace(out);
	}

	/**
	 * Gets attributes type validation flag value.
	 * 
	 * @return {@code true} if attributes type validation is applied, {@code false} - otherwise
	 */
	public boolean isValidateTypes() {
		return validateTypes;
	}

	/**
	 * Sets attributes type validation flag value.
	 *
	 * @param validateTypes
	 *            flag indicating if attributes type validation shall be applied
	 */
	public void setValidateTypes(boolean validateTypes) {
		this.validateTypes = validateTypes;
	}

	/**
	 * Process/extract value from a given MBean attribute
	 * 
	 * @param snapshot
	 *            instance where extracted attribute is stored
	 * @param jinfo
	 *            attribute info
	 * @param propName
	 *            name to be assigned to given attribute value
	 * @param value
	 *            associated with attribute
	 * @throws UnsupportedAttributeException
	 *             if provided attribute not supported
	 * @return snapshot instance where all attributes are contained
	 */
	private PropertySnapshot processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo jinfo, String propName, Object value) throws UnsupportedAttributeException {
		if (value instanceof CompositeData) {
			CompositeData cdata = (CompositeData) value;
			Set<String> keys = cdata.getCompositeType().keySet();
			for (String key : keys) {
				Object cval = cdata.get(key);
				processAttrValue(snapshot, jinfo, propName + "\\" + key, cval);
			}
		} else if (value instanceof TabularData) {
			TabularData tdata = (TabularData) value;
			Collection<?> values = tdata.values();
			int row = 0;
			for (Object cval : values) {
				processAttrValue(snapshot, jinfo, propName + "\\" + (++row), cval);
			}
		} else if (typeSupported(value)) {
			snapshot.add(propName, value);
		} else {
			if (value != null) {
				throw new UnsupportedAttributeException("Unsupported type=" + value.getClass(), jinfo, value);
			}
		}
		return snapshot;
	}

	/**
	 * Determine if a given value and its type are supported.
	 * 
	 * @param value value to test for support
	 * @return {@code true} if a given value and its type are supported, {@code false} - otherwise
	 */
	protected boolean typeSupported(Object value) {
		return !validateTypes || FactNameValueFormatter.isSerializable(value);
	}
}