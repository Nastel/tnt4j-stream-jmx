/*
 * Copyright 2015 Nastel Technologies, Inc.
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
package org.tnt4j.pingjmx;

import org.tnt4j.pingjmx.conditions.AttributeSample;

import com.nastel.jkool.tnt4j.core.Activity;

/**
 * <p> 
 * This interface provides a way to get call backs on every sample:pre, during, post
 * each sample.
 * </p>
 * 
 * @see Pinger
 * @see NestedHandler
 * @see SampleStats
 * 
 * @version $Revision: 1 $
 */
public interface SampleListener {
	/**
	 * This method is called before a sample begins
	 * Set activity <code>OpType</code> to NOOP to ignore
	 * activity reporting. Setting this to NOOP at pre stage
	 * cancels current sample.
	 * 
	 * @param stats sample statistics
	 * @param activity instance
	 */
	void pre(SampleStats stats, Activity activity);
	
	/**
	 * This method is called for each sampled attribute.
	 * Throw a runtime exception if you want samples to halt.
	 * 
	 * @param stats sample statistics
	 * @param sample attribute sample object
	 * @return true of current sampled metric should be included, false otherwise (excluded)
	 */
	boolean sample(SampleStats stats, AttributeSample sample);

	/**
	 * This method is called after current sample is completed
	 * Set activity <code>OpType</code> to NOOP to ignore
	 * activity reporting. Setting this to NOOP at post stage
	 * cancels reporting of the current sample.
	 * 
	 * @param stats sample statistics
	 * @param activity instance
	 */
	void post(SampleStats stats, Activity activity);
}
