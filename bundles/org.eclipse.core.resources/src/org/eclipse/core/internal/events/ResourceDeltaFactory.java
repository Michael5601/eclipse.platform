/**********************************************************************
 * Copyright (c) 2000,2002 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v0.5
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.internal.events;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.internal.dtree.*;
import org.eclipse.core.internal.events.ResourceComparator;
import org.eclipse.core.internal.events.ResourceDelta;
import org.eclipse.core.internal.events.ResourceDeltaInfo;
import org.eclipse.core.internal.resources.ICoreConstants;
import org.eclipse.core.internal.resources.Project;
import org.eclipse.core.internal.resources.ResourceInfo;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.internal.watson.ElementTree;
import java.util.*;
/**
 * This class is used for calculating and building resource delta trees for notification 
 * and build purposes.
 */
public class ResourceDeltaFactory {
	/**
	 * Singleton indicating no delta children
	 */
	protected static final IResourceDelta[] NO_CHILDREN = new IResourceDelta[0];
/**
 * Returns the resource delta representing the changes made between the given old and new trees,
 * starting from the given root element.
 */
public static ResourceDelta computeDelta(Workspace workspace, ElementTree oldTree, ElementTree newTree, IPath root, boolean notification) {
	//compute the underlying delta tree.
	ResourceComparator comparator = ResourceComparator.getComparator(notification);
	newTree.immutable();
	DeltaDataTree delta = null;
	if (Path.ROOT.equals(root))
		delta = newTree.getDataTree().compareWith(oldTree.getDataTree(), comparator);
	else
		delta = newTree.getDataTree().compareWith(oldTree.getDataTree(), comparator, root);

	delta = delta.asReverseComparisonTree(comparator);
	IPath pathInTree = root.isRoot() ? Path.ROOT : root;
	IPath pathInDelta = Path.ROOT;

	// get the marker deltas for the delta info object....if needed
	Map allMarkerDeltas = null;
	if (notification)
		allMarkerDeltas = workspace.getMarkerManager().getMarkerDeltas();
		
	//recursively walk the delta and create a tree of ResourceDelta objects.
	ResourceDeltaInfo deltaInfo = new ResourceDeltaInfo(workspace, allMarkerDeltas, comparator);
	ResourceDelta result = createDelta(workspace, delta, deltaInfo, pathInTree, pathInDelta);
	
	//compute node ID map and fix up moves
	deltaInfo.setNodeIDMap(computeNodeIDMap(result, new NodeIDMap()));
	result.fixMovesAndMarkers(oldTree);

	// check all the projects and if they were added and opened then tweek the flags
	// so the delta reports both.
	int segmentCount = result.getFullPath().segmentCount();
	if (segmentCount <= 1) 
		checkForOpen(result, segmentCount);
	return result;
}
/**
 * Checks to see if added projects were also opens and tweaks the flags
 * accordingly. Should only be called for root and projects. Pass the segment count
 * in since we've already calculated it before.
 */
protected static void checkForOpen(ResourceDelta delta, int segmentCount) {
	if (delta.getKind() == IResourceDelta.ADDED)
		if (delta.newInfo.isSet(ICoreConstants.M_OPEN))
			delta.status |= IResourceDelta.OPEN;
	// return for PROJECT
	if (segmentCount == 1)
		return;
	// recurse for ROOT
	IResourceDelta[] children = delta.children;
	for (int i = 0; i < children.length; i++)
		checkForOpen((ResourceDelta) children[i], 1);
}
/**
 * Creates the map from node id to element id for the old and new states.
 * Used for recognizing moves.  Returns the map.
 */
protected static NodeIDMap computeNodeIDMap(ResourceDelta delta, NodeIDMap nodeIDMap) {
	IResourceDelta[] children = delta.children;
	for (int i = 0; i < children.length; i++) {
		ResourceDelta child = (ResourceDelta)children[i];
		IPath path = child.getFullPath();
		switch (child.getKind()) {
			case IResourceDelta.ADDED :
				nodeIDMap.putNewPath(child.newInfo.getNodeId(), path);
				break;
			case IResourceDelta.REMOVED :
				nodeIDMap.putOldPath(child.oldInfo.getNodeId(), path);
				break;
			case IResourceDelta.CHANGED :
				long oldID = child.oldInfo.getNodeId();
				long newID = child.newInfo.getNodeId();
				//don't add entries to the map if nothing has changed.
				if (oldID != newID) {
					nodeIDMap.putOldPath(oldID, path);
					nodeIDMap.putNewPath(newID, path);
				}
				break;
		}
		//recurse
		computeNodeIDMap(child, nodeIDMap);
	}
	return nodeIDMap;
}
/**
 * Recursively creates the tree of ResourceDelta objects rooted at
 * the given path.
 */
protected static ResourceDelta createDelta(Workspace workspace, DeltaDataTree delta, ResourceDeltaInfo deltaInfo, IPath pathInTree, IPath pathInDelta) {
	// create the delta and fill it with information
	ResourceDelta result = new ResourceDelta(pathInTree, deltaInfo);

	// fill the result with information
	NodeComparison compare = (NodeComparison) delta.getData(pathInDelta);
	int comparison = compare.getUserComparison();
	result.setStatus(comparison);
	if (comparison == IResourceDelta.NO_CHANGE || Path.ROOT.equals(pathInTree)) {
		ResourceInfo info = workspace.getResourceInfo(pathInTree, true, false);
		result.setOldInfo(info);
		result.setNewInfo(info);
	} else {
		result.setOldInfo((ResourceInfo) compare.getOldData());
		result.setNewInfo((ResourceInfo) compare.getNewData());
	}
	// recurse over the children
	IPath[] childKeys = delta.getChildren(pathInDelta);
	int numChildren = childKeys.length;
	if (numChildren == 0) {
		result.setChildren(NO_CHILDREN);
	} else {
		IResourceDelta[] children = new IResourceDelta[numChildren];
		for (int i = 0; i < numChildren; i++) {
			//reuse the delta path if tree-relative and delta-relative are the same
			IPath newTreePath = pathInTree == pathInDelta ? childKeys[i] : pathInTree.append(childKeys[i].lastSegment());
			children[i] = createDelta(workspace, delta, deltaInfo, newTreePath, childKeys[i]);
		}
		result.setChildren(children);
	}

	// if this delta has children but no other changes, mark it as changed
	int status = result.status;
	if ((status & IResourceDelta.ALL_WITH_PHANTOMS) == 0 && numChildren != 0)
		result.setStatus(status |= IResourceDelta.CHANGED);

	// return the delta
	return result;
}
/**
 * Returns an empty build delta describing the fact that no
 * changes occurred in the given project.  The returned delta
 * is not appropriate for use as a notification delta because
 * it is rooted at a project, and does not contain marker deltas.
 */
public static IResourceDelta newEmptyDelta(IProject project) {
	ResourceDelta result = new ResourceDelta(
		project.getFullPath(), 
		new ResourceDeltaInfo(
			((Workspace)project.getWorkspace()), 
			null, 
			ResourceComparator.getComparator(false)));
	result.setStatus(0);
	result.setChildren(NO_CHILDREN);
	ResourceInfo info = ((Project)project).getResourceInfo(true, false);
	result.setOldInfo(info);
	result.setNewInfo(info);
	return result;
}
}