/*
 * Copyright 2014-2021 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx.aggregations;

import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.jkoolcloud.tnt4j.core.Activity;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.core.Snapshot;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;

/**
 * This class provides an aggregator implementation capable to append new attribute values to activity contained
 * snapshot or to create new one and put resolved values as it's properties.
 * <p>
 * JSON configuration of this aggregator can be like this:
 * 
 * <pre>
 *     {
 *         "aggregatorId": "MLSnapshotsAggregator",
 *         "type": "com.jkoolcloud.tnt4j.stream.jmx.aggregations.SnapshotAggregator",
 *         "enabled": true,
 *         "snapshots": [
 *             {
 *                 "name": "KafkaStatsML",
 *                 "category": "kafka.aggregated",
 *                 "enabled": true,
 *                 "properties": [
 *                     {
 *                         "beanId": "kafka.server:name=UnderReplicatedPartitions,type=ReplicaManager",
 *                         "attribute": "Value",
 *                         "name": "UnderReplicatedPartitions"
 *                     },
 *                     {
 *                         "beanId": "kafka.server:name=IsrShrinksPerSec,type=ReplicaManager",
 *                         "attribute": "MeanRate",
 *                         "name": "IsrShrinksPerSec"
 *                     },
 *                     {
 *                         "beanId": "kafka.server:name=IsrExpandsPerSec,type=ReplicaManager",
 *                         "attribute": "MeanRate",
 *                         "name": "IsrExpandsPerSec"
 *                     },
 *                     {
 *                         "beanId": "kafka.controller:name=ActiveControllerCount,type=KafkaController",
 *                         "attribute": "Value",
 *                         "name": "ActiveControllerCount"
 *                     },
 *                     {
 *                         "beanId": "kafka.controller:name=OfflinePartitionsCount,type=KafkaController",
 *                         "attribute": "Value",
 *                         "name": "OfflinePartitionsCount"
 *                     },
 *                     {
 *                         "beanId": "kafka.controller:name=LeaderElectionRateAndTimeMs,type=ControllerStats",
 *                         "attribute": "Mean",
 *                         "name": "LeaderElectionRateAndTimeMs"
 *                     },
 *                     {
 *                         "beanId": "kafka.controller:name=UncleanLeaderElectionsPerSec,type=ControllerStats",
 *                         "attribute": "MeanRate",
 *                         "name": "UncleanLeaderElectionsPerSec"
 *                     },
 *                     {
 *                         "beanId": "kafka.network:name=TotalTimeMs,request=?,type=RequestMetrics",
 *                         "where": {
 *                             "request": "Produce|FetchConsumer|FetchFollower"
 *                         },
 *                         "attribute": "Mean",
 *                         "name": "${request}-TotalTimeMs"
 *                     },
 *                     {
 *                         "beanId": "kafka.server:delayedOperation=?,name=PurgatorySize,type=DelayedOperationPurgatory",
 *                         "where": {
 *                             "delayedOperation": "Produce|Fetch"
 *                         },
 *                         "attribute": "Value",
 *                         "name": "${delayedOperation}-PurgatorySize"
 *                     },
 *                     {
 *                         "beanId": "kafka.server:name=BytesInPerSec,type=BrokerTopicMetrics",
 *                         "attribute": "MeanRate",
 *                         "name": "BytesInPerSec"
 *                     },
 *                     {
 *                         "beanId": "kafka.server:name=BytesOutPerSec,type=BrokerTopicMetrics",
 *                         "attribute": "MeanRate",
 *                         "name": "BytesOutPerSec"
 *                     },
 *                     {
 *                         "beanId": "kafka.network:name=RequestsPerSecond,request=?,type=RequestMetrics",
 *                         "where": {
 *                             "request": "Produce|FetchConsumer|FetchFollower"
 *                         },
 *                         "attribute": "Count",
 *                         "name": "${request}-RequestsPerSecond"
 *                     }
 *                 ]
 *             }
 *         ]
 *     }
 * </pre>
 *
 * @version $Revision: 1 $
 */
public class SnapshotAggregator implements ActivityAggregator {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(SnapshotAggregator.class);

	private static final String CAT_NAME_DELIMITER = ":";
	private static final String ID_CAT_DELIMITER = "@";

	private String id;

