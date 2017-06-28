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
package com.jkoolcloud.tnt4j.stream.jmx.utils;

import java.io.PrintStream;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import com.ibm.websphere.security.WSSecurityException;
import com.ibm.websphere.security.auth.WSSubject;
import com.ibm.websphere.security.auth.callback.WSCallbackHandlerImpl;
import com.ibm.websphere.security.cred.WSCredential;

/**
 * Helper class used to simplify use of WAS security entities.
 * <p>
 * It allows to use RunAs subject to perform {@link PrivilegedAction}s. Also it provides {@link #login(String, String)}
 * method to use particular user subject when performing {@link PrivilegedAction}s.
 *
 * @version $Revision: 1 $
 */
public class WASSecurityHelper {
	private static final String REALM_NAME = "default";

	private static LoginContext loginContext;
	private static Subject subject = null;

	/**
	 * Logins WAS user by provided username/password.
	 * 
	 * @param userName
	 *            user name
	 * @param password
	 *            user password
	 * @throws LoginException
	 *             if the authentication fails
	 *
	 * @see #logout()
	 */
	public static synchronized void login(String userName, String password) throws LoginException {
		if (loginContext == null) {
			loginContext = new LoginContext("WSLogin", new WSCallbackHandlerImpl(userName, REALM_NAME, password));
			loginContext.login();
		}

		subject = loginContext.getSubject();
	}

	/**
	 * Logout user.
	 * 
	 * @throws LoginException
	 *             if logout fails
	 *
	 * @see #login(String, String)
	 */
	public static synchronized void logout() throws LoginException {
		if (loginContext != null) {
			loginContext.logout();
			loginContext = null;
		}
	}

	/**
	 * Run some privileged code with logged in user subject.
	 * 
	 * @param action
	 *            privileged action to perform
	 * @return result the value returned by the {@link PrivilegedAction#run()} method
	 * @throws LoginException
	 *             if user is not logged in
	 *
	 * @see WSSubject#doAs(Subject, PrivilegedAction)
	 * @see #doPrivilegedAction(PrivilegedExceptionAction)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T doPrivilegedAction(PrivilegedAction<T> action) throws LoginException {
		checkSubject();

		return (T) WSSubject.doAs(subject, action);
	}

	/**
	 * Run some privileged code with logged in user subject.
	 *
	 * @param action
	 *            privileged exception action to perform
	 * @return result the value returned by the {@link PrivilegedExceptionAction#run()} method
	 * @throws LoginException
	 *             if user is not logged in
	 * @throws PrivilegedActionException
	 *             if the specified action's {@link PrivilegedExceptionAction#run()} method threw a <i>checked</i>
	 *             exception
	 *
	 * @see WSSubject#doAs(Subject, PrivilegedExceptionAction)
	 * @see #doPrivilegedAction(PrivilegedAction)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T doPrivilegedAction(PrivilegedExceptionAction<T> action)
			throws LoginException, PrivilegedActionException {
		checkSubject();

		return (T) WSSubject.doAs(subject, action);
	}

	private static void checkSubject() throws LoginException {
		if (System.getSecurityManager() != null && subject == null) {
			throw new LoginException("No user logged in!");
		}
	}

	/**
	 * Prints RunAs subject credentials to provided print stream.
	 *
	 * @param out
	 *            print stream to use for output
	 */
	public static void printCurrentCredentials(PrintStream out) {
		try {
			Subject runAsSubject = WSSubject.getRunAsSubject();
			if (runAsSubject != null) {
				final Iterator<Principal> iterator = runAsSubject.getPrincipals().iterator();
				out.print("Running as: ");
				while (iterator.hasNext()) {
					out.print(iterator.next().getName() + ", ");
				}
			} else {
				out.println("!!!!   Security failure - no subject!..   !!!!");
				return;
			}

			final Set<WSCredential> credentials = runAsSubject.getPublicCredentials(WSCredential.class);

			for (WSCredential cred : credentials) {
				out.println("getSecurityName: " + cred.getSecurityName());
				out.println("getUniqueSecurityName: " + cred.getUniqueSecurityName());
				out.println("getRealmName: " + cred.getRealmName());
				out.println("getRealmSecurityName: " + cred.getRealmSecurityName());
				out.println("getRealmUniqueSecurityName: " + cred.getRealmUniqueSecurityName());
				// always return null
				out.println("getRoles: " + cred.getRoles());
				ArrayList<?> groupIds = cred.getGroupIds();
				out.println("getGroupIds: " + groupIds);
				out.println("=-=-=-=-=-=-=-=-=-=-=-=-=-=");
			}
		} catch (Exception e) {
			out.println("!!!!   Security access failure!..   !!!!");
			e.printStackTrace(out);
		}
	}

	/**
	 * Login current RunAs user, and keep it.
	 * 
	 * @throws WSSecurityException
	 *             if WS security occurs while getting RunAs subject
	 */
	public static void loginCurrent() throws WSSecurityException {
		subject = WSSubject.getRunAsSubject();
	}
}
