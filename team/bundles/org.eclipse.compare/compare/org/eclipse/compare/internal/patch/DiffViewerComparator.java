/*******************************************************************************
 * Copyright (c) 2010, 2024 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.compare.internal.patch;

import java.util.Comparator;
import java.util.regex.Pattern;

import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.DocumentRangeNode;
import org.eclipse.jface.util.Policy;
import org.eclipse.jface.viewers.ViewerComparator;

public class DiffViewerComparator extends ViewerComparator {

	public boolean isSorterProperty(Object element, Object property) {
		return false;
	}

	@Override
	public int category(Object node) {
		if (node instanceof DiffNode) {
			Object o= ((DiffNode) node).getId();
			if (o instanceof DocumentRangeNode) {
				return ((DocumentRangeNode) o).getTypeCode();
			}
		}
		return 0;
	}

	@Override
	protected Comparator<? super String> getComparator() {
		return (arg0, arg1) -> {
			String label0 = arg0 == null ? "" : arg0.toString(); //$NON-NLS-1$
			String label1 = arg1 == null ? "" : arg1.toString(); //$NON-NLS-1$

			// see org.eclipse.compare.internal.patch.Hunk.getDescription()
			String pattern = "\\d+,\\d+ -> \\d+,\\d+.*"; //$NON-NLS-1$

			if (Pattern.matches(pattern, label0)
					&& Pattern.matches(pattern, label1)) {
				int oldStart0 = Integer.parseInt(label0.split(",")[0]); //$NON-NLS-1$
				int oldStart1 = Integer.parseInt(label1.split(",")[0]); //$NON-NLS-1$

				return oldStart0 - oldStart1;
			}
			return Policy.getComparator().compare(arg0, arg1);
		};
	}
}