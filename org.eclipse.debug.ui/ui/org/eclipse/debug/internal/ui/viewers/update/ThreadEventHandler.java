/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.viewers.update;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.debug.internal.ui.viewers.AbstractModelProxy;
import org.eclipse.debug.internal.ui.viewers.IModelDelta;

/**
 * @since 3.2
 */
public class ThreadEventHandler extends DebugEventHandler {
	
	/**
	 * Queue of suspended threads to choose from when needing
	 * to select a thread when another is resumed. Threads
	 * are added in the order they suspend.
	 */
	private Set fThreadQueue = new LinkedHashSet();
	
	/** 
	 * Map of previous TOS per thread
	 */
	private Map fLastTopFrame = new HashMap();
	/**
	 * Constructs and event handler for a threads in the given viewer.
	 * 
	 * @param viewer
	 */
	public ThreadEventHandler(AbstractModelProxy proxy) {
		super(proxy);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.viewers.update.DebugEventHandler#dispose()
	 */
	public synchronized void dispose() {
		fLastTopFrame.clear();
		fThreadQueue.clear();
		super.dispose();
	}

	protected void handleSuspend(DebugEvent event) {
        IThread thread = (IThread) event.getSource();
		if (event.isEvaluation()) {
        	ModelDelta delta = buildRootDelta();
    		ModelDelta node = addPathToThread(delta, thread);
			try {
				IStackFrame frame = thread.getTopStackFrame();
                if (frame != null) { 
                    node.addNode(frame, IModelDelta.CHANGED | IModelDelta.STATE);
                    fireDelta(delta);
                }
			} catch (DebugException e) {
			}
        } else {
        	queueSuspendedThread(event);
        	fireDeltaUpdatingTopFrame(thread, IModelDelta.NOCHANGE);
        }
	}
	
	private boolean isEqual(Object o1, Object o2) {
		if (o1 == o2) {
			return true;
		}
		if (o1 == null) {
			return false;
		}
		return o1.equals(o2);
	}

	protected void handleResume(DebugEvent event) {
		IThread thread = removeSuspendedThread(event);
		fireDeltaAndClearTopFrame(thread, IModelDelta.CHANGED | IModelDelta.STATE | IModelDelta.CONTENT);
		thread = getNextSuspendedThread();
		if (thread != null) {
			fireDeltaUpdatingTopFrame(thread, IModelDelta.NOCHANGE);
		}
	}

	protected void handleCreate(DebugEvent event) {
		fireDeltaAndClearTopFrame((IThread) event.getSource(), IModelDelta.ADDED | IModelDelta.STATE);
	}

	protected void handleTerminate(DebugEvent event) {
		fireDeltaAndClearTopFrame((IThread) event.getSource(), IModelDelta.REMOVED);
	}

	protected void handleChange(DebugEvent event) {
		fireDeltaUpdatingTopFrame((IThread) event.getSource(), IModelDelta.CHANGED | IModelDelta.STATE);
	}

	protected void handleLateSuspend(DebugEvent suspend, DebugEvent resume) {
		IThread thread = queueSuspendedThread(suspend);
		fireDeltaUpdatingTopFrame(thread, IModelDelta.CHANGED | IModelDelta.CONTENT | IModelDelta.EXPAND);
	}

	protected void handleSuspendTimeout(DebugEvent event) {
		IThread thread = removeSuspendedThread(event);
		fireDeltaAndClearTopFrame(thread, IModelDelta.CHANGED | IModelDelta.CONTENT);
	}
	
	private ModelDelta buildRootDelta() {
		return new ModelDelta(DebugPlugin.getDefault().getLaunchManager(), IModelDelta.NOCHANGE);
	}
	
	private ModelDelta addPathToThread(ModelDelta delta, IThread thread) {
		delta = delta.addNode(thread.getLaunch(), IModelDelta.NOCHANGE);
		return delta.addNode(thread.getDebugTarget(), IModelDelta.EXPAND);
	}

	private void fireDeltaAndClearTopFrame(IThread thread, int flags) {
		ModelDelta delta = buildRootDelta();
		ModelDelta node = addPathToThread(delta, thread);
		node.addNode(thread, flags);
		synchronized (this) {
			fLastTopFrame.remove(thread);
		}
		fireDelta(delta);
	}
	
	private void fireDeltaUpdatingTopFrame(IThread thread, int flags) {
		ModelDelta delta = buildRootDelta();
		ModelDelta node = addPathToThread(delta, thread);
    	IStackFrame prev = null;
    	synchronized (this) {
    		 prev = (IStackFrame) fLastTopFrame.get(thread);
		}
    	IStackFrame frame = null;
		try {
			 frame = thread.getTopStackFrame();
		} catch (DebugException e) {
		}
    	if (isEqual(frame, prev)) {
    		node = node.addNode(thread, flags | IModelDelta.CHANGED | IModelDelta.EXPAND);
    	} else {
			node = node.addNode(thread, flags | IModelDelta.CHANGED | IModelDelta.CONTENT | IModelDelta.EXPAND);
    	}
    	if (frame != null) {
            node.addNode(frame, IModelDelta.CHANGED | IModelDelta.STATE | IModelDelta.SELECT);
        }
    	synchronized (this) {
    		if (!isDisposed()) {
    			fLastTopFrame.put(thread, frame);
    		}
		}
    	fireDelta(delta);
	}	
	
	protected boolean handlesEvent(DebugEvent event) {
		return event.getSource() instanceof IThread;
	}
	
	protected synchronized IThread queueSuspendedThread(DebugEvent event) {
		IThread thread = (IThread) event.getSource();
		if (!isDisposed()) {
			fThreadQueue.add(thread);
		}
		return thread;
	}
	
	protected synchronized IThread removeSuspendedThread(DebugEvent event) {
		IThread thread = (IThread)event.getSource();
		fThreadQueue.remove(thread);
		return thread;
	}
	
	protected synchronized IThread getNextSuspendedThread() {
		if (!fThreadQueue.isEmpty()) {
			return (IThread) fThreadQueue.iterator().next();
		}
		return null;
	}

}
