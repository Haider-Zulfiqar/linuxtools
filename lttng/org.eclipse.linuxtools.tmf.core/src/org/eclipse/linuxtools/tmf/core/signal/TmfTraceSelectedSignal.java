/*******************************************************************************
 * Copyright (c) 2010 Ericsson
 * 
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *   Patrick Tasse - Initial API and implementation
 *******************************************************************************/

package org.eclipse.linuxtools.tmf.core.signal;

import org.eclipse.linuxtools.tmf.core.trace.ITmfTrace;

/**
 * A trace was selected
 * 
 * @version 1.0
 * @author Patrick Tasse
 */
public class TmfTraceSelectedSignal extends TmfSignal {

    private final ITmfTrace<?> fTrace;
    
    public TmfTraceSelectedSignal(Object source, ITmfTrace<?> trace) {
        super(source);
        fTrace = trace;
    }

    public ITmfTrace<?> getTrace() {
        return fTrace;
    }

    @Override
    @SuppressWarnings("nls")
    public String toString() {
        return "[TmfTraceSelectedSignal (" + fTrace.getName() + ")]";
    }
}
