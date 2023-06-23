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
package com.jkoolcloud.tnt4j.stream.jmx.core;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.*;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
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
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(DefaultSampleListener.class);

	private static final String AGENT_PROP_PREFIX = "com.jkoolcloud.tnt4j.stream.jmx.agent.";

	public static final String STAT_FORCE_OBJECT_NAME_MODE = "listener.forceObjectName.mode";
	public static final String STAT_COMPOSITE_DELIMITER = "listener.compositeProperty.delimiter";
	public static final String STAT_USE_OBJ_NAME_PROPS_MODE = "listener.useObjectNameProperties.mode";
	public static final String STAT_EXCLUDE_ON_ERROR_MODE = "listener.excludeOnError.mode";
	public static final String STAT_EXCLUDE_SET_COUNT = "listener.exclude.set.count";
	public static final String STAT_USER_EXCLUDED_ATTRS_COUNT = "listener.user.excluded.attrs.count";

	boolean forceObjectName = false;
	String compositeDelimiter = null;
	boolean useObjectNameProperties = true;
	boolean excludeOnError = false;

	Collection<MBeanAttributeInfo> excAttrs = new HashSet<>(89);
	Map<ObjectName, Pattern> userExcAttrs = new HashMap<>(5);

	private PropertyNameBuilder pnb;
	protected final ReentrantLock buildLock = new ReentrantLock();

	/**
	 * Create an instance of {@code DefaultSampleListener} with a a given print stream and configuration properties.
	 *
	 * @param properties
	 *            listener configuration properties map
	 *
	 * @see com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener.ListenerProperties
	 */
	public DefaultSampleListener(Map<String, ?> properties) {
		this.forceObjectName = Utils.getBoolean(ListenerProperties.FORCE_OBJECT_NAME.pName(), properties,
				forceObjectName);
		this.compositeDelimiter = Utils.getString(ListenerProperties.COMPOSITE_DELIMITER.pName(), properties,
				compositeDelimiter);
		this.useObjectNameProperties = Utils.getBoolean(ListenerProperties.USE_OBJECT_NAME_PROPERTIES.pName(),
				properties, useObjectNameProperties);
		this.excludeOnError = Utils.getBoolean(ListenerProperties.EXCLUDE_ON_ERROR.pName(), properties, excludeOnError);

		String attrExcludes = Utils.getString(ListenerProperties.USER_EXCLUDED_ATTRIBUTES.pName(), properties, "");
		fillUserExcludedAttributes(attrExcludes);
	}

	/**
	 * Fills user excluded attributes map with entries parser from property value.
	 *
	 * @param attrExcludes
	 *            user exclude attributes property string value to parse
	 */
	protected void fillUserExcludedAttributes(String attrExcludes) {
		if (StringUtils.isEmpty(attrExcludes)) {
			return;
		}

		String FILTER_TOKENS_DELIM = ";";
		String MBEAN_DELIM = "@";
		String ATTRS_DELIM = ",";
		String WILDCARD = "*";
		String RE_WB = "\\b";
		String RE_DWB = "\\b\\b";
		String RE_ANJ = "\\b|\\b";

		StringTokenizer itk = new StringTokenizer(attrExcludes, FILTER_TOKENS_DELIM);
		while (itk.hasMoreTokens()) {
			String attrsFilter = itk.nextToken().trim();
			if (StringUtils.isNotEmpty(attrsFilter)) {
				String[] filterTokens = attrsFilter.split(MBEAN_DELIM);

				String attrsList = ArrayUtils.getLength(filterTokens) > 0 ? filterTokens[0].trim() : "";
				String mBean = ArrayUtils.getLength(filterTokens) > 1 ? filterTokens[1].trim() : "";

				if (StringUtils.isEmpty(attrsList)) {
					attrsList = WILDCARD;
				}
				if (StringUtils.isEmpty(mBean)) {
					mBean = WILDCARD;
				}

				try {
					ObjectName mBeanName = WILDCARD.equals(mBean) ? ObjectName.WILDCARD : new ObjectName(mBean);
					attrsList = RE_WB + attrsList.replace(ATTRS_DELIM, RE_ANJ) + RE_WB;

					Pattern attrNamePattern = userExcAttrs.get(mBeanName);
					if (attrNamePattern != null) {
						attrsList = attrNamePattern.pattern() + RE_ANJ + attrsList;
						attrsList = attrsList.replace(RE_DWB, RE_WB);
					}

					attrNamePattern = Pattern.compile(Utils.wildcardToRegex2(attrsList));
					userExcAttrs.put(mBeanName, attrNamePattern);
				} catch (MalformedObjectNameException exc) {
					LOGGER.log(OpLevel.WARNING,
							"Init: Failed to construct ObjectName from attribute exclude filter definition: attrFilter={1}, objName={1}",
							attrsFilter, mBean);
				}
			}
		}

		LOGGER.log(OpLevel.DEBUG, "Init: User MBean attribute exclusions map: {0}", userExcAttrs);
	}

	/**
	 * Determine if a given attribute to be excluded from sampling.
	 *
	 * @param objName
	 *            MBean object name
	 * @param ainfo
	 *            MBean attribute info
	 * @return {@code true} when attribute should be excluded, {@code false} - otherwise
	 */
	protected boolean isExcluded(ObjectName objName, MBeanAttributeInfo ainfo) {
		return isInCollection(excAttrs, ainfo) || isExcludedByUser(userExcAttrs, objName, ainfo);
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

	/**
	 * Determine if a given attribute is excluded by user and such attribute shall not be sampled.
	 *
	 * @param userExcAttrs
	 *            user excluded attributes map
	 * @param objName
	 *            MBean object name
	 * @param ainfo
	 *            MBean attribute info
	 * @return {@code true} is attribute is excluded by user defined list, {@code false} - otherwise
	 */
	protected boolean isExcludedByUser(Map<ObjectName, Pattern> userExcAttrs, ObjectName objName,
			MBeanAttributeInfo ainfo) {
		if (!Utils.isEmpty(userExcAttrs)) {
			for (Map.Entry<ObjectName, Pattern> ueae : userExcAttrs.entrySet()) {
				boolean apply = ueae.getKey().apply(objName);

				if (apply) {
					Pattern attributeNamePattern = ueae.getValue();
					Matcher fMatcher = attributeNamePattern.matcher(ainfo.getName());
					if (fMatcher.matches()) {
						LOGGER.log(OpLevel.DEBUG, "UserExclude: Excluding user defined MBean attribute: {1}@{0}",
								objName, ainfo.getName());
						exclude(ainfo);
						return true;
					}
				}
			}
		}

		return false;
	}

	@Override
	public void pre(SampleContext context, Activity activity) {
		LOGGER.log(OpLevel.DEBUG,
				"Pre: {0}: sample.count={1}, mbean.count={2}, sample.mbeans.count={3}, exclude.user.attr.set={4}, exclude.attr.set={5}, total.noop.count={7}, total.exclude.count={8}, total.error.count={9}, tracking.id={10}, mbean.server={11}",
				activity.getName(), context.getSampleCount(), getMBeanCount(context), context.getMBeanCount(),
				userExcAttrs.size(), excAttrs.size(), context.getTotalNoopCount(), context.getExcludeAttrCount(),
				context.getTotalErrorCount(), activity.getTrackingId(), context.getMBeanServer());
	}

	@Override
	public void pre(SampleContext context, AttributeSample sample) {
		Map<String, MBeanAttributeInfo> aInfoMap = sample.getAttributesInfo();
		for (Map.Entry<String, MBeanAttributeInfo> ainfo : aInfoMap.entrySet()) {
			boolean exclude = !ainfo.getValue().isReadable() || isExcluded(sample.getObjectName(), ainfo.getValue());

			if (exclude) {
				sample.exclude(ainfo.getValue());
			}
		}
	}

	@Override
	public void post(SampleContext context, AttributeSample sample) throws UnsupportedAttributeException {
		PropertySnapshot snapshot = sample.getSnapshot();
		buildLock.lock();
		try {
			AttributeList attrList = sample.get();
			for (Attribute attr : attrList.asList()) {
				MBeanAttributeInfo mbAttrInfo = sample.getAttributeInfo(attr.getName());
				processAttrValue(snapshot, mbAttrInfo, initPropName(mbAttrInfo.getName()), attr.getValue());
			}
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
		LOGGER.log(OpLevel.DEBUG,
				"Post: {0}: sample.count={1}, mbean.count={2}, elapsed.usec={3}, snap.count={4}, id.count={5}, sample.mbeans.count={6}, sample.metric.count={7}, sample.time.usec={8}, exclude.user.attr.set={9}, exclude.attr.set={10}, total.noop.count={12}, total.exclude.count={13}, total.error.count={14}, tracking.id={15}, mbean.server={16}",
				activity.getName(), context.getSampleCount(), getMBeanCount(context), activity.getElapsedTimeUsec(),
				activity.getSnapshotCount(), activity.getIdCount(), context.getMBeanCount(),
				context.getLastMetricCount(), context.getLastSampleUsec(), userExcAttrs.size(), excAttrs.size(),
				context.getTotalNoopCount(), context.getExcludeAttrCount(), context.getTotalErrorCount(),
				activity.getTrackingId(), context.getMBeanServer());
	}

	private static String getMBeanCount(SampleContext context) {
		try {
			return String.valueOf(context.getMBeanServer().getMBeanCount());
		} catch (Exception exc) {
			return Utils.getExceptionMessages(exc);
		}
	}

	@Override
	public void error(SampleContext context, AttributeSample sample, OpLevel level) {
		LOGGER.log(OpLevel.DEBUG, "Error: Failed to sample:\n ojbName={0},\n info={1},\n exclude={2}\n ex={4}",
				sample.getObjectName(), sample.getAttributesInfo(), sample.excludes().size(),
				LOGGER.isSet(OpLevel.TRACE) ? Utils.getExceptionMessage(sample.getError())
						: Utils.getAllExceptionMessages(sample.getError()),
				LOGGER.isSet(OpLevel.TRACE) ? sample.getError() : null);
	}

	private boolean isFatalError(Throwable exc, OpLevel level) {
		boolean fatal = level.ordinal() >= OpLevel.ERROR.ordinal();

		if (fatal) {
			return true;
		}

		if (excludeOnError) {
			Throwable ct = exc;
			if (exc instanceof RuntimeMBeanException) {
				ct = exc.getCause();
			}
			String eName = ct.getClass().getSimpleName();

			if (eName.contains("Unsupported")) {
				return true;
			} else if (eName.contains("NotFound")) {
				return true;
			}

			if (exc instanceof MBeanException) {
				ct = Utils.getTopCause(exc);
			}
			eName = ct.getClass().getSimpleName();

			if (eName.contains("NoSuch")) {
				return true;
			} else if (eName.contains("Illegal")) {
				return true;
			} else if (eName.contains("NotRunning")) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void getStats(SampleContext context, Map<String, Object> stats) {
		stats.put(STAT_FORCE_OBJECT_NAME_MODE, forceObjectName);
		stats.put(STAT_COMPOSITE_DELIMITER, compositeDelimiter);
		stats.put(STAT_USE_OBJ_NAME_PROPS_MODE, useObjectNameProperties);
		stats.put(STAT_EXCLUDE_ON_ERROR_MODE, excludeOnError);
		stats.put(STAT_EXCLUDE_SET_COUNT, excAttrs.size());
		stats.put(STAT_USER_EXCLUDED_ATTRS_COUNT, userExcAttrs.size());
	}

	@Override
	public void register(SampleContext context, ObjectName oName) {
		LOGGER.log(OpLevel.DEBUG, "Register MBean: {0}, mbean.server={1}", oName, context.getMBeanServer());
	}

	@Override
	public void unregister(SampleContext context, ObjectName oName) {
		LOGGER.log(OpLevel.DEBUG, "Un-register MBean: {0}, mbean.server={1}", oName, context.getMBeanServer());
	}

	@Override
	public void error(SampleContext context, Throwable ex) {
		LOGGER.log(OpLevel.ERROR, "Error: Unexpected error when sampling mbean.server={0}", context.getMBeanServer(),
				ex);
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
		if (value instanceof CompositeData[]) {
			CompositeData[] cdArray = (CompositeData[]) value;

			for (int i = 0; i < cdArray.length; i++) {
				CompositeData cdata = cdArray[i];

				processAttrValue(snapshot, mbAttrInfo, propName.append(padNumber(i + 1)), cdata);
				propName.popLevel();
			}
			propName.popLevel();
		} else if (value instanceof TabularData[]) {
			TabularData[] tdArray = (TabularData[]) value;

			for (int i = 0; i < tdArray.length; i++) {
				TabularData tdata = tdArray[i];

				processAttrValue(snapshot, mbAttrInfo, propName.append(padNumber(i + 1)), tdata);
				propName.popLevel();
			}
			propName.popLevel();
		} else if (value instanceof CompositeData) {
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
		USE_OBJECT_NAME_PROPERTIES("useObjectNameProperties"),
		/**
		 * Flag indicating to auto-exclude failed to sample attributes.
		 */
		EXCLUDE_ON_ERROR("excludeOnError"),
		/**
		 * List of user chosen attribute names (may have wildcards {@code '*'} and {@code '?'}) to exclude, pattern:
		 * {@code "attr1,attr2,...,attrN@MBean1_ObjectName;...;attr1,attr2,...,attrN@MBeanN_ObjectName"}
		 */
		USER_EXCLUDED_ATTRIBUTES("excludedAttributes");

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