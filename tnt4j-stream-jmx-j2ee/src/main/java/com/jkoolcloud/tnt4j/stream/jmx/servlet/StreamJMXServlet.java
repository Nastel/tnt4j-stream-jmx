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

package com.jkoolcloud.tnt4j.stream.jmx.servlet;

import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXProperty.Display.*;
import static com.jkoolcloud.tnt4j.stream.jmx.servlet.StreamJMXProperty.Scope.*;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import com.jkoolcloud.tnt4j.config.TrackerConfigStore;
import com.jkoolcloud.tnt4j.core.OpLevel;
import com.jkoolcloud.tnt4j.sink.EventSink;
import com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent;
import com.jkoolcloud.tnt4j.stream.jmx.utils.TextAreaLogAppender;
import com.jkoolcloud.tnt4j.stream.jmx.utils.Utils;

/**
 * Base Stream-JMX servlet class providing JMX attributes sampling over
 * {@link com.jkoolcloud.tnt4j.stream.jmx.SamplingAgent}. Servlet itself allows to view/edit {@link SamplingAgent} and
 * TNT4J configuration viewing/editing and monitor Stream-JMX console output.
 * <p>
 * After configuration gets changed, current run of sampling gets stopped and started again applying new configuration.
 *
 * @version $Revision: 1 $
 *
 * @see HttpServlet#init(ServletConfig)
 * @see HttpServlet#doGet(HttpServletRequest, HttpServletResponse)
 * @see HttpServlet#doPost(HttpServletRequest, HttpServletResponse)
 * @see HttpServlet#destroy()
 * @see StreamJMXProperties
 */
public abstract class StreamJMXServlet extends HttpServlet {
	private static final long serialVersionUID = 6290613044594221667L;

	public static final String TNT4J_LOGGER_OUTPUT = "tnt4j_logger_output";

	static {
		TextAreaLogAppender.getInstance().start();
	}

	protected StreamJMXProperty[] servletProperties;
	private Properties inAppCfgProperties = new Properties();
	private static Thread sampler;

	@Override
	public void init(ServletConfig config) throws ServletException {
		servletProperties = initProperties();
		getPropertiesFromContext(config);
		initSubject();
		initStream();

		super.init(config);
	}

	protected abstract EventSink logger();

	protected StreamJMXProperty[] initProperties() {
		return StreamJMXProperties.values();
	}

