/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.core;


/**
 * An event filter allows clients to intercept debug events.
 * Event filters are registered with the debug plug-in.
 * <p>
 * Clients may implement this interface.
 * </p>
 * @see DebugPlugin
 * @since 2.0
 */
public interface IDebugEventFilter {

	/**
	 * Filters the given set of debug events, and returns the set of debug
	 * events that should be fired to registered listeners - <code>null</code>
	 * or an empty collection if no debug events should be fired.
	 * <p>
	 * When multiple event filters are registered, events are passed through
	 * all filters. That is, the events returned from the first filter are
	 * passed through the second filter, and so on.
	 * </p>
	 * 
	 * @return  the set of debug events to fire
	 */
	public DebugEvent[] filterDebugEvents(DebugEvent[] events);
}
