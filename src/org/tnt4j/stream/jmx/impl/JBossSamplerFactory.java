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
package org.tnt4j.stream.jmx.impl;

import javax.management.MBeanServer;

import org.tnt4j.stream.jmx.core.Sampler;
import org.tnt4j.stream.jmx.factory.SamplerFactory;

/**
 * <p> 
 * This class provides a {@link SamplerFactory} implementation
 * with {@code JBossJmxSampler} as underlying sampler implementation.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see Sampler
 * @see JBossJmxSampler
 */
public class JBossSamplerFactory implements SamplerFactory {

	@Override
	public Sampler newInstance() {
		return new JBossJmxSampler();
	}

	@Override
	public Sampler newInstance(MBeanServer mserver) {
		return new JBossJmxSampler(mserver);
	}
}
