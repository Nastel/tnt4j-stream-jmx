/*
 * Copyright 2014-2023 JKOOL, LLC.
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

import org.apache.commons.lang3.StringUtils;

import com.ibm.websphere.pmi.stat.*;
import com.ibm.ws.pmi.stat.JCAConnectionPoolStatsImpl;
import com.ibm.ws.pmi.stat.JDBCConnectionStatsImpl;
import com.ibm.ws.pmi.stat.StatisticAggregateImpl;
import com.ibm.ws.pmi.stat.StatisticExternalImpl;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.stream.jmx.core.PropertyNameBuilder;

/**
 * This class provide a specific implementation of a {@link WASSampleListener} to handle WAS API provided
 * {@link com.ibm.ws.pmi.j2ee.StatsImpl} and {@link com.ibm.websphere.pmi.stat.WSStats} type J2EE statistics values.
 *
 * @version $Revision: 1 $
 */
public class WASPMISampleListener extends WASSampleListener {

	/**
	 * Create an instance of {@code WASPMISampleListener} with a given print stream and configuration properties.
	 *
	 * @param properties
	 *            listener configuration properties map
	 *
	 * @see com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener.ListenerProperties
	 */
	public WASPMISampleListener(Map<String, ?> properties) {
		super(properties);
	}

