/*
 * Copyright 2015 JKOOL, LLC.
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
package org.tnt4j.stream.jmx;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;

import javax.management.MBeanAttributeInfo;

import org.tnt4j.stream.jmx.conditions.AttributeSample;
import org.tnt4j.stream.jmx.core.SampleContext;
import org.tnt4j.stream.jmx.core.SampleListener;

import com.nastel.jkool.tnt4j.core.Activity;

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
	public static String STAT_TRACE_MODE = "listener.trace.mode";
	public static String STAT_EXCLUDE_SET_COUNT = "listener.exclude.set.count";

	boolean trace = false;
	PrintStream out;
	
	HashSet<MBeanAttributeInfo> excAttrs = new HashSet<MBeanAttributeInfo>(89);
	
	public DefaultSampleListener(PrintStream pstream, boolean trace) {
		this.trace = trace;
		this.out = pstream;
	}
	
	/**
	 * Determine if a given attribute to be excluded from sampling.	
	 * 
	 * @param attr MBean attribute info
	 * @return true when attribute should be excluded, false otherwise
	 */
	protected boolean isExcluded(MBeanAttributeInfo attr) {
	    return excAttrs.contains(attr);
    }

	/**
	 * Mark a given attribute to be excluded from sampling.	
	 * 
	 * @param attr MBean attribute info
	 */
	protected void exclude(MBeanAttributeInfo attr) {
	    excAttrs.add(attr);
    }

	@Override
	public void pre(SampleContext context, Activity activity) {
	}

	@Override
	public boolean sample(SampleContext context, AttributeSample sample) {
		return !isExcluded(sample.getAttributeInfo());
	}

	@Override
	public void post(SampleContext context, Activity activity) {
		if (trace) {
			out.println(activity.getName()
				+ ": sample.count=" + context.getSampleCount()
				+ ", mbean.count=" + context.getMBeanServer().getMBeanCount()
				+ ", elapsed.usec=" + activity.getElapsedTimeUsec() 
				+ ", snap.count=" + activity.getSnapshotCount() 
				+ ", id.count=" + activity.getIdCount()
				+ ", sample.mbeans.count=" + context.getMBeanCount()
				+ ", sample.metric.count=" + context.getLastMetricCount()
				+ ", sample.time.usec=" + context.getLastSampleUsec()
				+ ", exclude.attr.set=" + excAttrs.size()
				+ ", total.noop.count=" + context.getTotalNoopCount()
				+ ", total.exclude.set=" + context.getExcludeAttrCount()
				+ ", total.error.count=" + context.getTotalErrorCount()
				+ ", trackind.id=" + activity.getTrackingId() 
				+ ", mbean.server=" + context.getMBeanServer()
				);
		}
	}

	@Override
    public void error(SampleContext context, AttributeSample sample) {
		sample.excludeNext(true);
		if (trace) {
			out.println("Failed to sample: " + sample.getAttributeInfo() + ", exclude=" + sample.excludeNext() + ", ex=" + sample.getError());
			sample.getError().printStackTrace(out);
		}
		if (sample.excludeNext()) {
			exclude(sample.getAttributeInfo());
		}
    }

	@Override
    public void getStats(SampleContext context, Map<String, Object> stats) {
		stats.put(STAT_TRACE_MODE, trace);
		stats.put(STAT_EXCLUDE_SET_COUNT, excAttrs.size());
	}
}