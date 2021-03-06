/*******************************************************************************
 * Copyright (c) 2004, 2016 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Keith Seitz <keiths@redhat.com> - initial API and implementation
 *    Kent Sebastian <ksebasti@redhat.com> -
 *******************************************************************************/
package org.eclipse.linuxtools.internal.oprofile.core.linux;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.linuxtools.internal.oprofile.core.Oprofile;
import org.eclipse.linuxtools.internal.oprofile.core.Oprofile.OprofileProject;
import org.eclipse.linuxtools.internal.oprofile.core.OprofileCorePlugin;
import org.eclipse.linuxtools.internal.oprofile.core.OprofileProperties;
import org.eclipse.linuxtools.internal.oprofile.core.opxml.AbstractDataAdapter;
import org.eclipse.linuxtools.internal.oprofile.core.opxml.OprofileSAXHandler;
import org.eclipse.linuxtools.internal.oprofile.core.opxml.checkevent.CheckEventAdapter;
import org.eclipse.linuxtools.internal.oprofile.core.opxml.info.InfoAdapter;
import org.eclipse.linuxtools.internal.oprofile.core.opxml.modeldata.ModelDataAdapter;
import org.eclipse.linuxtools.internal.oprofile.core.opxml.sessions.SessionManager;
import org.eclipse.linuxtools.profiling.launch.IRemoteFileProxy;
import org.eclipse.linuxtools.profiling.launch.RemoteProxyManager;
import org.eclipse.linuxtools.tools.launch.core.factory.RuntimeProcessFactory;
import org.eclipse.osgi.util.NLS;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * This class will run opxml.
 *
 * opxml is a small program which acts as a textual interface between Oprofile
 * and BFD and the oprofile plugins.
 */
public class OpxmlRunner {

	/**
	 * Runs opxml with the given arguments.
	 * 
	 * @param args
	 *            the arguments to pass to opxml
	 * @param callData
	 *            any callData to pass to the processor
	 * @return boolean indicating the success/failure of opxml
	 */
	public boolean run(String[] args, Object callData) {
		XMLReader reader = null;
		OprofileSAXHandler handler = OprofileSAXHandler.getInstance(callData);

		// Create XMLReader
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			reader = factory.newSAXParser().getXMLReader();
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

		// Set content/error handlers
		reader.setContentHandler(handler);
		reader.setErrorHandler(handler);

		// Check for timer support
		InfoAdapter.checkTimerSupport();

		// Run opxml
		try {
			File file = constructFile(args);

			// handle the opxml_session file
			if (args[0].equals(SessionManager.SESSIONS)) {
				SessionManager sessManNew = new SessionManager(SessionManager.SESSION_LOCATION);
				populateWithCurrentSession(sessManNew);
				sessManNew.write();
				FileReader fr = new FileReader(file);
				reader.parse(new InputSource(fr));
				// file has not been saved
			} else if (!file.exists()) {
				AbstractDataAdapter aea;
				if (args[0].equals(CheckEventAdapter.CHECK_EVENTS)) {
					aea = new CheckEventAdapter(args[1], args[2], args[3]);
					aea.process();
					BufferedReader bi = new BufferedReader(new InputStreamReader(aea.getInputStream()));
					reader.parse(new InputSource(bi));
				} else if (args[0].equals(InfoAdapter.INFO)) {
					aea = new InfoAdapter();
					aea.process();
					BufferedReader bi = new BufferedReader(new InputStreamReader(aea.getInputStream()));
					reader.parse(new InputSource(bi));
				} else if (args[0].equals(ModelDataAdapter.MODEL_DATA)) {
					// this should only happen initially when the current
					// session
					// has not been generated
					if (!handleModelData(args)) {
						return false;
					}
					FileReader fr = new FileReader(file);
					reader.parse(new InputSource(fr));
				} else {
					throw new RuntimeException("Unrecognized argument encountered"); //$NON-NLS-1$
				}
			} else {
				// always regenerate the 'current' session file
				if (args.length == 3 && args[0].equals(SessionManager.MODEL_DATA)
						&& args[2].equals(SessionManager.CURRENT)) {
					if (!handleModelData(args)) {
						return false;
					}
				}
				FileReader fr = new FileReader(file);
				reader.parse(new InputSource(fr));
			}

			return true;
		} catch (SAXException e) {
			e.printStackTrace();
			OprofileCorePlugin.showErrorDialog("opxmlSAXParseException", null); //$NON-NLS-1$
		} catch (IOException e) {
			e.printStackTrace();
			OprofileCorePlugin.showErrorDialog("opxmlParse", null); //$NON-NLS-1$
		}
		return false;
	}

