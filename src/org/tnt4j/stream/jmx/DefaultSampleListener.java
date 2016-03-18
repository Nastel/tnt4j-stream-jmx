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
	boolean trace = false;
	PrintStream out;
	
	public DefaultSampleListener(PrintStream pstream, boolean trace) {
		this.trace = trace;
		this.out = pstream;
	}
	
	@Override
	public void pre(SampleContext context, Activity activity) {
	}

	@Override
	public boolean sample(SampleContext context, AttributeSample sample) {
		return true;
	}

	@Override
	public void post(SampleContext context, Activity activity) {
		if (trace) {
			out.println(activity.getName()
				+ ": sample.count=" + context.getSampleCount()
				+ ", mbean.count=" + context.getMBeanServer().getMBeanCount()
				+ ", elasped.usec=" + activity.getElapsedTimeUsec() 
				+ ", snap.count=" + activity.getSnapshotCount() 
				+ ", id.count=" + activity.getIdCount()
				+ ", noop.count=" + context.getTotalNoopCount()
				+ ", sample.mbeans.count=" + context.getMBeanCount()
				+ ", sample.metric.count=" + context.getLastMetricCount()
				+ ", sample.time.usec=" + context.getLastSampleUsec()
				+ ", exclude.attrs=" + context.getExcludeAttrCount()
				+ ", trackind.id=" + activity.getTrackingId() 
				+ ", mbean.server=" + context.getMBeanServer()
				);
		}
	}

	@Override
    public void error(SampleContext context, AttributeSample sample) {
		if (trace) {
			out.println("Failed to sample: " + sample.getAttributeInfo() + ", ex=" + sample.getError());
			sample.getError().printStackTrace(out);
		}
    }
}