	private Collection<SnapshotAggregation> snapshotAggregations = new ArrayList<>();

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void configure(Map<String, ?> cfg) throws IllegalArgumentException {
		Collection<Map<String, ?>> snapshots = (Collection<Map<String, ?>>) cfg.get("snapshots");

		for (Map<String, ?> snapshot : snapshots) {
			String sName = (String) snapshot.get("name");
			String sCategory = (String) snapshot.get("category");
			Boolean sEnabled = (Boolean) snapshot.get("enabled");

			SnapshotAggregation snapshotAggregation = new SnapshotAggregation(sName, sCategory);
			snapshotAggregation.setEnabled(sEnabled == null || sEnabled);

			Collection<Map<String, ?>> properties = (Collection<Map<String, ?>>) snapshot.get("properties");

			for (Map<String, ?> property : properties) {
				String pName = (String) property.get("name");
				String pBeanId = (String) property.get("beanId");
				String pBeanAttribute = (String) property.get("attribute");

				Property f = snapshotAggregation.addProperty(pName, pBeanId, pBeanAttribute);

				Map<String, ?> fWhereMap = (Map<String, ?>) property.get("where");
				if (fWhereMap != null) {
					for (Map.Entry<String, ?> where : fWhereMap.entrySet()) {
						f.addWhere(where.getKey(), (String) where.getValue());
					}
				}
			}

			snapshotAggregations.add(snapshotAggregation);
		}
	}

	@Override
	public Activity aggregate(Activity activity) {
		if (activity != null) {
			for (SnapshotAggregation aggregation : snapshotAggregations) {
				if (aggregation.isEmpty()) {
					LOGGER.log(OpLevel.DEBUG, "Skipping empty aggregator execution ''{0}:{1}''", id, aggregation.name);
					continue;
				}

				if (!aggregation.isEnabled()) {
					LOGGER.log(OpLevel.DEBUG, "Skipping disabled aggregator execution ''{0}:{1}''", id,
							aggregation.name);
					continue;
				}

				LOGGER.log(OpLevel.DEBUG, "Aggregating beans data with aggregator ''{0}:{1}''", id, aggregation.name);

				String sCat = aggregation.getCategory();
				String sName = aggregation.getName();

				String sFullName;
				if (StringUtils.isEmpty(sCat)) {
					String[] nameTokens = sName.split(CAT_NAME_DELIMITER);
					sCat = nameTokens.length > 1 ? nameTokens[0] : SnapshotAggregation.DEFAULT_CATEGORY;
					sFullName = sName;
				} else {
					sFullName = sCat + CAT_NAME_DELIMITER + sName;
				}

				if (sFullName.contains(ID_CAT_DELIMITER)) {
					String[] idTokens = sFullName.split(ID_CAT_DELIMITER);
					sFullName = idTokens[0];
				}

				String sId = sFullName + ID_CAT_DELIMITER + sCat;

				Collection<Snapshot> aggrSnapshots = getSnapshots(activity, sId);
				if (aggrSnapshots.isEmpty()) {
					Snapshot aggrSnapshot = new PropertySnapshot(sCat, sFullName);
					activity.addSnapshot(aggrSnapshot);
					aggrSnapshots.add(aggrSnapshot);
				}

				for (Property property : aggregation.getProperties()) {
					String beanId = property.getFullBeanId();
					String attribute = property.getAttribute();
					String propertyName = property.getName();

					Set<Pair<String, String>> aProperties = new HashSet<>();

					if (!property.isWhereEmpty()) {
						Where where = property.getWhere();

						for (Map.Entry<String, String[]> var : where.getVarValuesMap().entrySet()) {
							String varName = var.getKey();
							String[] varValues = var.getValue();

							String beanIdVarToken = varName + "=?";
							String nameVarToken = "${" + varName + "}";
							for (String varValue : varValues) {
								MutablePair<String, String> varProperty = new MutablePair<>();
								if (beanId.contains(beanIdVarToken)) {
									varProperty.setLeft(beanId.replace(beanIdVarToken, varName + "=" + varValue));
								} else {
									varProperty.setLeft(beanId);
								}

								if (propertyName.contains(nameVarToken)) {
									varProperty.setRight(propertyName.replace(nameVarToken, varValue));
								} else {
									varProperty.setRight(propertyName);
								}

								aProperties.add(varProperty);
							}
						}
					}

					if (aProperties.isEmpty()) {
						Pair<String, String> iProperty = new ImmutablePair<>(beanId, propertyName);
						aProperties.add(iProperty);
					}

					for (Pair<String, String> aProperty : aProperties) {
						Snapshot actSnapshot = activity.getSnapshot(aProperty.getLeft());
						com.jkoolcloud.tnt4j.core.Property attrProp = actSnapshot == null ? null
								: actSnapshot.get(attribute);

						if (attrProp != null) {
							Object value = attrProp.getValue();
							for (Snapshot aggrSnapshot : aggrSnapshots) {
								aggrSnapshot.add(aProperty.getRight(), value);
							}
						}
					}
				}
			}
		}

		return activity;
	}

