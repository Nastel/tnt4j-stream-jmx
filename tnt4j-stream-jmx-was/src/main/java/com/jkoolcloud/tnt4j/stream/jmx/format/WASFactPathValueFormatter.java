/*
 * Copyright 2014-2017 JKOOL, LLC.
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

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * Custom fact path value formatter implementation for a WebSphere Application Server.
 *
 * @version $Revision: 1 $
 */
public class WASFactPathValueFormatter extends FactPathValueFormatter {
	private static final String[] SHORT_NAME_KEYS = new String[] { "mbeanIdentifier", "name", "type" };

	private String[] replaceable = new String[] { "\\\\", "#", "/" };
	private String[] replacement = new String[] { PATH_DELIM, "\\", "\\" };

	public WASFactPathValueFormatter() {
		super();

		useAllProperties = false;
	}

	@Override
	protected String getSnapNameStr(String objCanonName) {
		if (Utils.isEmpty(objCanonName)) {
			return objCanonName;
		}

		int ddIdx = objCanonName.indexOf(':');

		if (ddIdx == -1) {
			return objCanonName;
		}

		synchronized (objNameProps) {
			loadProps(objNameProps, objCanonName, ddIdx);
			StringBuilder pathBuilder = new StringBuilder(256);
			String pv = (String) objNameProps.remove("domain");

			super.appendPath(pathBuilder, pv);

			for (String snKey : SHORT_NAME_KEYS) {
				pv = (String) objNameProps.remove(snKey);
				if (!Utils.isEmpty(pv) && !"null".equals(pv)) {
					pv = StringUtils.replaceEach(pv, replaceable, replacement);
					if (pv.startsWith(PATH_DELIM)) {
						pv = pv.substring(1);
					}
					if (pv.endsWith(PATH_DELIM)) {
						pv = pv.substring(0, pv.length() - 1);
					}

					pathBuilder.append(pathBuilder.length() > 0 ? PATH_DELIM : "").append(pv);
					break;
				}
			}

			return pathBuilder.toString();
		}
	}
}
