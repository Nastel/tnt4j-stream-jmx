/*
 * Copyright 2014-2021 JKOOL, LLC.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.gson.Gson;
import com.jkoolcloud.tnt4j.core.Activity;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.utils.LoggerUtils;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * This class is aggregations facade. It is responsible to load aggregations configuration and perform provided activity
 * aggregations using configuration provided aggregators. Aggregations configuration definition format is JSON.
 * <p>
 * System property to define aggregations configuration file path is
 * {@code "com.jkoolcloud.tnt4j.stream.jmx.aggregations.config"}.
 * <p>
 * Default aggregations configuration file path is {@code "config/aggregations.json"}
 * <p>
 * Aggregations configuration JSON file is like this:
 * 
 * <pre>
 * [
 *     {
 *         aggregator1 config
 *     },
 *     {
 *         aggregator2 config
 *     },
 *     ...
 *     {
 *          aggregatorN config
 *     }
 * ]
 * </pre>
 * 
 * @version $Revision: 1 $
 * 
 * @see com.jkoolcloud.tnt4j.stream.jmx.aggregations.SnapshotAggregator
 */
public class AggregationsManager {
	private static final EventSink LOGGER = LoggerUtils.getLoggerSink(AggregationsManager.class);

	private static final String SYS_PROP_AGGREGATIONS_CONFIG = "com.jkoolcloud.tnt4j.stream.jmx.aggregations.config";
	private static final String DEFAULT_CONFIG_PATH = "config/aggregations.json";
	private static String configPath = System.getProperty(SYS_PROP_AGGREGATIONS_CONFIG, DEFAULT_CONFIG_PATH);

	private static Collection<ActivityAggregator> aggregators = new ArrayList<>();

	static {
		loadConfig(configPath);
	}

	/**
	 * Loads aggregations configuration.
	 *
	 * @param cfgPath
	 *            the aggregations configuration file path
	 */
	@SuppressWarnings("unchecked")
	protected static void loadConfig(String cfgPath) {
		Collection<Map<String, ?>> cfg = new ArrayList<>();
		if (new File(cfgPath).exists()) {
			Gson gson = new Gson();
			try (Reader cfgReader = new BufferedReader(new FileReader(cfgPath))) {
				cfg = gson.fromJson(cfgReader, cfg.getClass());
			} catch (Exception exc) {
				LOGGER.log(OpLevel.ERROR, "Failed to load aggregations configuration file ''{0}''", cfgPath, exc);
			}
		} else {
			String cfgProp = System.getProperty(SYS_PROP_AGGREGATIONS_CONFIG);
			if (cfgProp != null) {
				LOGGER.log(OpLevel.WARNING,
						"System property ''{0}'' defined aggregations configuration file ''{1}'' not found!",
						SYS_PROP_AGGREGATIONS_CONFIG, cfgPath);
			} else {
				LOGGER.log(OpLevel.DEBUG, "Default aggregations configuration ''{0}'' not found!", cfgPath);
			}
		}

		int idx = 0;
		for (Map<String, ?> aggregatorCfg : cfg) {
			idx++;
			String type = (String) aggregatorCfg.get("type");
			String id = (String) aggregatorCfg.get("aggregatorId");
			Boolean enabled = (Boolean) aggregatorCfg.get("enabled");

			if (enabled != null && !enabled) {
				LOGGER.log(OpLevel.DEBUG, "Skipping disabled aggregation loading ''{0}:{1}''", id, type);
				continue;
			}

			try {
				ActivityAggregator aggregator = (ActivityAggregator) Utils.createInstance(type);
				aggregator.setId(StringUtils.isEmpty(id) ? aggregator.getClass().getSimpleName() + idx : id);
				aggregator.configure(aggregatorCfg);
				aggregators.add(aggregator);
			} catch (Exception exc) {
				LOGGER.log(OpLevel.ERROR, "Failed to initiate aggregator ''{0}:{1}''", id, type, exc);
			}
		}
	}

	/**
	 * Performs provided {@code activity} aggregations using configuration defined aggregators.
	 *
	 * @param activity
	 *            e activity instance to perform aggregations
	 */
	public static void aggregate(Activity activity) {
		for (ActivityAggregator aggregator : aggregators) {
			try {
				aggregator.aggregate(activity);
			} catch (Exception exc) {
				LOGGER.log(OpLevel.ERROR, "Failed to apply aggregator ''{0}''", aggregator.getId(), exc);
			}
		}
	}
}