	protected static Collection<Snapshot> getSnapshots(Activity activity, String sId) {
		Collection<Snapshot> matchingSnapshots = new ArrayList<>();

		if (activity != null) {
			Collection<Snapshot> snapshots = activity.getSnapshots();

			for (Snapshot snapshot : snapshots) {
				if (snapshot.getId().matches(sId)) {
					matchingSnapshots.add(snapshot);
				}
			}
		}

		return matchingSnapshots;
	}

	private static class SnapshotAggregation {
		/**
		 * Constant defining default (one used when not defined in configuration) aggregation snapshot category name:
		 * {@value}
		 */
		static final String DEFAULT_CATEGORY = "jmx.aggregated";

		private String name;
		private String category;
		private boolean enabled = true;
		private Collection<Property> properties = new ArrayList<>();

		/**
		 * Constructs a new SnapshotAggregation.
		 *
		 * @param name
		 *            aggregation snapshot name, it can be activity contained snapshot name (to append properties) or
		 *            new one (to create new snapshot and add it to activity)
		 */
		SnapshotAggregation(String name) {
			this(name, DEFAULT_CATEGORY);
		}

		/**
		 * Constructs a new SnapshotAggregation.
		 *
		 * @param name
		 *            aggregation snapshot name, it can be activity contained snapshot name (to append properties) or
		 *            new one (to create new snapshot and add it to activity)
		 * @param category
		 *            aggregation snapshot category, it can be omitted if aggregation snapshot name has it defined like
		 *            this {@code "category:beanId"}
		 */
		SnapshotAggregation(String name, String category) {
			this.name = name;
			this.category = category;
		}

		/**
		 * Returns aggregation snapshot name.
		 *
		 * @return aggregation name
		 */
		String getName() {
			return name;
		}

		/**
		 * Returns aggregation snapshot category.
		 *
		 * @return aggregation category
		 */
		String getCategory() {
			return category;
		}

		/**
		 * Sets this aggregation enabled or disabled.
		 *
		 * @param enabled
		 *            flag enabling or disabling this aggregation
		 */
		void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		/**
		 * Returns if this aggregation is enabled.
		 *
		 * @return {@code true} if enabled, {@code false} - otherwise
		 */
		boolean isEnabled() {
			return enabled;
		}

		/**
		 * Creates property definition instance and ads it to aggregation properties list.
		 *
		 * @param name
		 *            property name, it can have variable expression like {@code "${varName}"}
		 * @param beanId
		 *            property bound bean identifier, it can have variable expression like {@code "varName=?"}
		 * @param attribute
		 *            property bound bean attribute name to get value
		 *
		 * @return property definition instance
		 * 
		 * @throws IllegalArgumentException
		 *             if any of property name, bean identifier or attribute name is empty
		 */
		Property addProperty(String name, String beanId, String attribute) throws IllegalArgumentException {
			Property property = new Property(name, beanId, attribute);

			properties.add(property);

			return property;
		}

		/**
		 * Checks if this aggregation has empty properties list.
		 *
		 * @return {@code true} if {@link #properties} is {@code null} or empty, {@code false} - otherwise
		 */
		boolean isEmpty() {
			return properties == null || properties.isEmpty();
		}

		/**
		 * Returns properties list for this aggregation.
		 *
		 * @return aggregation properties collection
		 */
		Collection<Property> getProperties() {
			return properties;
		}
	}

	private static class Property {
		/**
		 * Property bound bean identifier. It can have variable expression like {@code "varName=?"}
		 */
		String beanId;
		/**
		 * Property variables definition.
		 */
		Where where;
		/**
		 * Property bound bean attribute (snapshot property) name to get value.
		 */
		String attribute;
		/**
		 * Property name to be set as snapshot property name. It can have variable expression like {@code "${varName}"}.
		 */
		String name;

		/**
		 * This property bound bean snapshot full identifier.
		 */
		String fullBeanId;

