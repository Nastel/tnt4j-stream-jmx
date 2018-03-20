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
package com.jkoolcloud.tnt4j.stream.jmx.core;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.*;
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
	private static final String AGENT_PROP_PREFIX = "com.jkoolcloud.tnt4j.stream.jmx.agent.";

	public static final String STAT_TRACE_MODE = "listener.trace.mode";
	public static final String STAT_FORCE_OBJECT_NAME_MODE = "listener.forceObjectName.mode";
	public static final String STAT_COMPOSITE_DELIMITER = "listener.compositeProperty.delimiter";
	public static final String STAT_USE_OBJ_NAME_PROPS = "listener.useObjectNameProperties.mode";
	public static final String STAT_EXCLUDE_SET_COUNT = "listener.exclude.set.count";
	public static final String STAT_SILENCE_SET_COUNT = "listener.silence.set.count";

	boolean trace = false;
	boolean forceObjectName = false;
	PrintStream out;
	String compositeDelimiter = null;
	boolean useObjectNameProperties = true;

	Collection<MBeanAttributeInfo> excAttrs = new HashSet<MBeanAttributeInfo>(89);
	Collection<MBeanAttributeInfo> silenceAttrs = new HashSet<MBeanAttributeInfo>(89);

	private PropertyNameBuilder pnb;
	protected final ReentrantLock buildLock = new ReentrantLock();

	/**
	 * Create an instance of {@code DefaultSampleListener} with a a given print stream and configuration properties.
	 *
	 * @param pStream
	 *            print stream instance for tracing
	 * @param properties
	 *            listener configuration properties map
	 *
	 * @see com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener.ListenerProperties
	 */
	public DefaultSampleListener(PrintStream pStream, Map<String, ?> properties) {
		this.trace = Utils.getBoolean(ListenerProperties.TRACE.pName(), properties, trace);
		this.out = pStream == null ? System.out : pStream;
		this.forceObjectName = Utils.getBoolean(ListenerProperties.FORCE_OBJECT_NAME.pName(), properties,
				forceObjectName);
		this.compositeDelimiter = Utils.getString(ListenerProperties.COMPOSITE_DELIMITER.pName(), properties,
				compositeDelimiter);
		this.useObjectNameProperties = Utils.getBoolean(ListenerProperties.USE_OBJECT_NAME_PROPERTIES.pName(),
				properties, useObjectNameProperties);
	}

	/**
	 * Determine if a given attribute to be excluded from sampling.
	 *
	 * @param attr
	 *            MBean attribute info
	 * @return true when attribute should be excluded, false otherwise
	 */
	protected boolean isExcluded(MBeanAttributeInfo attr) {
		return isInCollection(excAttrs, attr);
	}

	/**
	 * Mark a given attribute to be excluded from sampling.
	 *
	 * @param attr
	 *            MBean attribute info
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
		buildLock.lock();
		try {
			processAttrValue(snapshot, mbAttrInfo, initPropName(mbAttrInfo.getName()), sample.get());
		} finally {
			buildLock.unlock();
		}
	}

	@Override
	public void complete(SampleContext context, Activity activity, ObjectName name, MBeanInfo info, Snapshot snapshot) {
		forceObjectNameAttribute(name, snapshot);
		if (useObjectNameProperties) {
			objNamePropsToSnapshot(name, snapshot);
		}
	}

	private void forceObjectNameAttribute(ObjectName name, Snapshot snapshot) {
		snapshot.add(Utils.OBJ_NAME_OBJ_PROP, name, true);
		Property objNameProp = Utils.getSnapPropertyIgnoreCase(snapshot, Utils.OBJ_NAME_PROP);

		if (objNameProp == null) {
			buildLock.lock();
			try {
				snapshot.add(Utils.OBJ_NAME_PROP, name, !forceObjectName);
			} finally {
				buildLock.unlock();
			}
		}
	}

	private void objNamePropsToSnapshot(ObjectName name, Snapshot snapshot) {
		Map<String, String> objNameProps = name.getKeyPropertyList();

		buildLock.lock();
		try {
			for (Map.Entry<String, String> objNameProp : objNameProps.entrySet()) {
				Property p = snapshot.get(objNameProp.getKey());
				// p = Utils.getSnapPropertyIgnoreCase(snapshot, objNameProp.getKey());
				if (p != null && isSameValue(p.getValue(), objNameProp.getValue())) {
					continue;
				}
				snapshot.add(objNameProp.getKey() + (p == null ? "" : "_"), objNameProp.getValue());
			}
		} finally {
			buildLock.unlock();
		}
	}

	private static boolean isSameValue(Object v1, Object v2) {
		return Utils.equal(v1, v2) || String.valueOf(v1).equals(String.valueOf(v2));
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
			return Utils.getExceptionMessages(exc);
		}
	}

	@Override
	public void error(SampleContext context, AttributeSample sample, OpLevel level) {
		sample.excludeNext(isFatalError(level == null ? OpLevel.ERROR : level));
		sample.silence(true);
		if (trace) {
			out.println(
					"Failed to sample:\n ojbName=" + sample.getObjectName() + ",\n info=" + sample.getAttributeInfo()
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
		stats.put(STAT_COMPOSITE_DELIMITER, compositeDelimiter);
		stats.put(STAT_USE_OBJ_NAME_PROPS, useObjectNameProperties);
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
	 * @param snapshot
	 *            instance where extracted attribute is stored
	 * @param mbAttrInfo
	 *            MBean attribute info
	 * @param propName
	 *            name to be assigned to given attribute value
	 * @param value
	 *            associated with attribute
	 * @return snapshot instance where all attributes are contained
	 */
	protected PropertySnapshot processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder propName, Object value) {
		if (value instanceof CompositeData) {
			CompositeData cdata = (CompositeData) value;
			Set<String> keys = cdata.getCompositeType().keySet();
			boolean isKVSet = keys.contains("key") && keys.contains("value"); // NON-NLS
			for (String key : keys) {
				Object cVal = cdata.get(key);
				if (isKVSet && "key".equals(key)) {
					propName.append(Utils.toString(cVal));
				} else if (isKVSet && "value".equals(key)) {
					processAttrValue(snapshot, mbAttrInfo, propName, cVal);
				} else {
					processAttrValue(snapshot, mbAttrInfo, propName.append(key), cVal);
				}
			}
			propName.popLevel();
		} else if (value instanceof TabularData) {
			TabularData tData = (TabularData) value;
			Collection<?> values = tData.values();
			int row = 0;
			for (Object tVal : values) {
				processAttrValue(snapshot, mbAttrInfo,
						tVal instanceof CompositeData ? propName : propName.append(padNumber(++row)), tVal);
			}
			propName.popLevel();
		} else {
			snapshot.add(propName.propString(), value);
		}
		return snapshot;
	}

	private static String padNumber(int idx) {
		return idx < 10 ? "0" + idx : String.valueOf(idx);
	}

	/**
	 * Initializes (creates or resets) property name builder with provided property name string.
	 *
	 * @param propName
	 *            property name string
	 * @return property name builder instance
	 *
	 * @see #createPropName(String)
	 */
	protected PropertyNameBuilder initPropName(String propName) {
		if (pnb == null) {
			pnb = createPropName(propName);
		} else {
			pnb.reset(propName);
		}

		return pnb;
	}

	/**
	 * Creates instance of property name builder with provided property name string.
	 *
	 * @param propName
	 *            property name string
	 * @return property name builder instance
	 */
	protected PropertyNameBuilder createPropName(String propName) {
		return StringUtils.isNotEmpty(compositeDelimiter) ? new PropertyNameBuilder(propName, compositeDelimiter)
				: new PropertyNameBuilder(propName);
	}

	/**
	 * Sample listener configuration properties enumeration.
	 */
	public enum ListenerProperties {
		/**
		 * Flag indicating whether the sample listener should print trace entries to print stream.
		 */
		TRACE("trace"),
		/**
		 * Flag indicating to forcibly add {@value com.jkoolcloud.tnt4j.stream.jmx.utils.Utils#OBJ_NAME_PROP} attribute
		 * if such is not present for a MBean.
		 */
		FORCE_OBJECT_NAME("forceObjectName"),
		/**
		 * Delimiter used to tokenize composite/tabular type MBean properties keys.
		 */
		COMPOSITE_DELIMITER("compositeDelimiter"),
		/**
		 * Flag indicating to copy MBean {@link javax.management.ObjectName} contained properties into sample snapshot
		 * properties.
		 */
		USE_OBJECT_NAME_PROPERTIES("useObjectNameProperties");

		private String pName;
		private String apName;

		private ListenerProperties(String pName) {
			this.pName = pName;
			this.apName = AGENT_PROP_PREFIX + pName;
		}

		/**
		 * Returns property name used by the sample listener.
		 * 
		 * @return listener property name
		 */
		public String pName() {
			return pName;
		}

		/**
		 * Returns {@link com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent} configuration property name used to configure
		 * the sample listener.
		 *
		 * @return sampling agent configuration property name
		 */
		public String apName() {
			return apName;
		}
	}
}