/*******************************************************************************
 * Copyright (c) 2015, 2016 Red Hat.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat - Initial Contribution
 *******************************************************************************/
package org.eclipse.linuxtools.docker.core;

import java.io.OutputStream;

public interface ILogger {

	/**
	 * Set the output stream for the logger to use.
	 * 
	 * @param stream
	 *            - output stream
	 */
	void setOutputStream(OutputStream stream);

}