		/**
		 * Constructs a new Property.
		 *
		 * @param name
		 *            property name
		 * @param beanId
		 *            bean identifier
		 * @param attribute
		 *            bean attribute name
		 *
		 * @throws IllegalArgumentException
		 *             if any of property name, bean identifier or attribute name is empty
		 */
		Property(String name, String beanId, String attribute) throws IllegalArgumentException {
			if (StringUtils.isEmpty(name)) {
				throw new IllegalArgumentException("Invalid configuration: property name must be set");
			}
			if (StringUtils.isEmpty(beanId)) {
				throw new IllegalArgumentException("Invalid configuration: property bound bean id must be set");
			}
			if (StringUtils.isEmpty(attribute)) {
				throw new IllegalArgumentException(
						"Invalid configuration: property bound bean attribute name must be set");
			}

			this.name = name;
			this.beanId = beanId;
			this.attribute = attribute;
		}

		/**
		 * Returns property bound bean identifier.
		 *
		 * @return bean identifier
		 */
		String getBeanId() {
			return beanId;
		}

		/**
		 * Returns property bound bean snapshot full identifier.
		 *
		 * @return bean snapshot full identifier
		 * 
		 * @see #makeFullBeanId()
		 */
		String getFullBeanId() {
			if (fullBeanId == null) {
				fullBeanId = makeFullBeanId();
			}

			return fullBeanId;
		}

		/**
		 * Makes full bean snapshot identifier string.
		 *
		 * @return full bean snapshot identifier
		 */
		String makeFullBeanId() {
			if (StringUtils.isEmpty(beanId)) {
				return beanId;
			}

			String[] bIdTokens = beanId.split(CAT_NAME_DELIMITER);

			if (bIdTokens.length < 2) {
				return beanId;
			}

			return beanId + ID_CAT_DELIMITER + bIdTokens[0];
		}

		/**
		 * Adds property variable definition.
		 *
		 * @param varName
		 *            variable name
		 * @param varValues
		 *            variable values string delimited by {@code "|"} symbol
		 * 
		 * @throws java.lang.IllegalArgumentException
		 *             if variable name is empty
		 * 
		 * @see com.jkoolcloud.tnt4j.stream.jmx.aggregations.SnapshotAggregator.Where#addVar(String, String)
		 */
		void addWhere(String varName, String varValues) throws IllegalArgumentException {
			if (this.where == null) {
				this.where = new Where();
			}
			this.where.addVar(varName, varValues);
		}

		/**
		 * Returns property variables definition.
		 *
		 * @return property variables definition
		 */
		Where getWhere() {
			return where;
		}

		/**
		 * Checks if this property variables definition is empty.
		 *
		 * @return {@code true} if property variables definition is {@code null} or empty, {@code false} - otherwise
		 */
		boolean isWhereEmpty() {
			return where == null || where.isEmpty();
		}

		/**
		 * Returns property bound bean attribute name.
		 *
		 * @return bean attribute name
		 */
		String getAttribute() {
			return attribute;
		}

		/**
		 * Returns property name.
		 *
		 * @return property name
		 */
		String getName() {
			return name;
		}
	}

	private static class Where {
		/**
		 * The variables definition map.
		 */
		Map<String, String[]> varMap;

		/**
		 * Checks if variables definition map is empty.
		 *
		 * @return {@code true} is variables definition map is {@code null} or empty, {@code false} - otherwise
		 */
		boolean isEmpty() {
			return varMap == null || varMap.isEmpty();
		}

		/**
		 * Adds variable definition.
		 *
		 * @param varName
		 *            variable name
		 * @param varValues
		 *            variable values string delimited by {@code "|"} symbol
		 *
		 * @throws IllegalArgumentException
		 *             if variable name is empty
		 */
		void addVar(String varName, String varValues) throws IllegalArgumentException {
			if (StringUtils.isEmpty(varName)) {
				throw new IllegalArgumentException(
						"Invalid configuration: property ''where'' condition variable name must be set");
			}

			if (varMap == null) {
				varMap = new LinkedHashMap<>();
			}

			varMap.put(varName, split(varValues));
		}

		/**
		 * Returns variable values array for provided variable name.
		 *
		 * @param varName
		 *            the variable name
		 *
		 * @return variable values array
		 */
		String[] getVarValues(String varName) {
			if (isEmpty()) {
				return null;
			}

			return varMap.get(varName);
		}

		/**
		 * Returns variables definition map.
		 *
		 * @return variables definition map
		 */
		Map<String, String[]> getVarValuesMap() {
			return varMap;
		}

		private static String[] split(String varValue) {
			return varValue == null ? null : varValue.split(Pattern.quote("|"));
		}
	}
}
