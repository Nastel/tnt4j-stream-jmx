/*
 * Copyright 2015-2022 JKOOL, LLC.
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
package com.jkoolcloud.tnt4j.stream.jmx.impl;

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
	 * @param properties
	 *            listener configuration properties map
	 *
	 * @see com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener.ListenerProperties
	 */
	public J2EESampleListener(Map<String, ?> properties) {
		super(properties);
	}

	@Override
	protected PropertySnapshot processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder propName, Object value) {
		if (value instanceof Stats) {
			Stats stats = (Stats) value;
			collectStats(stats, snapshot, mbAttrInfo, propName);

			return snapshot;
		} else {
			return super.processAttrValue(snapshot, mbAttrInfo, propName, value);
		}
	}

	private void collectStats(Stats stats, PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder statName) {
		String initialStatName = statName.toString();
		int initialPropsCount = snapshot.size();

		// getStatsName(stats, statName);

		if (stats instanceof JCAStats) {
			JCAStats jcaStats = (JCAStats) stats;
			collectStats(jcaStats.getConnections(), snapshot, mbAttrInfo, statName);
			collectStats(jcaStats.getConnectionPools(), snapshot, mbAttrInfo, statName);
		}
		if (stats instanceof JMSSessionStats) {
			JMSSessionStats jmsSessionStats = (JMSSessionStats) stats;

			collectStats(jmsSessionStats.getProducers(), snapshot, mbAttrInfo, statName);
			collectStats(jmsSessionStats.getConsumers(), snapshot, mbAttrInfo, statName);
		}
		if (stats instanceof JDBCStats) {
			JDBCStats jdbcStats = (JDBCStats) stats;
			collectStats(jdbcStats.getConnections(), snapshot, mbAttrInfo, statName);
			collectStats(jdbcStats.getConnectionPools(), snapshot, mbAttrInfo, statName);
		}
		if (stats instanceof JMSConnectionStats) {
			JMSConnectionStats jmsConnectionStats = (JMSConnectionStats) stats;
			collectStats(jmsConnectionStats.getSessions(), snapshot, mbAttrInfo, statName);
		}
		if (stats instanceof JMSStats) {
			JMSStats jmsStats = (JMSStats) stats;
			collectStats(jmsStats.getConnections(), snapshot, mbAttrInfo, statName);
		}

		Statistic[] statistics = stats.getStatistics();
		for (Statistic statistic : statistics) {
			collectStatistic(statistic, snapshot, mbAttrInfo, statName);
		}

		if (snapshot.size() == initialPropsCount) {
			processEmptyStats(stats, snapshot, statName);
		}

		statName.reset(initialStatName);
	}

	private void collectStats(Stats[] stats, PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder statName) {
		for (int i = 0; i < stats.length; i++) {
			String sName = getStatsName(stats[i]);
			statName.append(StringUtils.isEmpty(sName) ? String.valueOf(i) : sName);
			collectStats(stats[i], snapshot, mbAttrInfo, statName);
		}
	}

	private void collectStatistic(Statistic statistic, PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder statName) {
		processAttrValue(snapshot, mbAttrInfo, statName.append(statistic.getName()), statistic);
		statName.popLevel();
	}

	protected String getStatsName(Stats stats) {
		String statsName = null;

		if (stats instanceof JMSProducerStats) {
			statsName = ((JMSProducerStats) stats).getDestination();
		}
		if (stats instanceof JMSConsumerStats) {
			statsName = ((JMSConsumerStats) stats).getOrigin();
		}
		if (stats instanceof JCAConnectionStats) {
			JCAConnectionStats jcaConnectionStats = (JCAConnectionStats) stats;
			if (StringUtils.isNotEmpty(jcaConnectionStats.getConnectionFactory())) {
				statsName = jcaConnectionStats.getConnectionFactory();
			} else if (StringUtils.isNotEmpty(jcaConnectionStats.getManagedConnectionFactory())) {
				statsName = jcaConnectionStats.getManagedConnectionFactory();
			}
		}
		if (stats instanceof JDBCConnectionStats) {
			statsName = ((JDBCConnectionStats) stats).getJdbcDataSource();
		}

		return statsName;
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
