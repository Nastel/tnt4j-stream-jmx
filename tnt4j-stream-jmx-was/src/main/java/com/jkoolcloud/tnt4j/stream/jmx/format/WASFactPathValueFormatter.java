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

import java.util.Properties;

import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * Custom fact path value formatter implementation for a WebSphere Application Server.
 *
 * @version $Revision: 1 $
 */
public class WASFactPathValueFormatter extends FactPathValueFormatter {
	private static final String[] SHORT_NAME_KEYS = new String[] { "mbeanIdentifier", "name", "type" };

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

		Properties props = makeProps(objCanonName, ddIdx);
		StringBuilder pathBuilder = new StringBuilder();
		String pv = (String) props.remove("domain");

		super.appendPath(pathBuilder, pv);

		for (String snKey : SHORT_NAME_KEYS) {
			pv = (String) props.remove(snKey);
			if (!Utils.isEmpty(pv) && !"null".equals(pv)) {
				pv = pv.replace('#', '\\');
				pv = pv.replace('/', '\\');
				pv = replace(pv, "\\\\", FactNameValueFormatter.PATH_DELIM);
				if (pv.startsWith(FactNameValueFormatter.PATH_DELIM)) {
					pv = pv.substring(1);
				}
				if (pv.endsWith(FactNameValueFormatter.PATH_DELIM)) {
					pv = pv.substring(0, pv.length() - 1);
				}

				pathBuilder.append(pathBuilder.length() > 0 ? FactNameValueFormatter.PATH_DELIM : "").append(pv);
				break;
			}
		}

		return pathBuilder.toString();
	}

	public static String replace(String in, String o, String n) {
		StringBuilder buffer = new StringBuilder(2048);

		int start = 0;
		for (;;) {
			int index = in.indexOf(o, start);
			if (index == -1)
				break;
			buffer.append(in.substring(start, index));
			buffer.append(n);
			start = index + o.length();
		}
		buffer.append(in.substring(start));
		return buffer.toString();
	}
}
