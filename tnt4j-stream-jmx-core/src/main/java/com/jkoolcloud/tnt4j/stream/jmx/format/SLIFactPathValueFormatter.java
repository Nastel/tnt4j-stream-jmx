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

package com.jkoolcloud.tnt4j.stream.jmx.format;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * Custom fact path value formatter (Single Level Item) implementation used for an IBM WebSphere Application Server and
 * Liberty JMX sampling. It uses first available level attribute and skips all rest.
 *
 * @version $Revision: 1 $
 */
public class SLIFactPathValueFormatter extends FactPathValueFormatter {
	private static final String[][] PATH_LEVEL_ATTR_KEYS = new String[][] { { "domain" }, { "type" }, { "name" } };

	public SLIFactPathValueFormatter() {
		super();

		pathLevelAttrKeys = PATH_LEVEL_ATTR_KEYS;
	}

	@Override
	protected String getObjNameStr(Map<?, ?> objNameProps) {
		if (pathLevelAttrKeys == null) {
			return super.getObjNameStr(objNameProps);
		}

		StringBuilder pathBuilder = new StringBuilder(256);
		String pv;

		boolean appended;
		for (String[] levelAttrKeys : pathLevelAttrKeys) {
			appended = false;
			for (String pKey : levelAttrKeys) {
				pv = (String) objNameProps.remove(pKey);
				if (!appended && StringUtils.isNotEmpty(pv) && !"null".equals(pv)) {
					pv = Utils.replace(pv, keyReplacements);
					if (pv.startsWith(PATH_DELIM)) {
						pv = pv.substring(1);
					}
					if (pv.endsWith(PATH_DELIM)) {
						pv = pv.substring(0, pv.length() - 1);
					}

					appendPath(pathBuilder, pv);
					appended = true;
				}
			}
		}

		if (!objNameProps.isEmpty()) {
			for (Map.Entry<?, ?> pe : objNameProps.entrySet()) {
				pv = String.valueOf(pe.getValue());
				appendPath(pathBuilder, pv);
			}
		}

		return pathBuilder.toString();
	}

	/**
	 * Initializes default set symbol replacements for an attribute keys.
	 * <p>
	 * Default keys string replacements mapping is:
	 * <ul>
	 * <li>{@code " "} to {@code "_"}</li>
	 * <li>{@code "\""} to {@code "'"}</li>
	 * <li>{@code "/"} to {@code "%"}</li>
	 * <li>{@code "="} to {@code "\"}</li>
	 * <li>{@code ","} to {@code "!"}</li>
	 * <li>{@code "\\"} to {@code "\"}</li>
	 * <li>{@code "#"} to {@code "\"}</li>
	 * <li>{@code "/"} to {@code "\"}</li>
	 * </ul>
	 */
	@Override
	protected void initDefaultKeyReplacements() {
		super.initDefaultKeyReplacements();

		keyReplacements.put("\\\\", PATH_DELIM);
		keyReplacements.put("#", PATH_DELIM);
		keyReplacements.put("/", PATH_DELIM);
	}
}
