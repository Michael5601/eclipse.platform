/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ant.internal.ui.console;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ant.internal.ui.AntUIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.ui.console.FileLink;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.console.IPatternMatchListenerDelegate;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;

public class JikesJavacPatternMatcher implements IPatternMatchListenerDelegate {

    private TextConsole fConsole;
    private Pattern fFilePattern = Pattern.compile("\\S*\\.java"); //$NON-NLS-1$
    private Pattern fLineNumberPattern = Pattern.compile("\\d+"); //$NON-NLS-1$

    public void connect(TextConsole console) {
        fConsole = console;
    }

    public void disconnect() {
        fConsole = null;
    }

    /*
     *     [javac] Found 1 semantic error compiling "/Users/kevinbarnes/Eclipse/runtime-workspace/Foo/src/CarriageReturn.java":
     *     [javac] 3.         System.out.printer("\r");
     */
    public void matchFound(PatternMatchEvent event) {
        int eventOffset = event.getOffset();
        int eventLength = event.getLength();
        IDocument document = fConsole.getDocument();
        String matchedText;
        try {
            matchedText = document.get(eventOffset, eventLength);
        } catch (BadLocationException e) {
            AntUIPlugin.log(e);
            return;
        }

        Matcher matcher = null;
        synchronized (fFilePattern) {
            matcher = fFilePattern.matcher(matchedText);
        }
        String filePath = null;
        if (matcher.find()) {
            filePath = matcher.group();
            filePath = filePath.substring(1); // pattern returns the first "
            int matchStart = matcher.start() + 1;
            eventOffset += matchStart;
            eventLength = matcher.end() - matchStart;
        }
        if (filePath == null) {
            return; 
        }

        IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocation(new Path(filePath));
        IFile file = null;
        if (files.length > 0) {
            file = files[0];
        }
        if (file == null) {
            return; 
        }
        
        int lineNumber = getLineNumber(document, eventOffset);
        
        FileLink link = new FileLink(file, null, -1, -1, lineNumber);
        try {
            fConsole.addHyperlink(link, eventOffset, eventLength);
        } catch (BadLocationException e) {
            AntUIPlugin.log(e);
        }
    }

    private int getLineNumber(IDocument document, int eventOffset) {
        try {
            int fileLine = document.getLineOfOffset(eventOffset);
            IRegion region = document.getLineInformation(++fileLine);
            String lineLine = document.get(region.getOffset(), region.getLength());
            Matcher matcher = null;
            synchronized (fLineNumberPattern) {
                matcher = fLineNumberPattern.matcher(lineLine);
            }
            if (matcher.find()) {
                String lineString = matcher.group();
                return Integer.parseInt(lineString);
            }
            
        } catch (BadLocationException e) {
            AntUIPlugin.log(e);
        }
        return -1;
    }


}