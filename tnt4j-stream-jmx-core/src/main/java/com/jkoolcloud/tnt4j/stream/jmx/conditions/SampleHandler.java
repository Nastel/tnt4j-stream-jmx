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
package com.jkoolcloud.tnt4j.stream.jmx.conditions;

import com.jkoolcloud.tnt4j.core.ActivityListener;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleContext;
import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;
import com.jkoolcloud.tnt4j.stream.jmx.scheduler.Scheduler;

/**
 * <p>
 * This interface defines {@link SampleHandler} which provides a way to conditionally process activities.
 * </p>
 * 
 * @see AttributeCondition
 * @see AttributeAction
 * @version $Revision: 1 $
 * 
 */
public interface SampleHandler extends ActivityListener, NestedHandler<SampleHandler, SampleListener> {
	public static final String CFG_MBEAN_SERVER = "SH_CFG_MBEAN_SERVER_CONNECTION";
	public static final String CFG_INCLUDE_FILTER = "SH_CFG_INCLUDE_FILTER";
	public static final String CFG_EXCLUDE_FILTER = "SH_CFG_EXCLUDE_FILTER";
	public static final String CFG_SOURCE = "SH_CFG_SOURCE";
	public static final String CFG_BATCH_SIZE = "SH_CFG_BATCH_SIZE";
	public static final String CFG_JMX_CONNECTOR = "SH_CFG_JMX_CONNECTOR";

	/**
	 * Register and associate condition with an action
	 * 
	 * @param cond
	 *            user defined condition with {@link NoopAction}
	 * @return this handler instance
	 */
	SampleHandler register(AttributeCondition cond);

	/**
	 * Register and associate condition with an action
	 * 
	 * @param cond
	 *            user defined condition
	 * @param action
	 *            action to be triggered when condition evaluates to true
	 * @return this handler instance
	 */
	SampleHandler register(AttributeCondition cond, AttributeAction action);

	/**
	 * Obtain sample context associated with the handler
	 *
	 * @return sample context associated with the handler
	 */
	SampleContext getContext();

	/**
	 * Releases handler associated resources.
	 */
	void cleanup();

	/**
	 * Binds scheduler instance to this sample handler.
	 * 
	 * @param scheduler
	 *            scheduler instance to bind
	 */
	void setScheduler(Scheduler scheduler);
}