	protected void initSubject() {
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try (PrintWriter out = resp.getWriter()) {
			if (req.getRequestURI().endsWith("/js/tntJmx.js")) {
				resp.setContentType("application/javascript");
				out.write(getString(Utils.getResourceAsStream(StreamJMXServlet.class, "/static/js/tntJmx.js")));
				out.flush();
				return;
			}

			if (req.getRequestURI().endsWith("/css/tntJmx.css")) {
				resp.setContentType("text/css");
				out.write(getString(Utils.getResourceAsStream(StreamJMXServlet.class, "/static/css/tntJmx.css")));
				out.flush();
				return;
			}

			out.println("<html>");
			out.println("<head>");
			outputStyle(out, req.getContextPath());
			outputScript(out, req.getContextPath());
			out.println("</head>");
			out.println("<body>");
			out.println("<h1>TNT4J-Stream-JMX</h1>");

			out.println("<form action=\"" + req.getServletPath() + "\" method=\"post\">");
			out.println("<table>");

			out.println("<tr>");
			out.println("<th>Property</th>");
			out.println("<th>Value</th>");
			out.println("<th>Permission</th>");
			out.println("</tr>");
			for (StreamJMXProperty property : StreamJMXProperties.values(servletProperties, EDITABLE)) {
				printTableLine(out, property, true, false);
			}
			for (StreamJMXProperty property : StreamJMXProperties.values(servletProperties, EDITABLE_PW)) {
				printTableLine(out, property, true, true);
			}
			printTableSplitLine(out);
			for (StreamJMXProperty property : StreamJMXProperties.values(servletProperties, READ_ONLY)) {
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

			outputTabs(out, req.getServletPath());

			outputLogger(out);

			out.println("<input type=\"button\" value=\"Refresh log\" onClick=\"window.location.reload(true)\">");
			out.println("</body>");
			out.println("</html>");

			out.flush();
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		boolean changed = false;
		for (StreamJMXProperty property : StreamJMXProperties.values(servletProperties, EDITABLE, EDITABLE_PW)) {
			String propertyValue = req.getParameter(property.key());
			if (propertyValue != null) {
				changed |= setProperty(property, propertyValue);
			}
		}

		for (StreamJMXProperty property : StreamJMXProperties.values(servletProperties, FILE_EDITOR)) {
			String tnt4jConfigContents = req.getParameter(property.key());
			if (tnt4jConfigContents != null) {
				OutputStream tnt4jProperties = null;
				String fileName = new File(System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY)).getName();
				try {
					tnt4jProperties = Files
							.newOutputStream(Paths.get(getClass().getClassLoader().getResource(fileName).getFile()));
					tnt4jProperties.write(tnt4jConfigContents.getBytes());
					changed = true;
				} catch (Exception e) {
					logger().log(OpLevel.ERROR, "!!!!   Failed writing to file: {0}   !!!!", fileName, e);
				} finally {
					Utils.close(tnt4jProperties);
				}
			}
		}

		if (changed) {
			samplerDestroy();
			samplerStart();
		}

		resp.sendRedirect(req.getContextPath());
	}

	@Override
	public void destroy() {
		logger().log(OpLevel.INFO,
				"######################     Stopping TNT4J-stream-JMX as Servlet   ######################");
		samplerDestroy();

		super.destroy();

		postDestroy();

		TextAreaLogAppender.getInstance().stop();
	}

	protected void postDestroy() {
	}

	private void printTableLine(PrintWriter out, StreamJMXProperty property, boolean editable, boolean password) {
		String propertyKey = property.key();
		out.println("<tr>");
		out.println("<td>" + propertyKey + " </td>");

		String editableStr = editable ? "" : "readonly disabled";
		String inputType = password ? "password" : "text";
		String text = StringEscapeUtils.escapeHtml4(getProperty(propertyKey, ""));
		if (password) {
			text = text.replaceAll(".", "*");
		}

		out.println("<td><input type=\"" + inputType + "\" name=\"" + propertyKey + "\" value=\"" + text + "\" "
				+ editableStr + " size=\"70\"></td>");
		out.println("<td> ");

		if (property.isInScope(SYSTEM)) {
			out.print(propertyPermitted(propertyKey));
		} else {
			out.print("&nbsp;");
		}

		out.println("  </td>");
		out.println("</tr>");
	}

	private static void printTableSplitLine(PrintWriter out) {
		out.println("<tr>");
		out.println("<td>&nbsp;</td>");
		out.println("<td>&nbsp;</td>");
		out.println("<td>&nbsp;</td>");
		out.println("</tr>");
	}

	private void outputTabs(PrintWriter out, String servletPath) throws IOException {
		StreamJMXProperty[] feProperties = StreamJMXProperties.values(servletProperties, FILE_EDITOR);

		out.println("<div class=\"tab\">");
		for (StreamJMXProperty property : feProperties) {
			out.println("<button class=\"tablinks\" onclick=\"openTab(event, '" + property.key() + "')\"> "
					+ property.defaultValue() + "</button>");
		}
		out.println("</div>");
		for (StreamJMXProperty property : feProperties) {
			out.println("<div id=\"" + property.key() + "\" class=\"tabcontent\">");
			out.println("<form action=\"" + servletPath + "\" method=\"post\">");
			out.println("<H3>" + property.defaultValue() + "</H3>");
			out.println("<textarea name=\"" + property.key() + "\" cols=\"140\" rows=\"55\">");

			String configString = getString(Utils.getResourceAsStream(StreamJMXServlet.class,
					new File(System.getProperty(TrackerConfigStore.TNT4J_PROPERTIES_KEY)).getName()));
			try {
				out.println(configString);
			} catch (Exception e) {
				out.println("No " + property.key() + " found.");
				logger().log(OpLevel.ERROR, "No property found using key: {0}", property.key(), e);
			}
			out.println("</textarea>");

			out.println("<br>");
			out.println("<input type=\"submit\" value=\"Submit " + property.key() + " file\"><br>");
			out.println("<br>");
			out.println("</form>");
			out.println("</div>");
		}
	}

	private void outputLogger(PrintWriter out) throws IOException {
		out.println("<H3>Stream-SJMX Output</H3>");
		out.println("<textarea id=\"" + TNT4J_LOGGER_OUTPUT + "\" name=\"" + TNT4J_LOGGER_OUTPUT
				+ "\" cols=\"140\" rows=\"55\">");
		try {
			out.println(TextAreaLogAppender.getInstance().getCaptured());
		} catch (Exception e) {
			out.println("!!! NO captured logger output available !!!");
			e.printStackTrace(out);
			logger().log(OpLevel.ERROR, "Could not fill logger output!..", e);
		}
		out.println("</textarea>");
		out.println("<br>");
		out.println("<br>");
	}

	private static void outputStyle(PrintWriter out, String cp) {
		out.println("<link rel=\"stylesheet\" href=\"" + cp + "/static/css/tntJmx.css\">");
	}

	private static void outputScript(PrintWriter out, String cp) {
		out.println("<script src=\"" + cp + "/static/js/tntJmx.js\"></script>");
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

	private void getPropertiesFromContext(ServletConfig servletConfig) {
		logger().log(OpLevel.DEBUG, ">>>>>> Servlet context initial parameters: start");

		inAppCfgProperties.putAll(SamplingAgent.DEFAULTS);

		for (Map.Entry<?, ?> prop : inAppCfgProperties.entrySet()) {
			logger().log(OpLevel.DEBUG, "==> API default property found: {0}={1}", prop.getKey(), prop.getValue());
		}

		@SuppressWarnings("unchecked")
		Enumeration<String> initParameterNames = servletConfig.getInitParameterNames();
		while (initParameterNames.hasMoreElements()) {
			String initParamName = initParameterNames.nextElement();
			logger().log(OpLevel.DEBUG, "==> Servlet init parameter found: {0}={1}", initParamName,
					servletConfig.getInitParameter(initParamName));
		}
		for (StreamJMXProperty prop : servletProperties) {
			String initParameter = servletConfig.getInitParameter(prop.key());
			if (initParameter == null) {
				initParameter = inAppCfgProperties.getProperty(prop.key());
			}

			if (initParameter != null) {
				setProperty(prop, initParameter);
			}
		}

		logger().log(OpLevel.DEBUG, "<<<<<< Servlet context initial parameters: end");
	}

	private void initStream() {
		logger().log(OpLevel.INFO,
				"######################     Starting TNT4J-stream-JMX as Servlet   ######################");
		try {
			logger().log(OpLevel.INFO,
					">>>>>>>>>>>>>>>>>>    TNT4J-stream-JMX environment check start   >>>>>>>>>>>>>>>>>>");
			envCheck();
			logger().log(OpLevel.INFO,
					"<<<<<<<<<<<<<<<<<<<    TNT4J-stream-JMX environment check end   <<<<<<<<<<<<<<<<<<<");

			samplerStart();
		} catch (Exception e) {
			logger().log(OpLevel.ERROR, "!!!!   Failed to start TNT4J-stream-JMX   !!!!", e);
		}
	}

	protected void envCheck() {
		logger().log(OpLevel.DEBUG, "==> J2EE: {0}", getClassLocation("javax.management.j2ee.statistics.Statistic"));
	}

	protected static URL getClassLocation(Class<?> clazz) {
		return clazz.getResource('/' + clazz.getName().replace('.', '/') + ".class");
	}

	protected static URL getClassLocation(String clazzName) {
		return StreamJMXServlet.class.getResource('/' + clazzName.replace('.', '/') + ".class");
	}

	private void samplerStart() {
		configure();
		sampler = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					logger().log(OpLevel.INFO,
							"---------------------     Connecting Sampler Agent     ---------------------");
					String vm = getProperty(StreamJMXProperties.VM);
					String ao = getProperty(StreamJMXProperties.AO);

					SamplingAgent.initListenerProperties();

					if (StringUtils.isEmpty(vm)) {
						logger().log(OpLevel.INFO, "==> Sampling from local process runner JVM: options={0}", ao);
						SamplingAgent.sampleLocalVM(ao, true);
					} else {
						logger().log(OpLevel.INFO, "==> Connecting to remote JVM: vm={0}, options={1}", vm, ao);
						SamplingAgent.newSamplingAgent().connect(vm, ao);
					}
				} catch (Exception e) {
					logger().log(OpLevel.ERROR, "!!!!   Failed to connect Sampler Agent   !!!!", e);
				}
			}
		}, "Stream-JMX_servlet_sampler_thread");
		sampler.start();
	}

	private void configure() {
		for (StreamJMXProperty property : servletProperties) {
			if (!property.isInScope(INTERIM)) {
				setProperty(property, getProperty(property));
			}
		}
		expandAO(getProperty(StreamJMXProperties.AO));
	}

	private static String compileAO(Properties props) {
		StringBuilder sb = new StringBuilder(64);

		sb.append(props.getProperty(StreamJMXProperties.AO_INCLUDE.key(), "*.*"));
		sb.append("!");
		sb.append(props.getProperty(StreamJMXProperties.AO_EXCLUDE.key(), ""));
		sb.append("!");
		sb.append(props.getProperty(StreamJMXProperties.AO_PERIOD.key(), "60000"));
		sb.append("!");
		sb.append(props.getProperty(StreamJMXProperties.AO_DELAY.key(), "0"));
		return sb.toString();
	}

	private void expandAO(String ao) {
		String[] args = ao.split("!");
		if (args.length > 0) {
			setProperty(StreamJMXProperties.AO_INCLUDE, args[0]);
		}
		if (args.length > 1) {
			setProperty(StreamJMXProperties.AO_EXCLUDE, args[1]);
		}
		if (args.length > 2) {
			setProperty(StreamJMXProperties.AO_PERIOD, args[2]);
		}
		if (args.length > 3) {
			setProperty(StreamJMXProperties.AO_DELAY, args[3]);
		}
	}

	protected String getProperty(String key, String defValue) {
		for (StreamJMXProperty property : servletProperties) {
			if (property.key().equals(key)) {
				if (property.isInScope(SYSTEM)) {
					try {
						return System.getProperty(property.key(), defValue);
					} catch (SecurityException e) {
						logger().log(OpLevel.ERROR, "!!!!   Failed to get property {0}: {1}  !!!!", key,
								Utils.getExceptionMessages(e));

						return defValue;
					}
				}
				if (property.isInScope(LOCAL)) {
					return inAppCfgProperties.getProperty(property.key(), defValue);
				}
			}
		}
		return null;
	}

	protected String getProperty(StreamJMXProperty property) {
		if (property.isInScope(SYSTEM)) {
			try {
				return System.getProperty(property.key(), property.defaultValue());
			} catch (SecurityException e) {
				logger().log(OpLevel.ERROR, "!!!!   Failed to get property {0}: {1}  !!!!", property.key(),
						Utils.getExceptionMessages(e));
				return property.defaultValue();
			}
		}
		if (property.isInScope(LOCAL)) {
			return inAppCfgProperties.getProperty(property.key(), property.defaultValue());
		}

		return null;
	}

	private void samplerDestroy() {
		logger().log(OpLevel.INFO,
				"------------------------     Destroying Sampler Agent     ------------------------");
		SamplingAgent.destroy();
		try {
			sampler.join(TimeUnit.SECONDS.toMillis(2));
		} catch (InterruptedException exc) {
		}
	}

	private boolean setProperty(StreamJMXProperty property, String value) {
		logger().log(OpLevel.DEBUG, "==> Setting property: {0}={1}", property.key(), value);

		Object last = null;
		if (property.isInScope(LOCAL)) {
			last = inAppCfgProperties.setProperty(property.key(), value);
		}
		if (property.isInScope(SYSTEM)) {
			last = System.setProperty(property.key(), value);
		}
		if (property.isInScope(INTERIM)) {
			last = setProperty(StreamJMXProperties.AO, compileAO(inAppCfgProperties));
		}
		if (last != null) {
			return true;
		}
		return false;
	}
}
