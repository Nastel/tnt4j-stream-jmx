/*
 * Copyright 2015-2018 JKOOL, LLC.
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
import java.util.Map;

import com.jkoolcloud.tnt4j.stream.jmx.core.SampleListener;

/**
 * <p>
 * This class provides a {@link com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory} implementation with
 * {@link J2EESampleListener} as underlying sample listener implementation.
 * </p>
 *
 * @version $Revision: 1 $
 *
 * @see com.jkoolcloud.tnt4j.stream.jmx.core.Sampler
 * @see J2EESampleListener
 */
public class J2EESamplerFactory extends PlatformSamplerFactory {

	@Override
	public SampleListener newListener(PrintStream pStream, Map<String, ?> properties) {
		return new J2EESampleListener(pStream, properties);
	}

}
