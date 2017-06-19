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

import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXManager.console;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXManager.Display;
import com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXManager.Scope;
import com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXManager.StreamJMXProperties;
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
		for (StreamJMXProperties property : StreamJMXProperties.values(Display.EDITABLE)) {
			printTableLine(out, property, true, false);
		}
		for (StreamJMXProperties property : StreamJMXProperties.values(Display.EDITABLE_PW)) {
			printTableLine(out, property, true, true);
		}
		printTableSplitLine(out);
		for (StreamJMXProperties property : StreamJMXProperties.values(Display.READ_ONLY)) {
			printTableLine(out, property, false, false);
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

	private static void printTableLine(PrintWriter out, StreamJMXProperties property, boolean editable,
			boolean password) {
		String propertyKey = property.key;
		out.println("<tr>");
		out.println("<td>" + propertyKey + " </td>");

		String editableStr = editable ? "" : "readonly disabled";
		String inputType = password ? "password" : "text";
		String text = escapeHtml4(getPropertyOrEmpty(propertyKey));
		if (password) {
			text = text.replaceAll(".", "*");
		}

		out.println("<td><input type=\"" + inputType + "\" name=\"" + propertyKey + "\" value=\"" + text + "\" "
				+ editableStr + " size=\"60\"></td>");
		out.println("<td> ");

		if (property.isInScope(Scope.SYSTEM)) {
			out.print(propertyPermitted(propertyKey));
		} else {
			out.print("&nbsp");
		}

		out.println("  </td>");
		out.println("</tr>");
	}

	private static String getPropertyOrEmpty(String property) {
		return StreamJMXManager.getInstance().getProperty(property);
	}

	private static void printTableSplitLine(PrintWriter out) {
		out.println("<tr>");
		out.println("<td>&nbsp;</td>");
		out.println("<td>&nbsp;</td>");
		out.println("<td>&nbsp;</td>");
		out.println("</tr>");
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		boolean set = false;
		boolean setTNTFile = false;
		StreamJMXManager manager = StreamJMXManager.getInstance();
		Properties currentProperties = manager.getCurrentProperties();
		for (StreamJMXProperties property : StreamJMXProperties.values(Display.EDITABLE, Display.EDITABLE_PW)) {
			String propertyValue = req.getParameter(property.key);
			if (propertyValue != null) {
				boolean propertyIsSet = manager.setProperty(property, propertyValue);
				set = !set ? propertyIsSet : set;
				System.out.println("==> Setting property: name=" + property.key + ", value=" + propertyValue);
			}
		}
		final String tnt4jConfigContents = req.getParameter(StreamJMXProperties.TNT4J_CONFIG_CONTS.key);
		if (tnt4jConfigContents != null) {
			FileOutputStream tnt4jProperties = null;
			try {
				tnt4jProperties = new FileOutputStream(getClass().getClassLoader()
						.getResource(StreamJMXProperties.TNT4J_CONFIG.defaultValue).getFile());
				tnt4jProperties.write(tnt4jConfigContents.getBytes());
				System.setProperty(StreamJMXProperties.TNT4J_CONFIG.key, StreamJMXProperties.TNT4J_CONFIG.defaultValue);
				setTNTFile = true;
			} catch (Exception e) {
				System.out.println("!!!!  Failed writing " + StreamJMXProperties.TNT4J_CONFIG.defaultValue + "  !!!!");
			} finally {
				Utils.close(tnt4jProperties);
			}
		}

		if (set || setTNTFile) {
			manager.samplerDestroy();
			manager.samplerStart();
		}

		resp.sendRedirect(req.getContextPath());
	}

	private static void outputTNT4JConfig(PrintWriter out) throws IOException {
		out.println("<H3>TNT4J config</H3>");
		out.println("<textarea name=\"" + StreamJMXProperties.TNT4J_CONFIG_CONTS.key + "\" cols=\"120\" rows=\"55\">");

		final String tnt4JConfig = StreamJMXManager.getInstance().getProperty(StreamJMXProperties.TNT4J_CONFIG.key);

		String tnt4jConfigString;
		if (tnt4JConfig.startsWith(TrackerConfigStore.CFG_LINE_PREFIX)) {
			tnt4jConfigString = tnt4JConfig;
		} else {
			tnt4jConfigString = getString(StreamJMXServlet.class.getClassLoader().getResourceAsStream(tnt4JConfig));
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

	@Override
	public void init(ServletConfig config) throws ServletException {
		getPropertiesFromContext(config);
		initStream();
		super.init(config);
	}

	private static void getPropertiesFromContext(ServletConfig servletConfig) {
		StreamJMXManager manager = StreamJMXManager.getInstance();
		System.out.println(">>>>>> Getting initial parameter from servlet context");
		final Enumeration<String> initParameterNames = servletConfig.getInitParameterNames();
		while (initParameterNames.hasMoreElements()) {
			String initParam = (String) initParameterNames.nextElement();
			System.out.println("==> Parameter found: " + initParam);
		}
		for (StreamJMXProperties prop : StreamJMXProperties.values()) {
			final String initParameter = servletConfig.getInitParameter(prop.key);
			if (initParameter != null) {
				manager.setProperty(prop, initParameter);
				System.out.println("==> Property: " + prop.key + " value changed to: " + initParameter);
			}
		}

		System.out.println("<<<<<< End of servlet context parameter initialisation");
	}

	private static void initStream() {
		console.start();

		System.out.println("######################     Starting TNT4J-stream-JMX as Servlet   ######################");
		try {
			System.out.println(">>>>>>>>>>>>>>>>>>    TNT4J-stream-JMX environment check start   >>>>>>>>>>>>>>>>>>");
			System.out.println("==> J2EE: " + getClassLocation("javax.management.j2ee.statistics.Statistic"));
			System.out.println("==> IBM.ORB: " + getClassLocation("com.ibm.CORBA.MinorCodes"));
			System.out.println("==> IBM.EJB.THIN.CLIENT: " + getClassLocation("com.ibm.tx.TranConstants"));
			System.out.println("==> IBM.ADMIN.CLIENT: " + getClassLocation("com.ibm.ws.pmi.j2ee.StatisticImpl"));
			System.out.println("<<<<<<<<<<<<<<<<<<<    TNT4J-stream-JMX environment check end   <<<<<<<<<<<<<<<<<<<");

			StreamJMXManager manager = StreamJMXManager.getInstance();

			manager.samplerStart();
		} catch (Exception e) {
			System.out.println("!!!!!!!!!!!!     Failed to start TNT4J-stream-JMX    !!!!!!!!!!!!");
			e.printStackTrace(System.out);
		}
	}

	private static URL getClassLocation(Class<?> clazz) {
		return clazz.getResource('/' + clazz.getName().replace('.', '/') + ".class");
	}

	private static URL getClassLocation(String clazzName) {
		return StreamJMXServlet.class.getResource('/' + clazzName.replace('.', '/') + ".class");
	}

	@Override
	public void destroy() {
		System.out.println("######################     Stopping TNT4J-stream-JMX as Servlet   ######################");
		StreamJMXManager manager = StreamJMXManager.getInstance();
		manager.samplerDestroy();

		super.destroy();

		Exception last = null;
		for (StreamJMXProperties prop : StreamJMXProperties.values()) {
			final String propertyValue = manager.getProperty(prop.key);

			try {
				System.out.println("==> Saving parameters to servlet context...");
				final Method method = getServletConfig().getClass().getMethod("setInitParameter", String.class,
						String.class);

				method.invoke(getServletConfig(), prop.key, propertyValue);
			} catch (Exception e) {
				System.out.println("!!!!!   Save failed " + e.getClass().getName() + " " + e.getMessage() + "   !!!!!");
				last = e;
			}
		}
		if (last != null)
			last.printStackTrace();

		console.stop();
	}
}
