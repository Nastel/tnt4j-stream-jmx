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

import com.jkoolcloud.tnt4j.core.*;
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
 *                 "fields": [
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
	public void configure(Map<String, ?> cfg) throws IllegalArgumentException {
		Collection<Map<String, ?>> snapshots = (Collection<Map<String, ?>>) cfg.get("snapshots");

		for (Map<String, ?> snapshot : snapshots) {
			String sName = (String) snapshot.get("name");
			String sCategory = (String) snapshot.get("category");
			Boolean sEnabled = (Boolean) snapshot.get("enabled");

			SnapshotAggregation snapshotAggregation = new SnapshotAggregation(sName, sCategory);
			snapshotAggregation.setEnabled(sEnabled == null || sEnabled);

			Collection<Map<String, ?>> fields = (Collection<Map<String, ?>>) snapshot.get("fields");

			for (Map<String, ?> field : fields) {
				String fName = (String) field.get("name");
				String fBeanId = (String) field.get("beanId");
				String fBeanAttribute = (String) field.get("attribute");

				Field f = snapshotAggregation.addField(fName, fBeanId, fBeanAttribute);

				Map<String, ?> fWhereMap = (Map<String, ?>) field.get("where");
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
					String[] nameTokens = sName.split(":");
					sCat = nameTokens.length > 1 ? nameTokens[0] : SnapshotAggregation.DEFAULT_CATEGORY;
					sFullName = sName;
				} else {
					sFullName = sCat + ":" + sName;
				}

				String sId = sFullName + "@" + sCat;

				Snapshot aSnapshot = activity.getSnapshot(sId);
				if (aSnapshot == null) {
					aSnapshot = new PropertySnapshot(sCat, sFullName);
					activity.addSnapshot(aSnapshot);
				}

				for (Field field : aggregation.getFields()) {
					String beanId = field.getFullBeanId();
					String attribute = field.getAttribute();
					String fieldName = field.getName();

					Set<Pair<String, String>> aFields = new HashSet<>();

					if (!field.isWhereEmpty()) {
						Where where = field.getWhere();

						for (Map.Entry<String, String[]> var : where.getVarValuesMap().entrySet()) {
							String varName = var.getKey();
							String[] varValues = var.getValue();

							String beanIdVarToken = "," + varName + "=?";
							String nameVarToken = "${" + varName + "}";
							for (String varValue : varValues) {
								MutablePair<String, String> varField = new MutablePair<>();
								if (beanId.contains(beanIdVarToken)) {
									varField.setLeft(beanId.replace(beanIdVarToken, "," + varName + "=" + varValue));
								} else {
									varField.setLeft(beanId);
								}

								if (fieldName.contains(nameVarToken)) {
									varField.setRight(fieldName.replace(nameVarToken, varValue));
								} else {
									varField.setRight(fieldName);
								}

								aFields.add(varField);
							}
						}
					}

					if (aFields.isEmpty()) {
						Pair<String, String> iField = new ImmutablePair<>(beanId, fieldName);
						aFields.add(iField);
					}

					for (Pair<String, String> aField : aFields) {
						Snapshot bSnapshot = activity.getSnapshot(aField.getLeft());
						Property attrProp = bSnapshot == null ? null : bSnapshot.get(attribute);

						if (attrProp != null) {
							Object value = attrProp.getValue();
							aSnapshot.add(aField.getRight(), value);
						}
					}
				}
			}
		}

		return activity;
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
		private Collection<Field> fields = new ArrayList<>();

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
		 * Creates field definition instance and ads it to aggregation fields list.
		 *
		 * @param name
		 *            field name, it can have variable expression like {@code "${varName}"}
		 * @param beanId
		 *            field bound bean identifier, it can have variable expression like {@code "${varName}"}
		 * @param attribute
		 *            field bound bean attribute name to get value
		 *
		 * @return field definition instance
		 * 
		 * @throws IllegalArgumentException
		 *             if any of field name, bean identifier or attribute name is empty
		 */
		Field addField(String name, String beanId, String attribute) throws IllegalArgumentException {
			Field field = new Field(name, beanId, attribute);

			fields.add(field);

			return field;
		}

		/**
		 * Checks if this aggregation has empty fields list.
		 *
		 * @return {@code true} if {@link #fields} is {@code null} or empty, {@code false} - otherwise
		 */
		boolean isEmpty() {
			return fields == null || fields.isEmpty();
		}

		/**
		 * Returns fields list for this aggregation.
		 *
		 * @return aggregation fields collection
		 */
		Collection<Field> getFields() {
			return fields;
		}
	}

	private static class Field {
		/**
		 * Field bound bean identifier. It can have variable expression like {@code "${varName}"}.
		 */
		String beanId;
		/**
		 * Field variables definition.
		 */
		Where where;
		/**
		 * Field bound bean attribute (snapshot property) name to get value.
		 */
		String attribute;
		/**
		 * Field name to be set as snapshot property name. It can have variable expression like {@code "${varName}"}.
		 */
		String name;

		/**
		 * This field bound bean snapshot full identifier.
		 */
		String fullBeanId;

		/**
		 * Constructs a new Field.
		 *
		 * @param name
		 *            field name
		 * @param beanId
		 *            bean identifier
		 * @param attribute
		 *            bean attribute name
		 *
		 * @throws IllegalArgumentException
		 *             if any of field name, bean identifier or attribute name is empty
		 */
		Field(String name, String beanId, String attribute) throws IllegalArgumentException {
			if (StringUtils.isEmpty(name)) {
				throw new IllegalArgumentException("Invalid configuration: field name must be set");
			}
			if (StringUtils.isEmpty(beanId)) {
				throw new IllegalArgumentException("Invalid configuration: field bound bean id must be set");
			}
			if (StringUtils.isEmpty(attribute)) {
				throw new IllegalArgumentException(
						"Invalid configuration: field bound bean attribute name must be set");
			}

			this.name = name;
			this.beanId = beanId;
			this.attribute = attribute;
		}

		/**
		 * Returns field bound bean identifier.
		 *
		 * @return bean identifier
		 */
		String getBeanId() {
			return beanId;
		}

		/**
		 * Returns field bound bean snapshot full identifier.
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

			String[] bIdTokens = beanId.split(":");

			if (bIdTokens.length < 2) {
				return beanId;
			}

			return beanId + "@" + bIdTokens[0];
		}

		/**
		 * Adds field variable definition.
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
		 * Returns field variables definition.
		 *
		 * @return field variables definition
		 */
		Where getWhere() {
			return where;
		}

		/**
		 * Checks if this field variables definition is empty.
		 *
		 * @return {@code true} if field variables definition is {@code null} or empty, {@code false} - otherwise
		 */
		boolean isWhereEmpty() {
			return where == null || where.isEmpty();
		}

		/**
		 * Returns field bound bean attribute name.
		 *
		 * @return bean attribute name
		 */
		String getAttribute() {
			return attribute;
		}

		/**
		 * Returns field name.
		 *
		 * @return field name
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
						"Invalid configuration: field ''where'' condition variable name must be set");
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
