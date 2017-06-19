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
package com.jkoolcloud.tnt4j.stream.jmx.impl;

import java.io.PrintStream;

import javax.management.MBeanAttributeInfo;
import javax.management.j2ee.statistics.*;

import com.jkoolcloud.tnt4j.core.Property;
import com.jkoolcloud.tnt4j.core.PropertySnapshot;
import com.jkoolcloud.tnt4j.stream.jmx.conditions.AttributeSample;
import com.jkoolcloud.tnt4j.stream.jmx.core.DefaultSampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.UnsupportedAttributeException;

/**
 * This class provide a specific implementation of a {@link SampleListener} for a WebSphere Application Server.
 *
 * @version $Revision: 1 $
 */
public class WASSampleListener extends DefaultSampleListener {

	/**
	 * Create an instance of {@code WASSampleListener} with a a given print stream and trace mode
	 *
	 * @param pStream
	 *            print stream instance for tracing
	 * @param trace
	 *            mode
	 */
	public WASSampleListener(PrintStream pStream, boolean trace) {
		super(pStream, trace);
	}

	@Override
	public void post(SampleContext context, AttributeSample sample) throws UnsupportedAttributeException {
		super.post(context, sample);

		MBeanAttributeInfo mbAttrInfo = sample.getAttributeInfo();
		PropertySnapshot snapshot = sample.getSnapshot();
		Property onp = snapshot.get("objectName");
		if (onp == null) {
			processAttrValue(snapshot, mbAttrInfo, new StringBuilder("objectName"), sample.getObjetName());
		}
	}

	@Override
	protected PropertySnapshot processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo,
			StringBuilder propName, Object value) {
		if (value instanceof Stats) {
			Stats stats = (Stats) value;
			Statistic[] statistics = stats.getStatistics();
			for (Statistic stat : statistics) {
				processAttrValue(snapshot, mbAttrInfo, propName.append("\\").append(stat.getName()), stat);
			}

			return snapshot;
		} else {
			return super.processAttrValue(snapshot, mbAttrInfo, propName, value);
		}
	}

	private void processAttrValue(PropertySnapshot snapshot, MBeanAttributeInfo mbAttrInfo, StringBuilder propName,
			Statistic stat) {
		if (stat == null) {
			return;
		}

		processAttrValue(snapshot, mbAttrInfo, propName.append("\\Name"), stat.getName());
		processAttrValue(snapshot, mbAttrInfo, propName.append("\\Description"), stat.getDescription());
		processAttrValue(snapshot, mbAttrInfo, propName.append("\\LastSampleTime"), stat.getLastSampleTime());
		processAttrValue(snapshot, mbAttrInfo, propName.append("\\StartTime"), stat.getStartTime());

		if (stat instanceof CountStatistic) {
			CountStatistic cs = (CountStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\Count"), cs.getCount());
		}
		if (stat instanceof BoundaryStatistic) {
			BoundaryStatistic bs = (BoundaryStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\LowerBound"), bs.getLowerBound());
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\UpperBound"), bs.getUpperBound());
		}
		if (stat instanceof RangeStatistic) {
			RangeStatistic rs = (RangeStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\Current"), rs.getCurrent());
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\LowWaterMark"), rs.getLowWaterMark());
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\HighWaterMark"), rs.getHighWaterMark());
		}
		if (stat instanceof TimeStatistic) {
			TimeStatistic ts = (TimeStatistic) stat;
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\Count"), ts.getCount());
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\MaxTime"), ts.getMaxTime());
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\MinTime"), ts.getMinTime());
			processAttrValue(snapshot, mbAttrInfo, propName.append("\\TotalTime"), ts.getTotalTime());
		}

		processAttrValue(snapshot, mbAttrInfo, propName.append("\\Unit"), stat.getUnit());
	}
}