	private File saveOpxmlToFile(BufferedReader bi, String[] args) {
		String fileName = ""; //$NON-NLS-1$
		for (String arg : args) {
			fileName += arg;
		}
		File file = new File(SessionManager.OPXML_PREFIX + fileName);
		String line;
		try {
			file.createNewFile();
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
				while ((line = bi.readLine()) != null) {
					bw.write(line + "\n"); //$NON-NLS-1$
				}
				bi.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return file;
	}

	private File constructFile(String[] args) {
		String fileName = ""; //$NON-NLS-1$
		for (int i = 0; i < args.length; i++) {
			fileName += args[i];
		}
		return new File(SessionManager.OPXML_PREFIX + fileName);
	}

	private boolean handleModelData(String[] args) {
		ArrayList<String> cmd = new ArrayList<>();
		cmd.add("-Xdg"); //$NON-NLS-1$
		if (!InfoAdapter.hasTimerSupport()) {
			cmd.add("event:" + args[1]); //$NON-NLS-1$
		}
		String[] a = {};
		InputStream is = runOpReport(cmd.toArray(a));

		if (is == null) {
			return false;
		}
		ModelDataAdapter mda = new ModelDataAdapter(is);
		if (!mda.isParseable()) {
			return false;
		}
		mda.process();
		BufferedReader bi = new BufferedReader(new InputStreamReader(mda.getInputStream()));
		saveOpxmlToFile(bi, args);
		return true;
	}

	/**
	 * Add the current session to the session manager for each event that it was
	 * profiled under.
	 * 
	 * @param session
	 *            the session manager to populate
	 */
	private void populateWithCurrentSession(SessionManager session) {
		session.removeAllCurrentSessions();
		String[] eventName = getEventNames();
		if (eventName != null) {
			for (int i = 0; i < eventName.length; i++) {
				session.addSession(SessionManager.CURRENT, eventName[i]);
			}
		}
	}

	private String[] getEventNames() {
		String[] ret = null;
		try {
			String cmd[] = { "-X", "-d" }; //$NON-NLS-1$ //$NON-NLS-2$
			InputStream is = runOpReport(cmd);

			if (is != null) {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder;
				builder = factory.newDocumentBuilder();
				Document doc = builder.parse(is);
				Element root = (Element) doc.getElementsByTagName(ModelDataAdapter.PROFILE).item(0);

				String eventOrTimerSetup;
				String eventOrTimerName;

				// Determine if we are in timer-mode or not as the XML will vary
				if (!InfoAdapter.hasTimerSupport()) {
					eventOrTimerSetup = ModelDataAdapter.EVENT_SETUP;
					eventOrTimerName = ModelDataAdapter.EVENT_NAME;
				} else {
					eventOrTimerSetup = ModelDataAdapter.TIMER_SETUP;
					eventOrTimerName = ModelDataAdapter.RTC_INTERRUPTS;
				}

				Element setupTag = (Element) root.getElementsByTagName(ModelDataAdapter.SETUP).item(0);
				NodeList eventSetupList = setupTag.getElementsByTagName(eventOrTimerSetup);

				// get the event names for the current session
				ret = new String[eventSetupList.getLength()];
				for (int i = 0; i < eventSetupList.getLength(); i++) {
					Element elm = (Element) eventSetupList.item(i);
					ret[i] = elm.getAttribute(eventOrTimerName);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			OprofileCorePlugin.showErrorDialog("opxmlParse", null); //$NON-NLS-1$
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
			OprofileCorePlugin.showErrorDialog("opxmlSAXParseException", null); //$NON-NLS-1$
		}
		return ret;
	}

	/**
	 * Run opreport with specified arguments <code>args</code> and return
	 * InputStream to output of report for parsing.
	 *
	 * @param args
	 *            arguments to run with opreport
	 * @return InputStream to output of report
	 */
	private InputStream runOpReport(String[] args) {

		ArrayList<String> cmd = new ArrayList<>();
		cmd.add("opreport"); //$NON-NLS-1$
		if (OprofileProject.getProfilingBinary().equals(OprofileProject.OPERF_BINARY)) {
			/*
			 * The session-dir parameter is relative to project's working dir,
			 * which might be local or remote. So it should use the proxy
			 * manager to determine working dir.
			 */
			String workingDir = ""; //$NON-NLS-1$
			RemoteProxyManager proxy = RemoteProxyManager.getInstance();
			try {
				IRemoteFileProxy rfile = proxy.getFileProxy(Oprofile.OprofileProject.getProject());
				workingDir = rfile.getWorkingDir().getPath();
			} catch (CoreException e) {
				e.printStackTrace();
				return null;
			}
			cmd.add(1, "--session-dir=" + workingDir + IPath.SEPARATOR + "oprofile_data"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		Collections.addAll(cmd, args);
		Process p = null;
		try {
			p = RuntimeProcessFactory.getFactory().exec(cmd.toArray(new String[0]),
					Oprofile.OprofileProject.getProject());

			StringBuilder output = new StringBuilder();
			StringBuilder errorOutput = new StringBuilder();
			String s = null;
			try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
					BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
				// Read output of opreport. We need to do this, since this might
				// cause the plug-in to hang. See Eclipse bug 341621 for more
				// info.
				// FIXME: Both of those while loops should really be done in two
				// separate
				// threads, so that we avoid this very problem when the error
				// input
				// stream buffer fills up.
				while ((s = stdInput.readLine()) != null) {
					output.append(s + System.getProperty("line.separator")); //$NON-NLS-1$
				}
				while ((s = stdError.readLine()) != null) {
					errorOutput.append(s + System.getProperty("line.separator")); //$NON-NLS-1$
				}
				if (!errorOutput.toString().trim().equals("")) { //$NON-NLS-1$
					OprofileCorePlugin.log(IStatus.ERROR, NLS.bind(OprofileProperties.getString("process.log.stderr"), //$NON-NLS-1$
							"opreport", errorOutput.toString().trim())); //$NON-NLS-1$
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (p.waitFor() == 0) {
				// convert the string to inputstream to pass to builder.parse
				return new ByteArrayInputStream(output.toString().getBytes(StandardCharsets.UTF_8));
			}
		} catch (IOException e1) {
			e1.printStackTrace();
			OprofileCorePlugin.showErrorDialog("opxmlParse", null); //$NON-NLS-1$
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return null;
	}
}
