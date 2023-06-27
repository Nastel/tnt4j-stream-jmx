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

package com.jkoolcloud.tnt4j.stream.jmx.impl;

import java.util.Map;

import javax.management.j2ee.statistics.Stats;

import com.ibm.websphere.pmi.stat.WSStats;
import com.ibm.ws.pmi.j2ee.StatsImpl;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.stream.jmx.core.PropertyNameBuilder;

/**
 * This class provide a specific implementation of a {@link com.jkoolcloud.tnt4j.stream.jmx.impl.J2EESampleListener} to
 * handle WAS API {@link com.ibm.ws.pmi.j2ee.StatsImpl} and {@link com.ibm.websphere.pmi.stat.WSStats} type J2EE
 * statistics values.
 * 
 * @version $Revision: 1 $
 */
public class WASSampleListener extends J2EESampleListener {

	/**
	 * Create an instance of {@code WASSampleListener} with a given print stream and configuration properties.
	 *
	 * @param properties
	 *            listener configuration properties map
	 *
	 * @see com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener.ListenerProperties
	 */
	public WASSampleListener(Map<String, ?> properties) {
		super(properties);
	}

	@Override
	protected String getStatsName(Stats stats) {
		if (stats instanceof StatsImpl) {
			StatsImpl wsStatsImpl = (StatsImpl) stats;
			WSStats wsStats = wsStatsImpl.getWSImpl();

			return wsStats.getName();
		}

		return super.getStatsName(stats);
	}

	@Override
	protected void processEmptyStats(Stats stats, PropertySnapshot snapshot, PropertyNameBuilder propName) {
		if (stats instanceof StatsImpl) {
			StatsImpl wsStatsImpl = (StatsImpl) stats;
			WSStats wsStats = wsStatsImpl.getWSImpl();

			snapshot.add(propName.append("name").propString(), wsStats.getName());
			snapshot.add(propName.append("type").propString(), wsStats.getStatsType());
			snapshot.add(propName.append("time").propString(), wsStats.getTime());
		} else {
			super.processEmptyStats(stats, snapshot, propName);
		}
	}
}
