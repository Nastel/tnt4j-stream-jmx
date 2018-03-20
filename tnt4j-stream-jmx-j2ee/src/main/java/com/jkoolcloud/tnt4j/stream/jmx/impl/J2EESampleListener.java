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
package com.jkoolcloud.tnt4j.stream.jmx.impl;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanAttributeInfo;
import javax.management.j2ee.statistics.*;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.PropertyNameBuilder;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;

/**
 * This class provide a specific implementation of a {@link SampleListener} to handle J2EE API {@link Stats} type
 * attributes.
 *
 * @version $Revision: 1 $
 */
public class J2EESampleListener extends DefaultSampleListener {

	/**
	 * Create an instance of {@code J2EESampleListener} with a a given print stream and configuration properties.
	 *
	 * @param pStream
	 *            print stream instance for tracing
	 * @param properties
	 *            listener configuration properties map
	 *
	 * @see com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener.ListenerProperties
	 */
	public J2EESampleListener(PrintStream pStream, Map<String, ?> properties) {
		super(pStream, properties);
	}

	@Override
	protected PropertySnapshot processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder propName, Object value) {
		if (value instanceof Stats) {
			Stats stats = (Stats) value;
			Map<String, Statistic> statisticMap = new HashMap<String, Statistic>();
			collectStats(stats, statisticMap, createPropName(""));
			if (statisticMap.isEmpty()) {
				processEmptyStats(stats, snapshot, propName);
			} else {
				for (Map.Entry<String, Statistic> sme : statisticMap.entrySet()) {
					processAttrValue(snapshot, mbAttrInfo, propName.append(sme.getKey()), sme.getValue());
					propName.popLevel();
				}
			}

			return snapshot;
		} else {
			return super.processAttrValue(snapshot, mbAttrInfo, propName, value);
		}
	}

	private void collectStats(Stats stats, Map<String, Statistic> statisticMap, PropertyNameBuilder statName) {
		String tmpStatName = statName.toString();

		getStatsName(stats, statName);

		if (stats instanceof JCAStats) {
			JCAStats jcaStats = (JCAStats) stats;
			collectStats(jcaStats.getConnections(), statisticMap, statName);
			collectStats(jcaStats.getConnectionPools(), statisticMap, statName);
		}
		if (stats instanceof JMSSessionStats) {
			JMSSessionStats jmsSessionStats = (JMSSessionStats) stats;

			collectStats(jmsSessionStats.getProducers(), statisticMap, statName);
			collectStats(jmsSessionStats.getConsumers(), statisticMap, statName);
		}
		if (stats instanceof JDBCStats) {
			JDBCStats jdbcStats = (JDBCStats) stats;
			collectStats(jdbcStats.getConnections(), statisticMap, statName);
			collectStats(jdbcStats.getConnectionPools(), statisticMap, statName);
		}
		if (stats instanceof JMSConnectionStats) {
			JMSConnectionStats jmsConnectionStats = (JMSConnectionStats) stats;
			collectStats(jmsConnectionStats.getSessions(), statisticMap, statName);
		}
		if (stats instanceof JMSStats) {
			JMSStats jmsStats = (JMSStats) stats;
			collectStats(jmsStats.getConnections(), statisticMap, statName);
		}

		Statistic[] statistics = stats.getStatistics();
		for (Statistic statistic : statistics) {
			collectStatistic(statistic, statisticMap, statName);
		}

		statName.reset(tmpStatName);
	}

	private void collectStats(Stats[] stats, Map<String, Statistic> statisticMap, PropertyNameBuilder statName) {
		for (int i = 0; i < stats.length; i++) {
			collectStats(stats[i], statisticMap, statName.append(String.valueOf(i)));
		}
	}

	private static void collectStatistic(Statistic statistic, Map<String, Statistic> statisticMap,
			PropertyNameBuilder statName) {
		statisticMap.put(statName.append(statistic.getName()).propString(), statistic);
	}

	protected void getStatsName(Stats stats, PropertyNameBuilder statName) {
		if (stats instanceof JMSProducerStats) {
			statName.popLevel();
			statName.append(((JMSProducerStats) stats).getDestination());
		}
		if (stats instanceof JMSConsumerStats) {
			statName.popLevel();
			statName.append(((JMSConsumerStats) stats).getOrigin());
		}
		if (stats instanceof JCAConnectionStats) {
			JCAConnectionStats jcaConnectionStats = (JCAConnectionStats) stats;
			if (StringUtils.isNotEmpty(jcaConnectionStats.getConnectionFactory())) {
				statName.popLevel();
				statName.append(jcaConnectionStats.getConnectionFactory());
			} else if (StringUtils.isNotEmpty(jcaConnectionStats.getManagedConnectionFactory())) {
				statName.popLevel();
				statName.append(jcaConnectionStats.getManagedConnectionFactory());
			}
		}
		if (stats instanceof JDBCConnectionStats) {
			statName.popLevel();
			statName.append(((JDBCConnectionStats) stats).getJdbcDataSource());
		}
	}

	protected void processEmptyStats(Stats stats, PropertySnapshot snapshot, PropertyNameBuilder propName) {
		snapshot.add(propName.propString(), stats.toString());
	}

	private void processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder propName, Statistic stat) {
		if (stat == null) {
			return;
		}

		processAttrValue(snapshot, mbAttrInfo, propName.append("Name"), stat.getName());
		processAttrValue(snapshot, mbAttrInfo, propName.append("Description"), stat.getDescription());
		processAttrValue(snapshot, mbAttrInfo, propName.append("LastSampleTime"), stat.getLastSampleTime());
		processAttrValue(snapshot, mbAttrInfo, propName.append("StartTime"), stat.getStartTime());
		processAttrValue(snapshot, mbAttrInfo, propName.append("Unit"), stat.getUnit());

		if (stat instanceof CountStatistic) {
			CountStatistic cs = (CountStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("Count"), cs.getCount());
		}
		if (stat instanceof BoundaryStatistic) {
			BoundaryStatistic bs = (BoundaryStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("LowerBound"), bs.getLowerBound());
			processAttrValue(snapshot, mbAttrInfo, propName.append("UpperBound"), bs.getUpperBound());
		}
		if (stat instanceof RangeStatistic) {
			RangeStatistic rs = (RangeStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("Current"), rs.getCurrent());
			processAttrValue(snapshot, mbAttrInfo, propName.append("LowWaterMark"), rs.getLowWaterMark());
			processAttrValue(snapshot, mbAttrInfo, propName.append("HighWaterMark"), rs.getHighWaterMark());
		}
		if (stat instanceof TimeStatistic) {
			TimeStatistic ts = (TimeStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("Count"), ts.getCount());
			processAttrValue(snapshot, mbAttrInfo, propName.append("MaxTime"), ts.getMaxTime());
			processAttrValue(snapshot, mbAttrInfo, propName.append("MinTime"), ts.getMinTime());
			processAttrValue(snapshot, mbAttrInfo, propName.append("TotalTime"), ts.getTotalTime());
		}
	}
}
