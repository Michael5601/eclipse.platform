/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.core.mapping;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.mapping.*;
import org.eclipse.core.runtime.*;
import org.eclipse.team.core.diff.IDiffNode;
import org.eclipse.team.core.mapping.provider.MergeStatus;
import org.eclipse.team.internal.core.Policy;


/**
 * Abstract implementation of {@link IResourceMappingMerger}.
 * 
 * <p>
 * Clients may subclass this class.
 * 
 * <p>
 * <strong>EXPERIMENTAL</strong>. This class or interface has been added as
 * part of a work in progress. There is a guarantee neither that this API will
 * work nor that it will remain the same. Please do not use this API without
 * consulting with the Platform/Team team.
 * </p>
 * 
 * @see IResourceMappingMerger
 * 
 * @since 3.2
 */
public abstract class ResourceMappingMerger implements IResourceMappingMerger {

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.mapping.IResourceMappingMerger#validateMerge(org.eclipse.team.core.mapping.IMergeContext, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus validateMerge(IMergeContext mergeContext, IProgressMonitor monitor) {
		return Status.OK_STATUS;
	}
	
	/**
	 * Return the model provider associated with this merger.
	 * @return Return the model provider associated with this merger.
	 */
	protected abstract ModelProvider getModelProvider();
	
	/**
	 * A default implementation of merge that attempts to merge all the mappings
	 * in the context.
	 * @param mergeContext the context
	 * @param monitor a progress monitor
	 * @return a status indicating whether the merge was successful
	 * @throws CoreException if an error occurred 
	 * @see org.eclipse.team.core.mapping.IResourceMappingMerger#merge(org.eclipse.team.core.mapping.IMergeContext, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus merge(IMergeContext mergeContext, IProgressMonitor monitor) throws CoreException {
		try {
			IDiffNode[] deltas = getSetToMerge(mergeContext);
			monitor.beginTask(null, 100);
			IStatus status = mergeContext.merge(deltas, false /* don't force */, Policy.subMonitorFor(monitor, 75));
			return covertFilesToMappings(status, mergeContext);
		} finally {
			monitor.done();
		}
	}

	private IDiffNode[] getSetToMerge(IMergeContext mergeContext) {
		ResourceMapping[] mappings = mergeContext.getScope().getMappings(getModelProvider().getDescriptor().getId());
		Set result = new HashSet();
		for (int i = 0; i < mappings.length; i++) {
			ResourceMapping mapping = mappings[i];
			ResourceTraversal[] traversals = mergeContext.getScope().getTraversals(mapping);
			IDiffNode[] deltas = mergeContext.getDiffTree().getDiffs(traversals);
			for (int j = 0; j < deltas.length; j++) {
				IDiffNode delta = deltas[j];
				result.add(delta);
			}
		}
		return (IDiffNode[]) result.toArray(new IDiffNode[result.size()]);
	}

	private IStatus covertFilesToMappings(IStatus status, IMergeContext mergeContext) {
		if (status.getCode() == IMergeStatus.CONFLICTS) {
			// In general, we can't say which mapping failed so return them all
			return new MergeStatus(status.getPlugin(), status.getMessage(), mergeContext.getScope().getMappings(getModelProvider().getDescriptor().getId()));
		}
		return status;
	}
}
