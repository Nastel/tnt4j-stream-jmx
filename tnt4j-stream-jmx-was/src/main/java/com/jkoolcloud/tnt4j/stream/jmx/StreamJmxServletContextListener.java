/*
 * Copyright 2015-2017 JKOOL, LLC.
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

package com.jkoolcloud.tnt4j.stream.jmx;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.jkoolcloud.tnt4j.stream.jmx.utils.ConsoleOutputCaptor;
import com.jkoolcloud.tnt4j.utils.Utils;

/**
 * JMX stream {@link SamplingAgent} integration into WAS as HTTP servlet starting JMX sampling on servlet context
 * initialized and stopping sampling on servlet context destroyed.
 * <p>
 * Also allows configuring over WAS admin console.
 *
 * @version $Revision: 1 $
 *
 * @see ServletContextListener#contextInitialized(ServletContextEvent)
 * @see ServletContextListener#contextDestroyed(ServletContextEvent)
 * @see SamplingAgent#connect(String, String)
 * @see SamplingAgent#destroy()
 * @see HttpServlet#doGet(HttpServletRequest, HttpServletResponse)
 * @see HttpServlet#doPost(HttpServletRequest, HttpServletResponse)
 */
public class StreamJmxServletContextListener extends HttpServlet implements ServletContextListener {
	private static final long serialVersionUID = -8291650473147748942L;

	private static final String JMX_SAMPLER_FACTORY_KEY = "com.jkoolcloud.tnt4j.stream.jmx.sampler.factory";
	private static final String VALIDATE_TYPES_KEY = "com.jkoolcloud.tnt4j.stream.jmx.agent.validate.types";
	private static final String TRACE_KEY = "com.jkoolcloud.tnt4j.stream.jmx.agent.trace";
	private static final String AO_KEY = "com.jkoolcloud.tnt4j.stream.jmx.agent.options";
	private static final String VM_KEY = "com.jkoolcloud.tnt4j.stream.jmx.agent.vm";

	private static final String DEFAULT_TNT4J_PROPERTIES = "tnt4j.properties";
	private static final String DEFAULT_AO = "*:*!!10000!0";
	private static final String DEFAULT_VM = "service:jmx:iiop://localhost:2809/jndi/JMXConnector";
	private static final String TNT4J_CONFIG_KEY = "tnt4j.config";
	private static final String[] displayProperties = new String[] { TNT4J_CONFIG_KEY, VM_KEY, AO_KEY,
			JMX_SAMPLER_FACTORY_KEY, TRACE_KEY, /* VALIDATE_TYPES_KEY */ };

	private String vm = null;
	private String ao = null;
	private String tntConfig = null;

	private static ConsoleOutputCaptor console = new ConsoleOutputCaptor();

	private Thread sampler;

	static {
		console.start();
	}

	@Override
	public void contextInitialized(ServletContextEvent event) {
		System.out.println(
				"###############################     Starting TNT4J-stream-JMX as Servlet   ###############################");
		try {
			System.setProperty(TRACE_KEY, "true");
			System.setProperty(VALIDATE_TYPES_KEY, "false");
			System.setProperty(JMX_SAMPLER_FACTORY_KEY, "com.jkoolcloud.tnt4j.stream.jmx.impl.WASSamplerFactory");

			samplerStart();
		} catch (Exception e) {
			System.out.println("!!!!!!!!!!!!!!!!!!!!!!     Failed to start TNT4J-stream-JMX    !!!!!!!!!!!!!!!!!!!!!!");
			e.printStackTrace(System.out);
		}

	}

	private void samplerStart() {
		configure();
		sampler = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println(
							"-------------------------------     Connecting Sampler Agent     -------------------------------");
					SamplingAgent.connect(vm, ao);
				} catch (Exception e) {
					System.out.println(
							"!!!!!!!!!!!!!!!!!!!!!!     Failed to connect Sampler Agent    !!!!!!!!!!!!!!!!!!!!!!");
					e.printStackTrace(System.out);
				}
			}
		});
		sampler.start();
	}

	private void configure() {
		try {
			tntConfig = System.getProperty(TNT4J_CONFIG_KEY);
			vm = System.getProperty(VM_KEY);
			ao = System.getProperty(AO_KEY);
		} catch (SecurityException e) {
			// TODO: handle exception
		} finally {
			if (StringUtils.isEmpty(tntConfig)) {
				System.setProperty(TNT4J_CONFIG_KEY, DEFAULT_TNT4J_PROPERTIES);
			}
			if (StringUtils.isEmpty(vm)) {
				vm = DEFAULT_VM;
			}
			System.setProperty(VM_KEY, vm);
			if (StringUtils.isEmpty(ao)) {
				ao = DEFAULT_AO;
			}
			System.setProperty(AO_KEY, ao);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		System.out.println(
				"###############################     Stopping TNT4J-stream-JMX as Servlet   ###############################");
		samplerDestroy();
		console.stop();
	}

	private static void samplerDestroy() {
		System.out.println(
				"-------------------------------     Destroying Sampler Agent     -------------------------------");
		SamplingAgent.destroy();
	}

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
		out.println("<tr>");

		out.println("<tr>");
		out.println("</table><br>");

		out.println("<input type=\"submit\" value=\"Submit\"><br>");
		out.println("</form>");
		out.println("<br>");
		out.println("<form action=\"" + req.getServletPath() + "\" method=\"post\">");

		outputTNT4JConfig(out);
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
		for (String property : displayProperties) {
			String propertySet = req.getParameter(property);
			if (propertySet != null) {
				System.setProperty(property, propertySet);
				System.out.println("Setting property: " + property + " value: " + propertySet);
				set = true;
			}
		}
		if (set) {
			samplerDestroy();
			samplerStart();
		}
		resp.sendRedirect(req.getContextPath());
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