	@Override
	protected PropertySnapshot processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder propName, Object value) {
		if (value instanceof WSStats) {
			WSStats stats = (WSStats) value;
			collectStats(stats, snapshot, mbAttrInfo, propName);

			return snapshot;
		} else if (value instanceof com.ibm.ws.pmi.j2ee.StatsImpl) {
			com.ibm.ws.pmi.j2ee.StatsImpl wsStatsImpl = (com.ibm.ws.pmi.j2ee.StatsImpl) value;
			WSStats wsStats = wsStatsImpl.getWSImpl();

			collectStats(wsStats, snapshot, mbAttrInfo, propName);

			return snapshot;
		} else {
			return super.processAttrValue(snapshot, mbAttrInfo, propName, value);
		}
	}

	private void collectStats(WSStats stats, PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder statName) {
		String initialStatName = statName.toString();
		int initialPropsCount = snapshot.size();

		processAttrValue(snapshot, mbAttrInfo, statName.append("Name"), stats.getName());
		processAttrValue(snapshot, mbAttrInfo, statName.append("StatsType"), stats.getStatsType());
		processAttrValue(snapshot, mbAttrInfo, statName.append("Time"), stats.getTime());

		// getStatsName(stats, statName);

		if (stats instanceof JCAConnectionPoolStatsImpl) {
			JCAConnectionPoolStatsImpl jcaConnStats = (JCAConnectionPoolStatsImpl) stats;

			processAttrValue(snapshot, mbAttrInfo, statName.append("ConnectionFactory"),
					jcaConnStats.getConnectionFactory());
			processAttrValue(snapshot, mbAttrInfo, statName.append("ManagedConnectionFactory"),
					jcaConnStats.getManagedConnectionFactory());
		}
		if (stats instanceof JDBCConnectionStatsImpl) {
			JDBCConnectionStatsImpl jdbcConnectionStats = (JDBCConnectionStatsImpl) stats;

			processAttrValue(snapshot, mbAttrInfo, statName.append("JdbcDataSource"),
					jdbcConnectionStats.getJdbcDataSource());
		}

		WSStatistic[] statistics = stats.getStatistics();
		for (WSStatistic statistic : statistics) {
			collectStatistic(statistic, snapshot, mbAttrInfo, statName);
		}

		collectStats(stats.getSubStats(), snapshot, mbAttrInfo, statName);

		if (snapshot.size() == initialPropsCount) {
			processEmptyStats(stats, snapshot, statName);
		}

		statName.reset(initialStatName);
	}

	private void collectStats(WSStats[] stats, PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder statName) {
		String initialStatName = statName.toString();
		for (int i = 0; i < stats.length; i++) {
			String sName = getStatsName(stats[i]);
			statName.append(StringUtils.isEmpty(sName) ? getEmptyStatsName(stats[i], i) : sName);
			collectStats(stats[i], snapshot, mbAttrInfo, statName);
			statName.reset(initialStatName);
		}
	}

	private void collectStatistic(WSStatistic statistic, PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder statName) {
		processAttrValue(snapshot, mbAttrInfo, statName.append(statistic.getName()), statistic);
		statName.popLevel();
	}

	/**
	 * Resolves meaningful name for provided stats instance.
	 * 
	 * @param stats
	 *            stats instance
	 * @return stats instance provided name
	 */
	protected String getStatsName(WSStats stats) {
		return stats.getName();
	}

	/**
	 * Constructs stats name for stats instance having no any meaningful name.
	 *
	 * @param wsStats
	 *            stats instance
	 * @param index
	 *            stats instance index in the array
	 * @return stats name build of stats class name and index
	 */
	protected String getEmptyStatsName(WSStats wsStats, int index) {
		return wsStats.getClass().getSimpleName();
	}

	protected void processEmptyStats(WSStats wsStats, PropertySnapshot snapshot, PropertyNameBuilder propName) {
		snapshot.add(propName.append("name").propString(), wsStats.getName());
		snapshot.add(propName.append("type").propString(), wsStats.getStatsType());
		snapshot.add(propName.append("time").propString(), wsStats.getTime());
	}

	private void processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			PropertyNameBuilder propName, WSStatistic stat) {
		if (stat == null) {
			return;
		}

		if (isAddStatisticMetadata()) {
			processAttrValue(snapshot, mbAttrInfo, propName.append("Name"), stat.getName());
			processAttrValue(snapshot, mbAttrInfo, propName.append("Description"), stat.getDescription());
			processAttrValue(snapshot, mbAttrInfo, propName.append("LastSampleTime"), stat.getLastSampleTime());
			processAttrValue(snapshot, mbAttrInfo, propName.append("StartTime"), stat.getStartTime());
			processAttrValue(snapshot, mbAttrInfo, propName.append("Id"), stat.getId());
			processAttrValue(snapshot, mbAttrInfo, propName.append("DataInfo"), stat.getDataInfo());
		}
		processAttrValue(snapshot, mbAttrInfo, propName.append("Unit"), stat.getUnit());

		if (stat instanceof WSAverageStatistic) {
			WSAverageStatistic as = (WSAverageStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("Count"), as.getCount());
			processAttrValue(snapshot, mbAttrInfo, propName.append("Min"), as.getMin());
			processAttrValue(snapshot, mbAttrInfo, propName.append("Max"), as.getMax());
			processAttrValue(snapshot, mbAttrInfo, propName.append("SumOfSquares"), as.getSumOfSquares());
			processAttrValue(snapshot, mbAttrInfo, propName.append("Mean"), as.getMean());
			processAttrValue(snapshot, mbAttrInfo, propName.append("Total"), as.getTotal());
		}
		if (stat instanceof WSCountStatistic) {
			WSCountStatistic cs = (WSCountStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("Count"), cs.getCount());
		}
		if (stat instanceof WSBoundaryStatistic) {
			WSBoundaryStatistic bs = (WSBoundaryStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("LowerBound"), bs.getLowerBound());
			processAttrValue(snapshot, mbAttrInfo, propName.append("UpperBound"), bs.getUpperBound());
		}
		if (stat instanceof WSDoubleStatistic) {
			WSDoubleStatistic ds = (WSDoubleStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("Double"), ds.getDouble());
		}
		if (stat instanceof WSRangeStatistic) {
			WSRangeStatistic rs = (WSRangeStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("Current"), rs.getCurrent());
			processAttrValue(snapshot, mbAttrInfo, propName.append("LowWaterMark"), rs.getLowWaterMark());
			processAttrValue(snapshot, mbAttrInfo, propName.append("HighWaterMark"), rs.getHighWaterMark());
			processAttrValue(snapshot, mbAttrInfo, propName.append("Integral"), rs.getIntegral());
			processAttrValue(snapshot, mbAttrInfo, propName.append("Mean"), rs.getMean());
		}
		if (stat instanceof WSTimeStatistic) {
			WSTimeStatistic ts = (WSTimeStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("MaxTime"), ts.getMaxTime());
			processAttrValue(snapshot, mbAttrInfo, propName.append("MinTime"), ts.getMinTime());
			processAttrValue(snapshot, mbAttrInfo, propName.append("TotalTime"), ts.getTotalTime());
		}
		if (stat instanceof StatisticAggregateImpl) {
			processAttrValue(snapshot, mbAttrInfo, propName, ((StatisticAggregateImpl) stat).getStatistic());
			propName.popLevel();
		}
		if (stat instanceof StatisticExternalImpl) {
			processAttrValue(snapshot, mbAttrInfo, propName, ((StatisticExternalImpl) stat).getStatistic());
			propName.popLevel();
		}
	}
}
