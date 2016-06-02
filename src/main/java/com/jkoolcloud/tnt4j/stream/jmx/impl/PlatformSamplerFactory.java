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
package com.jkoolcloud.tnt4j.stream.jmx.impl;

import javax.management.MBeanServer;

import com.jkoolcloud.tnt4j.stream.jmx.core.Sampler;
import com.jkoolcloud.tnt4j.stream.jmx.factory.SamplerFactory;

/**
 * <p> 
 * This class provides a {@link SamplerFactory} implementation
 * with {@link PlatformJmxSampler} as underlying sampler implementation.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see Sampler
 * @see PlatformJmxSampler
 */
public class PlatformSamplerFactory implements SamplerFactory {
	
	@Override
	public Sampler newInstance() {
		return new PlatformJmxSampler();
	}

	@Override
	public Sampler newInstance(MBeanServer mserver) {
		return new PlatformJmxSampler(mserver);
	}
}
