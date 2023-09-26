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

package com.jkoolcloud.tnt4j.stream.jmx.scheduler;

import com.jkoolcloud.tnt4j.ActivityTask;
import com.jkoolcloud.tnt4j.TrackingLogger;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.tracker.TrackingActivity;

/**
 * This class implements a runnable task implementation scheduled by
 * {@link com.jkoolcloud.tnt4j.stream.jmx.scheduler.SchedulerImpl}.
 * 
 * @version $Revision: 1 $
 */
public class SampleActivityTask extends ActivityTask {

	/**
	 * Create a sampling task for a specific logger.
	 * 
	 * @param lg
	 *            tracking logger instance
	 */
	protected SampleActivityTask(TrackingLogger lg) {
		super(lg);
	}

	/**
	 * Create a sampling task for a specific logger and activity name.
	 * 
	 * @param lg
	 *            tracking logger instance
	 * @param name
	 *            activity name
	 */
	protected SampleActivityTask(TrackingLogger lg, String name) {
		super(lg, name);
	}

	/**
	 * Create a sampling task for a specific logger, activity name and severity.
	 * 
	 * @param lg
	 *            tracking logger instance
	 * @param name
	 *            activity name
	 * @param level
	 *            severity level
	 */
	protected SampleActivityTask(TrackingLogger lg, String name, OpLevel level) {
		super(lg, name, level);
	}

	@Override
	protected long endActivity() {
		activity.stop();
		postActivity(activity);
		return activity.getElapsedTimeUsec();
	}

	/**
	 * Posts provided activity over bound tracking logger.
	 * 
	 * @param tActivity
	 *            activity instance to post
	 * 
	 * @see #doSample(com.jkoolcloud.tnt4j.tracker.TrackingActivity)
	 * @see com.jkoolcloud.tnt4j.TrackingLogger#tnt(com.jkoolcloud.tnt4j.tracker.TrackingActivity)
	 */
	public void postActivity(TrackingActivity tActivity) {
		if (doSample(tActivity)) {
			logger.tnt(tActivity);
		}
	}

	/**
	 * Checks if provided activity shall be tracked.
	 * 
	 * @param tActivity
	 *            activity instance to perform check
	 * @return {@code true} to track current activity, {@code false} to ignore
	 * 
	 * @see #doSample()
	 */
	protected boolean doSample(TrackingActivity tActivity) {
		return doSample();
	}
}
