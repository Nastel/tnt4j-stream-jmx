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

import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;

/**
 * <p>
 * This class provides a {@link com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory} implementation with
 * {@link WASJmxSampler} as underlying sampler implementation, {@link WASPMISampleListener} as sample listener and
 * {@link com.jkoolcloud.tnt4j.stream.jmx.format.SLIFactPathValueFormatter} as samples formatter.
 * </p>
 *
 * @version $Revision: 1 $
 *
 * @see WASPMISampleListener
 */
public class WASPMISamplerFactory extends WASSamplerFactory {

	@Override
	public SampleListener newListener(Map<String, ?> properties) {
		return new WASPMISampleListener(properties);
	}
}
