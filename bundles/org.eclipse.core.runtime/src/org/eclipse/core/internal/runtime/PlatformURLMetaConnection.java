package org.eclipse.core.internal.runtime;

import java.io.*;
import java.net.URL;
import org.eclipse.core.internal.boot.PlatformURLConnection;
import org.eclipse.core.internal.boot.PlatformURLHandler;
import org.eclipse.core.runtime.*;
import org.osgi.framework.Bundle;

public class PlatformURLMetaConnection extends PlatformURLConnection {
	private Bundle target = null;
	private static boolean isRegistered = false;
	public static final String META = "meta"; //$NON-NLS-1$

	/**
	 * @param url
	 */
	public PlatformURLMetaConnection(URL url) {
		super(url);
	}

	protected URL resolve() throws IOException {
		String spec = url.getFile().trim();
		if (spec.startsWith("/")) //$NON-NLS-1$
			spec = spec.substring(1);
		if (!spec.startsWith(META))
			throw new IOException(NLS.bind(Messages.url_badVariant, url.toString()));
		int ix = spec.indexOf("/", META.length() + 1); //$NON-NLS-1$
		String ref = ix == -1 ? spec.substring(META.length() + 1) : spec.substring(META.length() + 1, ix);
		String id = getId(ref);
		target = InternalPlatform.getDefault().getBundle(id);
		if (target == null)
			throw new IOException(NLS.bind(Messages.url_resolvePlugin, url.toString())); 
		IPath path = Platform.getStateLocation(target);
		if (ix != -1 || (ix + 1) <= spec.length())
			path = path.append(spec.substring(ix + 1));
		return new URL("file", null, path.toString()); //$NON-NLS-1$
	}

	public static void startup() {
		// register connection type for platform:/meta handling
		if (isRegistered)
			return;
		PlatformURLHandler.register(META, PlatformURLMetaConnection.class);
		isRegistered = true;
	}

	/* (non-Javadoc)
	 * @see java.net.URLConnection#getOutputStream()
	 */
	public OutputStream getOutputStream() throws IOException {
		//This is not optimal but connection is a private ivar in super.
		URL resolved = getResolvedURL();
		if (resolved != null) {
			String fileString = resolved.getFile();
			if (fileString != null) {
				File file = new File(fileString);
				String parent = file.getParent();
				if (parent != null)
					new File(parent).mkdirs();
				return new FileOutputStream(file);
			}
		}
		return null;
	}
}
