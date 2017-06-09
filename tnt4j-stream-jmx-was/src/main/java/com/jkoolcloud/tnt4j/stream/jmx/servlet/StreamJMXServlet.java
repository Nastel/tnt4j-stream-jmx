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

package com.jkoolcloud.tnt4j.stream.jmx.servlet;

import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXManager.*;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.io.*;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * Provides Stream-JMX {@link com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent} used configuration viewing/editing and
 * displays console output. After configuration gets changed, current run of sampling gets stopped and started again
 * applying new configuration.
 *
 * @version $Revision: 1 $
 *
 * @see HttpServlet#doGet(HttpServletRequest, HttpServletResponse)
 * @see HttpServlet#doPost(HttpServletRequest, HttpServletResponse)
 */
public class StreamJMXServlet extends HttpServlet {
	private static final long serialVersionUID = -8291650473147748942L;

	private static final String TNT4JJMX_PROPERTIES_FILE_NAME = "tnt4jjmx.properties";

	private static final String[] displayProperties = new String[] { TNT4J_CONFIG_KEY, VM_KEY, AO_KEY,
			JMX_SAMPLER_FACTORY_KEY, TRACE_KEY, /* VALIDATE_TYPES_KEY */ };

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		PrintWriter out = resp.getWriter();
		out.println("<html>");
		out.println("<body>");
		out.println("<h1>TNT4J-Stream-JMX</h1>");

		out.println("<form action=\"" + req.getServletPath() + "\" method=\"post\">");
		out.println("<table>");

		out.println("<tr>");
		out.println("<th>Property</th>");
		out.println("<th>Value</th>");
		out.println("<th>Permission</th>");
		out.println("</tr>");
		for (String property : displayProperties) {
			out.println("<tr>");
			out.println("<td>" + property + " </td>");

			out.println("<td><input type=\"text\" name=\"" + property + "\" value=\""
					+ escapeHtml4(System.getProperty(property)) + "\"></td>");
			out.println("<td> " + propertyPermitted(property) + "  </td>");
			out.println("</tr>");
		}

		SecurityManager securityManager = System.getSecurityManager();
		boolean permittedChangeIO;
		try {
			securityManager.checkPermission(new RuntimePermission("setIO"));
			permittedChangeIO = true;
		} catch (SecurityException e) {
			permittedChangeIO = false;
		} catch (NullPointerException e) {
			permittedChangeIO = true;
		}

		out.println("<tr><td>Permission SetIO </td> <td></td>");
		out.println("<td>" + permittedChangeIO + " </td>");
		out.println("</tr>");

		out.println("</table><br>");

		out.println("<br>");
		out.println("<input type=\"submit\" value=\"Submit\"><br>");
		out.println("</form>");
		out.println("<br>");
		out.println("<form action=\"" + req.getServletPath() + "\" method=\"post\">");

		outputTNT4JConfig(out);
		out.println("<br>");
		out.println("<input type=\"submit\" value=\"Submit tnt4j.config\"><br>");
		out.println("<br>");
		outputConsole(out);

		out.println("</form>");

		out.println("</body>");
		out.println("</html>");
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		boolean set = false;
		StreamJMXManager manager = StreamJMXManager.getInstance();
		final Properties persistedProperties = manager.loadPersistedProperties();
		for (String property : displayProperties) {
			String propertySet = req.getParameter(property);
			if (propertySet != null) {
				System.setProperty(property, propertySet);
				if (!(property.equals(TNT4J_CONFIG_KEY) && StringUtils.containsAny(propertySet, ";#:={}"))) {
					persistedProperties.setProperty(property, propertySet);
				} else {
					FileOutputStream tnt4jProperties = null;
					try {
						tnt4jProperties = new FileOutputStream(
								getClass().getClassLoader().getResource(DEFAULT_TNT4J_PROPERTIES).getFile());
						tnt4jProperties.write(propertySet.getBytes());
						System.setProperty(TNT4J_CONFIG_KEY, DEFAULT_TNT4J_PROPERTIES);
					} catch (Exception e) {
						System.out.println("!!!!!!!!!!!!!!!!!!!!!!     Failed writing " + DEFAULT_TNT4J_PROPERTIES
								+ "     !!!!!!!!!!!!!!!!!!!!!!");
					} finally {
						Utils.close(tnt4jProperties);
					}
				}
				System.out.println("Setting property: " + property + " value: " + propertySet);
				set = true;
			}
		}

		try {
			if (set) {
				manager.samplerDestroy();
				manager.samplerStart();
			}
		} catch (Exception e) {
			System.out.println("!!!!!!!!!!!!!!!!!!!!!!     Failed restart TNT4J-stream-JMX    !!!!!!!!!!!!!!!!!!!!!!");
			e.printStackTrace(System.out);
		}

		FileOutputStream file = null;
		try {
			file = new FileOutputStream(
					getClass().getClassLoader().getResource(TNT4JJMX_PROPERTIES_FILE_NAME).getFile());
			persistedProperties.store(file, "SAVED FROM WEB");
		} catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println("Cannot save properties to file: " + file);
		} finally {
			resp.sendRedirect(req.getContextPath());
			Utils.close(file);
		}
	}

	private void outputTNT4JConfig(PrintWriter out) throws IOException {
		out.println("<H3>TNT4J config</H3>");
		out.println("<textarea name=\"" + TNT4J_CONFIG_KEY + "\" cols=\"120\" rows=\"55\">");

		final String tnt4JConfig = System.getProperty(TNT4J_CONFIG_KEY);

		String tnt4jConfigString;
		if (StringUtils.containsAny(tnt4JConfig, ";#:={}")) {
			tnt4jConfigString = tnt4JConfig;
		} else {
			tnt4jConfigString = getString(getClass().getClassLoader().getResourceAsStream(tnt4JConfig));
		}
		try {
			out.println(tnt4jConfigString);
		} catch (Exception e) {
			out.println("No tnt4j.properties found: " + tnt4JConfig);
		}
		out.println("</textarea>");
	}

	private static void outputConsole(PrintWriter out) throws IOException {
		out.println("<H3>Console Output</H3>");
		out.println("<textarea name=\"tnt4jConfig\" cols=\"120\" rows=\"55\">");
		try {
			out.println(console.getCaptured());
		} catch (Exception e) {
			out.println("N/A");
			out.println(e.getMessage());
			e.printStackTrace(out);
		}
		out.println("</textarea>");
	}

	private static String getString(InputStream inputStream) throws IOException {
		try {
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int length;
			while ((length = inputStream.read(buffer)) != -1) {
				result.write(buffer, 0, length);
			}
			return result.toString(Utils.UTF8);
		} catch (Exception e) {
			return "N/A";
		}
	}

	private static boolean propertyPermitted(String property) {
		SecurityManager securityManager = System.getSecurityManager();
		boolean permitted;
		try {
			securityManager.checkPropertyAccess(property);
			permitted = true;
		} catch (SecurityException e) {
			permitted = false;
		} catch (NullPointerException e) {
			permitted = true; // NO security manager
		}
		return permitted;
	}
}
