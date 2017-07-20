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
package com.jkoolcloud.tnt4j.stream.jmx.core;

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
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.core.Property;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;

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
	public static final String STAT_TRACE_MODE = "listener.trace.mode";
	public static final String STAT_FORCE_OBJECT_NAME_MODE = "listener.forceObjectName.mode";
	public static final String STAT_EXCLUDE_SET_COUNT = "listener.exclude.set.count";
	public static final String STAT_SILENCE_SET_COUNT = "listener.silence.set.count";

	boolean trace = false;
	boolean forceObjectName = false;
	PrintStream out;

	Collection<MBeanAttributeInfo> excAttrs = new HashSet<MBeanAttributeInfo>(89);
	Collection<MBeanAttributeInfo> silenceAttrs = new HashSet<MBeanAttributeInfo>(89);

	private PropertyNameBuilder pnb;
	protected final Object buildLock = new Object();

	/**
	 * Create an instance of {@code DefaultSampleListener} with a a given print stream and trace mode
	 *
	 * @param pStream print stream instance for tracing
	 * @param trace mode
	 * @param forceObjectName flag indicating to forcibly add objectName attribute if such is not present for a MBean
	 */
	public DefaultSampleListener(PrintStream pStream, boolean trace, boolean forceObjectName) {
		this.trace = trace;
		this.out = pStream == null ? System.out : pStream;
		this.forceObjectName = forceObjectName;
	}

	/**
	 * Determine if a given attribute to be excluded from sampling.
	 *
	 * @param attr MBean attribute info
	 * @return true when attribute should be excluded, false otherwise
	 */
	protected boolean isExcluded(MBeanAttributeInfo attr) {
		return isInCollection(excAttrs, attr);
	}

	/**
	 * Mark a given attribute to be excluded from sampling.
	 *
	 * @param attr MBean attribute info
	 */
	protected void exclude(MBeanAttributeInfo attr) {
		addToCollection(excAttrs, attr);
	}

	protected boolean isSilenced(MBeanAttributeInfo attr) {
		return isInCollection(silenceAttrs, attr);
	}

	protected void silence(MBeanAttributeInfo attr) {
		addToCollection(silenceAttrs, attr);
	}

	private static void addToCollection(Collection<MBeanAttributeInfo> coll, MBeanAttributeInfo attr) {
		try { // NOTE: sometimes MBeanAttributeInfo.equals throws internal NPE
			coll.add(attr);
		} catch (NullPointerException exc) {
		}
	}

	private static boolean isInCollection(Collection<MBeanAttributeInfo> coll, MBeanAttributeInfo attr) {
		try { // NOTE: sometimes MBeanAttributeInfo.equals throws internal NPE
			return coll.contains(attr);
		} catch (NullPointerException exc) {
			return false;
		}
	}

	@Override
	public void pre(SampleContext context, Activity activity) {
		if (trace) {
			out.println("Pre: " + activity.getName() 
					+ ": sample.count=" + context.getSampleCount() 
					+ ", mbean.count=" + getMBeanCount(context) 
					+ ", sample.mbeans.count=" + context.getMBeanCount() 
					+ ", exclude.attr.set=" + excAttrs.size() 
					+ ", silence.attr.set=" + silenceAttrs.size() 
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
		sample.silence(isSilenced(sample.getAttributeInfo()));
	}

	@Override
	public void post(SampleContext context, AttributeSample sample) throws UnsupportedAttributeException {
		MBeanAttributeInfo mbAttrInfo = sample.getAttributeInfo();
		PropertySnapshot snapshot = sample.getSnapshot();
		synchronized (buildLock) {
			processAttrValue(snapshot, mbAttrInfo, initPropName(mbAttrInfo.getName()), sample.get());
		}

		if (forceObjectName) {
			forceObjectNameAttribute(sample);
		}
	}

	private void forceObjectNameAttribute(AttributeSample sample) {
		MBeanAttributeInfo mbAttrInfo = sample.getAttributeInfo();
		PropertySnapshot snapshot = sample.getSnapshot();
		Property objNameProp = Utils.getSnapPropertyIgnoreCase(snapshot, "objectName");

		if (objNameProp == null) {
			synchronized (buildLock) {
				processAttrValue(snapshot, mbAttrInfo, initPropName("objectName"), sample.getObjectName());
			}
		}
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
					+ ", silence.attr.set=" + silenceAttrs.size() 
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
	public void error(SampleContext context, AttributeSample sample, OpLevel level) {
		sample.excludeNext(isFatalError(level == null ? OpLevel.ERROR : level));
		sample.silence(true);
		if (trace) {
			out.println("Failed to sample:\n ojbName=" + sample.getObjectName() + ",\n info=" + sample.getAttributeInfo()
					+ ",\n exclude=" + sample.excludeNext() + ",\n ex=" + sample.getError());
			sample.getError().printStackTrace(out);
		}
		if (sample.excludeNext()) {
			exclude(sample.getAttributeInfo());
		}
		if (sample.isSilence()) {
			silence(sample.getAttributeInfo());
		}
	}

	private static boolean isFatalError(OpLevel level) {
		return level.ordinal() >= OpLevel.ERROR.ordinal();
	}

	@Override
	public void getStats(SampleContext context, Map<String, Object> stats) {
		stats.put(STAT_TRACE_MODE, trace);
		stats.put(STAT_FORCE_OBJECT_NAME_MODE, forceObjectName);
		stats.put(STAT_EXCLUDE_SET_COUNT, excAttrs.size());
		stats.put(STAT_SILENCE_SET_COUNT, silenceAttrs.size());
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
	 * @param mbAttrInfo MBean attribute info
	 * @param propName name to be assigned to given attribute value
	 * @param value associated with attribute
	 * @return snapshot instance where all attributes are contained
	 */
	protected PropertySnapshot processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder propName, Object value) {
		if (value instanceof CompositeData) {
			CompositeData cdata = (CompositeData) value;
			Set<String> keys = cdata.getCompositeType().keySet();
			for (String key : keys) {
				Object cVal = cdata.get(key);
				processAttrValue(snapshot, mbAttrInfo, propName.append(key), cVal);
				propName.popLevel();
			}
		} else if (value instanceof TabularData) {
			TabularData tData = (TabularData) value;
			Collection<?> values = tData.values();
			int row = 0;
			for (Object tVal : values) {
				processAttrValue(snapshot, mbAttrInfo, propName.append(padNumber(++row)), tVal);
				propName.popLevel();
			}
		} else {
			snapshot.add(propName.propString(), value);
		}
		return snapshot;
	}

	private static String padNumber(int idx) {
		return idx < 10 ? "0" + idx : String.valueOf(idx);
	}

	/**
	 * Initializes property name builder with provided property name string.
	 *
	 * @param propName property name string
	 * @return property name builder instance
	 */
	protected PropertyNameBuilder initPropName(String propName) {
		if (pnb == null) {
			pnb = new PropertyNameBuilder(propName);
		} else {
			pnb.reset(propName);
		}

		return pnb;
	}
}