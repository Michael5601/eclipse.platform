/**********************************************************************
 * Copyright (c) 2002, 2004 IBM Corporation and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.tools.metadata;

import java.io.*;

/**
 * An abstract implementation for dumpers that generate bare text dumps by 
 * sequentially reading input streams.
 */
public abstract class AbstractDumper implements IDumper {

	/**
	 * Reads a given file and produces a dump object. Provides a template implementation for 
	 * <code>IDumper.dump(java.io.File)</code>. 
	 * <p>Subclasses must implement 
	 * <code>getStringDumpingStrategy(InputStream)</code> in order to select the 
	 * real dumping behaviour. This method will call 
	 * <code>IStringDumpingStrategy#dumpStringContents(DataInputStream)</code> on 
	 * the returned strategy. If, after calling that method,  there are still bytes 
	 * to be read in the input stream, 
	 * <code>getStringDumpingStrategy(InputStream)</code> will be called again in 
	 * order to select another strategy to read the remaining contents, and so on.
	 * </p> 
	 *
	 * <p>Subclasses can also select which type of low-level InputStream will be 
	 * used to read the file to be dumped by overriding
	 * <code>openInputStream(File)</code>.</p> 
	 * 
	 * @param file the file to be dumped
	 * @return a dump object representing the contents of the dumped file
	 * @see org.eclipse.core.tools.metadata.IDumper#dump(java.io.File)
	 * @see #getStringDumpingStrategy(DataInputStream)
	 * @see IStringDumpingStrategy#dumpStringContents(DataInputStream)
	 * @see #openInputStream(File)
	 */
	public IDump dump(File file) {
		DataInputStream dataInput = null;
		MeteredInputStream meteredInput = null;
		Dump dump = new Dump();
		dump.setFile(file);
		StringBuffer contents = new StringBuffer(40);
		try {
			//uses a metered input stream in order to count the number of bytes read
			meteredInput = new MeteredInputStream(openInputStream(file));
			dumpContents(meteredInput, contents); //$NON-NLS-1$
		} catch (PartialDumpException pde) {
			// ensure we remember any partial contents
			if (pde.getPartialContents() != null)
				contents.append(pde.getPartialContents());
			dump.setFailureReason(pde);
		} catch (Exception e) {
			dump.setFailureReason(e);
		} finally {
			if (dataInput != null)
				try {
					dataInput.close();
				} catch (IOException ioe) {
					if (!dump.isFailed())
						dump.setFailureReason(ioe);
				}
		}
		dump.setContents(contents.toString());
		if (meteredInput != null)
			dump.setOffset(meteredInput.getOffset());
		return dump;
	}

	/**
	 * Does the actual  work. Subclasses must implement this method to define
	 * dumping behavior. The results are added to the <code>contents</code>
	 * string buffer.
	 *  
	 * @param input
	 * @param contents
	 * @throws IOException
	 * @throws Exception
	 * @throws DumpException
	 */
	protected abstract void dumpContents(PushbackInputStream input, StringBuffer contents) throws IOException, Exception, DumpException;

	/**
	 * Opens an input stream connected to the file object provided. Provides an 
	 * opportunity for subclasses to select a different input stream class. By 
	 * default, returns a <code>FileInputStream</code> object.
	 * 
	 * @param file the file to be opened
	 * @return an input stream connected to the file provided as 
	 * argument
	 * @throws IOException if an exception happens while opening the inpuut stream
	 */
	protected InputStream openInputStream(File file) throws IOException {
		return new FileInputStream(file);
	}

}