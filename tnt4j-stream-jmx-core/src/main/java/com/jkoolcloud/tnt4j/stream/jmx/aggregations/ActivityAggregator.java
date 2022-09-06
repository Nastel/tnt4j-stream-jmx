/*
 * Copyright 2014-2022 JKOOL, LLC.
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

import java.util.Map;

import com.jkoolcloud.tnt4j.core.Activity;

/**
 * The interface Activity aggregator.
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.aggregations.SnapshotAggregator
 */
public interface ActivityAggregator {
	/**
	 * Sets aggregator identifier.
	 *
	 * @param id
	 *            the aggregator identifier
	 */
	void setId(String id);

	/**
	 * Gets aggregator identifier.
	 *
	 * @return the aggregator identifier
	 */
	String getId();

	/**
	 * Configures this aggregator using {@code cfg} provided configuration.
	 *
	 * @param cfg
	 *            the configuration map
	 * @throws IllegalArgumentException
	 *             if configuration is illegal or malformed
	 */
	void configure(Map<String, ?> cfg) throws IllegalArgumentException;

	/**
	 * Performs provided {@code activity} aggregations.
	 *
	 * @param activity
	 *            the activity instance to perform aggregations
	 * @return aggregated activity instance, in most cases same as provided one
	 */
	Activity aggregate(Activity activity);
}
