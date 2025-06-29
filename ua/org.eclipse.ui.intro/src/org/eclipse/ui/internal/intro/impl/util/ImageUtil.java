/*******************************************************************************
 * Copyright (c) 2004, 2006 IBM Corporation and others.
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
package org.eclipse.ui.internal.intro.impl.util;

import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.internal.intro.impl.IntroPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Convenience class for Images.
 */
public final class ImageUtil {

	/**
	 * Image keys, to be used by plugin (intro) registry.
	 */
	// Default images
	public static final String DEFAULT_ROOT_LINK = "rootLink"; //$NON-NLS-1$
	public static final String DEFAULT_SMALL_ROOT_LINK = "rootLinkSmall"; //$NON-NLS-1$
	public static final String DEFAULT_FORM_BG = "formBg"; //$NON-NLS-1$
	public static final String DEFAULT_LINK = "link"; //$NON-NLS-1$

	// Standby images
	public static final String BACK = "back"; //$NON-NLS-1$
	public static final String HELP_TOPIC = "helpTopic"; //$NON-NLS-1$

	// Launch bar image
	public static final String RESTORE_WELCOME = "restoreWelcome"; //$NON-NLS-1$

	// Viewer images
	public static final String INTRO_MODEL_LEAF = "leaf"; //$NON-NLS-1$
	public static final String INTRO_MODEL_CONTAINER = "container"; //$NON-NLS-1$
	public static final String OPEN_ITNRO_VIEW = "introView"; //$NON-NLS-1$

	public static final String CONFIG_EXTENSION = "configExtension";//$NON-NLS-1$

	// Image location
	public static final String ICONS_PATH = "$nl$/icons/"; //$NON-NLS-1$

	/**
	 * Convenience method to create an image descriptor from the Intro plugin.
	 *
	 * Method assumes that images are under the "icons" directory, so don't
	 * append that directory name for "imageName".
	 */
	public static ImageDescriptor createImageDescriptor(String imageName) {
		return createImageDescriptor(FrameworkUtil.getBundle(ImageUtil.class), ICONS_PATH + imageName);
	}

	/**
	 * Convenience method to create an image descriptor.
	 */
	public static ImageDescriptor createImageDescriptor(Bundle bundle,
			String imageName) {
		try {
			URL imageUrl = FileLocator.find(bundle, IPath.fromOSString(imageName), null);
			if (imageUrl != null) {
				ImageDescriptor desc = ImageDescriptor.createFromURL(imageUrl);
				return desc;
			}
		} catch (Exception e) {
			// Should never be here.
			Log.error("could not create Image Descriptor", e); //$NON-NLS-1$
		}
		Log.warning("could not create Image Descriptor for: " + imageName //$NON-NLS-1$
				+ " in bundle: " + bundle.getSymbolicName()); //$NON-NLS-1$
		return ImageDescriptor.getMissingImageDescriptor();
	}

	/**
	 * Convenience method to create an image descriptor.
	 */
	public static ImageDescriptor createImageDescriptor(IPath base,
			String imageName) {
		try {
			URL imageUrl = new URL(base.append(imageName).toOSString());
			if (imageUrl != null) {
				ImageDescriptor desc = ImageDescriptor.createFromURL(imageUrl);
				return desc;
			}
		} catch (Exception e) {
			// Should never be here.
			Log.error("could not create Image Descriptor", e); //$NON-NLS-1$
		}
		Log.warning("could not create Image Descriptor for: " + imageName); //$NON-NLS-1$
		return ImageDescriptor.getMissingImageDescriptor();
	}

	/**
	 * Convenience method to create an image from the Intro plugin.
	 *
	 * Method assumes that images are under the "icons" directory, so don't
	 * append that directory name for "imageName".
	 */
	public static Image createImage(String imageName) {
		try {
			ImageDescriptor imageDsc = createImageDescriptor(imageName);
			return imageDsc.createImage();
		} catch (Exception e) {
			// Should never be here.
			Log.error("could not create Image", e); //$NON-NLS-1$
			return ImageDescriptor.getMissingImageDescriptor().createImage();
		}
	}

	/**
	 * Util method for image re-use in Intro Plugin.
	 */
	public static Image getImage(String key) {
		// INTRO: Image registry should not have the same life span
		// as the intro plug-in. It should be disposed when
		// presentation is disposed, otherwise images will
		// stay around once Inro has been loaded.
		return IntroPlugin.getDefault().getVolatileImageRegistry().get(key);
	}

	public static boolean hasImage(String key) {
		ImageRegistry registry = IntroPlugin.getDefault().getVolatileImageRegistry();
		return (registry.getDescriptor(key) != null);
	}

	/**
	 * Register an image descriptor in the Intro Plugin image registry. Has no
	 * effect if the key has already been registered.
	 */
	public static void registerImage(String key, String imageName) {
		ImageRegistry registry = IntroPlugin.getDefault().getVolatileImageRegistry();
		if (registry.getDescriptor(key) != null) {
			// key has already been registered. do nothing.
			return;
		}
		registry.put(key, createImageDescriptor(imageName));
	}

	public static void registerImage(String key, Bundle bundle, String imageName) {

		ImageRegistry registry = IntroPlugin.getDefault().getVolatileImageRegistry();
		if (registry.getDescriptor(key) != null) {
			// key has already been registered. do nothing.
			return;
		}
		registry.put(key, createImageDescriptor(bundle, imageName));
	}

	public static void registerImage(String key, IPath base, String imageName) {
		ImageRegistry registry = IntroPlugin.getDefault().getVolatileImageRegistry();
		if (registry.getDescriptor(key) != null) {
			// key has already been registered. do nothing.
			return;
		}
		registry.put(key, createImageDescriptor(base, imageName));
	}
}
