/*******************************************************************************
 * Copyright (c) 2014, 2018 Wind River Systems, Inc. and others. All rights reserved.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.terminal.view.ui.internal.handler;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;

/**
 * Maximize view handler implementation.
 */
public class MaximizeViewHandler extends AbstractTriggerCommandHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		triggerCommand("org.eclipse.ui.window.maximizePart", null); //$NON-NLS-1$
		return null;
	}

}
