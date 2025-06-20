/*******************************************************************************
 * Copyright (c) 2005, 2015 IBM Corporation and others.
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
package org.eclipse.core.internal.filesystem;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.IFileSystem;
import org.eclipse.core.filesystem.provider.FileSystem;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionDelta;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IRegistryChangeEvent;
import org.eclipse.core.runtime.IRegistryChangeListener;
import org.eclipse.core.runtime.RegistryFactory;
import org.eclipse.osgi.util.NLS;

/**
 * The class manages internal implementation of methods on EFS.
 * This includes maintaining a list of file system extensions.
 */
public class InternalFileSystemCore implements IRegistryChangeListener {
	private static final InternalFileSystemCore INSTANCE = new InternalFileSystemCore();

	/**
	 * A map (String -&gt; (IConfigurationElement or IFileSystem)) mapping URI
	 * scheme to the file system for that scheme.  If the corresponding file
	 * system has never been accessed, then the map contains the configuration
	 * element for the extension.  Once the file system has been created, the
	 * map contains the IFileSystem instance for that scheme.
	 */
	private ConcurrentMap<String, Object> fileSystems;

	/**
	 * Returns the singleton instance of this class.
	 * @return The singleton instance.
	 */
	public static InternalFileSystemCore getInstance() {
		return INSTANCE;
	}

	/**
	 * This class has a singleton instance.
	 */
	private InternalFileSystemCore() {
		super();
		RegistryFactory.getRegistry().addRegistryChangeListener(this);
	}

	/**
	 * Implements the method {@link EFS#getFileSystem(String)}
	 *
	 * @param scheme The URI scheme of the file system
	 * @return The file system
	 * @throws CoreException on filesystem related errors
	 */
	public IFileSystem getFileSystem(String scheme) throws CoreException {
		if (scheme == null) {
			throw new NullPointerException();
		}
		final Map<String, Object> registry = getFileSystemRegistry();
		Object result = registry.get(scheme);
		if (result == null) {
			Policy.error(EFS.ERROR_INTERNAL, NLS.bind(Messages.noFileSystem, scheme));
		}
		if (result instanceof IFileSystem) {
			return (IFileSystem) result;
		}
		try {
			IConfigurationElement element = (IConfigurationElement) result;
			FileSystem fs = (FileSystem) element.createExecutableExtension("run"); //$NON-NLS-1$
			fs.initialize(scheme);
			synchronized (this) {
				result = registry.get(scheme);
				if (result instanceof IFileSystem) {
					return (IFileSystem) result;
				}
				//store the file system instance so we don't have to keep recreating it
				registry.put(scheme, fs);
				return fs;
			}
		} catch (CoreException e) {
			//remove this invalid file system from the registry
			registry.remove(scheme);
			throw e;
		}
	}

	/**
	 * Implements the method {@link EFS#getLocalFileSystem()}
	 *
	 * @return The local file system
	 */
	public IFileSystem getLocalFileSystem() {
		try {
			return getFileSystem(EFS.SCHEME_FILE);
		} catch (CoreException e) {
			//the local file system is always present
			throw new Error(e);
		}
	}

	/**
	 * Implements the method {@link EFS#getStore(URI)}
	 *
	 * @param uri The URI of the store to retrieve
	 * @return The file store corresponding to the given URI
	 * @throws CoreException on filesystem related errors or missing URI scheme
	 */
	public IFileStore getStore(URI uri) throws CoreException {
		final String scheme = uri.getScheme();
		if (scheme == null) {
			Policy.error(EFS.ERROR_INTERNAL, Messages.noScheme + uri);
		}
		return getFileSystem(scheme).getStore(uri);
	}

	/**
	 * Returns the fully initialized file system registry
	 * @return The file system registry
	 */
	private synchronized ConcurrentMap<String, Object> getFileSystemRegistry() {
		if (fileSystems == null) {
			fileSystems = new ConcurrentHashMap<>();
			IExtensionPoint point = RegistryFactory.getRegistry().getExtensionPoint(EFS.PI_FILE_SYSTEM, EFS.PT_FILE_SYSTEMS);
			IExtension[] extensions = point.getExtensions();
			for (IExtension extension : extensions) {
				IConfigurationElement[] elements = extension.getConfigurationElements();
				for (IConfigurationElement element : elements) {
					if ("filesystem".equals(element.getName())) { //$NON-NLS-1$
						String scheme = element.getAttribute("scheme"); //$NON-NLS-1$
						if (scheme != null) {
							fileSystems.put(scheme, element);
						}
					}
				}
			}
		}
		return fileSystems;
	}

	@Override
	public void registryChanged(IRegistryChangeEvent event) {
		IExtensionDelta[] changes = event.getExtensionDeltas(EFS.PI_FILE_SYSTEM, EFS.PT_FILE_SYSTEMS);
		if (changes.length == 0) {
			return;
		}
		synchronized (this) {
			//let the registry be rebuilt lazily
			fileSystems = null;
		}
	}

	/**
	 * Implements {@link EFS#getNullFileSystem()}.
	 * @return The null file system
	 */
	public IFileSystem getNullFileSystem() {
		try {
			return getFileSystem(EFS.SCHEME_NULL);
		} catch (CoreException e) {
			//the local file system is always present
			throw new Error(e);
		}
	}
}