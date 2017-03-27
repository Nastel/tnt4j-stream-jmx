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

	HashSet<MBeanAttributeInfo> excAttrs = new HashSet<MBeanAttributeInfo>(89);

	/**
	 * Create an instance of {@code DefaultSampleListener} with a a given print stream and trace mode
	 *
	 * @param pStream print stream instance for tracing
	 * @param trace mode
	 */
	public DefaultSampleListener(PrintStream pStream, boolean trace) {
		this.trace = trace;
		this.out = pStream == null ? System.out : pStream;
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
					+ ", tracking.id=" + activity.getTrackingId() 
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
					+ ", mbean.count=" + getMBeanCount(context) 
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
					+ ", tracking.id=" + activity.getTrackingId() 
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
	public void register(SampleContext context, ObjectName oName) {
		if (trace) {
			out.println("Register mbean: " + oName + ", mbean.server=" + context.getMBeanServer());
		}
	}

	@Override
	public void unregister(SampleContext context, ObjectName oName) {
		if (trace) {
			out.println("Unregister mbean: " + oName + ", mbean.server=" + context.getMBeanServer());
		}
	}

	@Override
	public void error(SampleContext context, Throwable ex) {
		out.println("Unexpected error when sampling mbean.server=" + context.getMBeanServer());
		ex.printStackTrace(out);
	}

	/**
	 * Process/extract value from a given MBean attribute
	 *
	 * @param snapshot instance where extracted attribute is stored
	 * @param jinfo attribute info
	 * @param propName name to be assigned to given attribute value
	 * @param value associated with attribute
	 * @return snapshot instance where all attributes are contained
	 */
	private PropertySnapshot processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo jinfo, String propName, Object value) {
		if (value instanceof CompositeData) {
			CompositeData cdata = (CompositeData) value;
			Set<String> keys = cdata.getCompositeType().keySet();
			for (String key : keys) {
				Object cVal = cdata.get(key);
				processAttrValue(snapshot, jinfo, propName + "\\" + key, cVal);
			}
		} else if (value instanceof TabularData) {
			TabularData tData = (TabularData) value;
			Collection<?> values = tData.values();
			int row = 0;
			for (Object tVal : values) {
				processAttrValue(snapshot, jinfo, propName + "\\" + padNumber(++row), tVal);
			}
		} else {
			snapshot.add(propName, value);
		}
		return snapshot;
	}

	private static String padNumber(int idx) {
		return idx < 10 ? "0" + idx : String.valueOf(idx);
	}
}