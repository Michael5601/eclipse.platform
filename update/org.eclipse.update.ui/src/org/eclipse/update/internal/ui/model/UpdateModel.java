/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.internal.ui.model;

import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.update.core.*;
import org.eclipse.update.internal.ui.UpdateUI;

public class UpdateModel implements IAdaptable {
	private Vector changes = new Vector();
	private Vector bookmarks = new Vector();
	private Vector listeners = new Vector();
	private static final String BOOKMARK_FILE = "bookmarks.xml";
	
	public UpdateModel() {
		reset();
	}
	
	public void reset() {
		// load bookmarks
		bookmarks.clear();
		BookmarkUtil.parse(getBookmarksFileName(), bookmarks);
	}
	
	private String getBookmarksFileName() {
		IPath path = UpdateUI.getDefault().getStateLocation();
		path = path.append(BOOKMARK_FILE);
		return path.toOSString();
	}
	
	public void shutdown() {
		saveBookmarks();
	}
	
	public void saveBookmarks() {
		BookmarkUtil.store(getBookmarksFileName(), bookmarks);
	}

	public PendingChange [] getPendingChanges() {
		return (PendingChange[])
			changes.toArray(new PendingChange[changes.size()]);
	}
	
	public PendingChange [] getPendingChanges(int type) {
		Vector v = new Vector();
		for (int i=0; i<changes.size(); i++) {
			PendingChange job = (PendingChange)changes.elementAt(i);
			if (job.getJobType() == type)
			   v.add(job);
		}
		return (PendingChange[])
			v.toArray(new PendingChange[v.size()]);
	}
	
	public PendingChange findPendingChange(IFeature feature) {
		for (int i=0; i<changes.size(); i++) {
			PendingChange job = (PendingChange)changes.elementAt(i);
			if (job.getFeature().equals(feature))
			   return job;
		}
		return null;
	}

	public PendingChange findRelatedPendingChange(IFeature feature) {
		for (int i=0; i<changes.size(); i++) {
			PendingChange job = (PendingChange)changes.elementAt(i);
			
			String jobId = job.getFeature().getVersionedIdentifier().getIdentifier();
			String id = feature.getVersionedIdentifier().getIdentifier();
			if (id.equals(jobId)) return job;
		}
		return null;
	}
			
	
	public boolean isPending(IFeature feature) {
		return findPendingChange(feature)!=null;
	}
	
	public void addPendingChange(PendingChange change) {
		changes.add(change);
		change.setModel(this);
		fireObjectsAdded(this, new Object[] {change});
	}
	
	public void removePendingChange(PendingChange change) {
		changes.remove(change);
		change.setModel(null);
		fireObjectsRemoved(this, new Object[] {change});
	}
	
	public void removePendingChange(IFeature scheduledFeature) {
		PendingChange change = findPendingChange(scheduledFeature);
		if (change!=null)
		   removePendingChange(change);
	}	
	
	public void addBookmark(NamedModelObject bookmark) {
		bookmarks.add(bookmark);
		bookmark.setModel(this);
		fireObjectsAdded(null, new Object []{bookmark});
	}
	
	public void removeBookmark(NamedModelObject bookmark) {
		bookmarks.remove(bookmark);
		bookmark.setModel(null);
		fireObjectsRemoved(null, new Object []{bookmark});
	}
	
	public NamedModelObject [] getBookmarks() {
		return (NamedModelObject[])bookmarks.toArray(new NamedModelObject[bookmarks.size()]);
	}
	
	public SiteBookmark [] getBookmarkLeafs() {
		return BookmarkUtil.getBookmarks(bookmarks);
	}
	
	public BookmarkFolder getFolder(IPath path) {
		return BookmarkUtil.getFolder(bookmarks, path);
	}
	
	public void addUpdateModelChangedListener(IUpdateModelChangedListener listener) {
		if (!listeners.contains(listener)) 
		   listeners.add(listener);
	}


	public void removeUpdateModelChangedListener(IUpdateModelChangedListener listener) {
		if (listeners.contains(listener))
			listeners.remove(listener);
	}
	
	public void fireObjectsAdded(Object parent, Object [] children) {
		for (Iterator iter=listeners.iterator();
				iter.hasNext();) {
			IUpdateModelChangedListener listener = (IUpdateModelChangedListener)iter.next();
			listener.objectsAdded(parent, children);
		}
	}


	public void fireObjectsRemoved(Object parent, Object [] children) {
		for (Iterator iter=listeners.iterator();
				iter.hasNext();) {
			IUpdateModelChangedListener listener = (IUpdateModelChangedListener)iter.next();
			listener.objectsRemoved(parent, children);
		}
	}
	
	public void fireObjectChanged(Object object, String property) {
		for (Iterator iter=listeners.iterator();
			iter.hasNext();) {
			IUpdateModelChangedListener listener = (IUpdateModelChangedListener)iter.next();
			listener.objectChanged(object, property);
		}
	}
	public Object getAdapter(Class key) {
		return null;
	}
	
	public static boolean hasLicense(PendingChange job) {
		IFeature feature = job.getFeature();
		return hasLicense(feature);
	}
	
	public static boolean hasLicense(IFeature feature) {
		IURLEntry info = feature.getLicense();
		if (info == null)
			return false;
		String licenseTxt = info.getAnnotation();
		if (licenseTxt == null)
			return false;
		return licenseTxt.trim().length() > 0;
	}
	public static boolean hasOptionalFeatures(IFeatureReference fref) {
		try {
			return hasOptionalFeatures(fref.getFeature(null));
		} catch (CoreException e) {
			return false;
		}
	}
	public static boolean hasOptionalFeatures(IFeature feature) {
		try {
			IIncludedFeatureReference[] irefs = feature.getIncludedFeatureReferences();
			for (int i = 0; i < irefs.length; i++) {
				IIncludedFeatureReference iref = irefs[i];
				if (iref.isOptional())
					return true;
				// see if it has optional children
				IFeature child = iref.getFeature(null);
				if (hasOptionalFeatures(child))
					return true;
			}
		} catch (CoreException e) {
		}
		return false;
	}
}
