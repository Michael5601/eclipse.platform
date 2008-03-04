/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ui.internal.console;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IPatternMatchListener;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;

public class ConsolePatternMatcher implements IDocumentListener {

    private MatchJob fMatchJob = new MatchJob();

    /**
     * Collection of compiled pattern match listeners
     */
    private ArrayList fPatterns = new ArrayList();

    private TextConsole fConsole;

    private boolean fFinalMatch;

	private boolean fScheduleFinal;

    public ConsolePatternMatcher(TextConsole console) {
        fConsole = console;
    }

    private class MatchJob extends Job {
        MatchJob() {
            super("Match Job"); //$NON-NLS-1$
            setSystem(true);
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.eclipse.core.runtime.jobs.Job#run(org.eclipse.core.runtime.IProgressMonitor)
         */
        protected IStatus run(IProgressMonitor monitor) {
			IDocument doc = fConsole.getDocument();
			String text = null;
			int prevBaseOffset = -1;
			if (doc != null && !monitor.isCanceled()) {
				int endOfSearch = doc.getLength();
				int indexOfLastChar = endOfSearch;
				if (indexOfLastChar > 0) {
					indexOfLastChar--;
				}
				int lastLineToSearch = 0;
				int offsetOfLastLineToSearch = 0;
				try {
					lastLineToSearch = doc.getLineOfOffset(indexOfLastChar);
					offsetOfLastLineToSearch = doc.getLineOffset(lastLineToSearch);
				} catch (BadLocationException e) {
					// perhaps the buffer was re-set
					return Status.OK_STATUS;
				}
				Object[] patterns = null;
				synchronized (fPatterns) {
					patterns = fPatterns.toArray();
				}
				for (int i = 0; i < patterns.length; i++) {
					if (monitor.isCanceled()) {
						break;
					}
					CompiledPatternMatchListener notifier = (CompiledPatternMatchListener) patterns[i];
					int baseOffset = notifier.end;
					int lengthToSearch = endOfSearch - baseOffset;
					if (lengthToSearch > 0) {
						try {
							if (prevBaseOffset != baseOffset) {
								// reuse the text string if possible
								text = doc.get(baseOffset, lengthToSearch);
							}
							Matcher reg = notifier.pattern.matcher(text);
							Matcher quick = null;
							if (notifier.qualifier != null) {
								quick = notifier.qualifier.matcher(text);
							}
							int startOfNextSearch = 0;
							int endOfLastMatch = -1;
							int lineOfLastMatch = -1;
							while ((startOfNextSearch < lengthToSearch) && !monitor.isCanceled()) {
								if (quick != null) {
									if (quick.find(startOfNextSearch)) {
										// start searching on the beginning
										// of the line where the potential
										// match was found, or after the
										// last match on the same line
										int matchLine = doc.getLineOfOffset(baseOffset + quick.start());
										if (lineOfLastMatch == matchLine) {
											startOfNextSearch = endOfLastMatch;
										} else {
											startOfNextSearch = doc.getLineOffset(matchLine) - baseOffset;
										}
									} else {
										startOfNextSearch = lengthToSearch;
									}
								}
								if (startOfNextSearch < 0) {
									startOfNextSearch = 0;
								}
								if (startOfNextSearch < lengthToSearch) {
									if (reg.find(startOfNextSearch)) {
										endOfLastMatch = reg.end();
										lineOfLastMatch = doc.getLineOfOffset(baseOffset + endOfLastMatch - 1);
										int regStart = reg.start();
										IPatternMatchListener listener = notifier.listener;
										if (listener != null && !monitor.isCanceled()) {
											listener.matchFound(new PatternMatchEvent(fConsole, baseOffset + regStart, endOfLastMatch - regStart));
										}
										startOfNextSearch = endOfLastMatch;
									} else {
										startOfNextSearch = lengthToSearch;
									}
								}
							}
							// update start of next search to the last line
							// searched
							// or the end of the last match if it was on the
							// line that
							// was last searched
							if (lastLineToSearch == lineOfLastMatch) {
								notifier.end = baseOffset + endOfLastMatch;
							} else {
								notifier.end = offsetOfLastLineToSearch;
							}
						} catch (BadLocationException e) {
							ConsolePlugin.log(e);
						}
					}
					prevBaseOffset = baseOffset;
				}
			}

			if (fFinalMatch) {
				disconnect();
				fConsole.matcherFinished();
			} else if (fScheduleFinal) {
				fFinalMatch = true;
				schedule();
			}

			return Status.OK_STATUS;
		}

        public boolean belongsTo(Object family) {
            return family == fConsole;
        }
        
        
    }

    private class CompiledPatternMatchListener {
        Pattern pattern;

        Pattern qualifier;

        IPatternMatchListener listener;

        int end = 0;

        CompiledPatternMatchListener(Pattern pattern, Pattern qualifier, IPatternMatchListener matchListener) {
            this.pattern = pattern;
            this.listener = matchListener;
            this.qualifier = qualifier;
        }

        public void dispose() {
            listener.disconnect();
            pattern = null;
            qualifier = null;
            listener = null;
        }
    }

    /**
	 * Adds the given pattern match listener to this console. The listener will
	 * be connected and receive match notifications.
	 * 
	 * @param matchListener
	 *            the pattern match listener to add
	 */
    public void addPatternMatchListener(IPatternMatchListener matchListener) {
        synchronized (fPatterns) {
            
            // check for dups
            for (Iterator iter = fPatterns.iterator(); iter.hasNext();) {
                CompiledPatternMatchListener element = (CompiledPatternMatchListener) iter.next();
                if (element.listener == matchListener) {
                    return;
                }
            }
            
            if (matchListener == null || matchListener.getPattern() == null) {
                throw new IllegalArgumentException("Pattern cannot be null"); //$NON-NLS-1$
            }

            Pattern pattern = Pattern.compile(matchListener.getPattern(), matchListener.getCompilerFlags());
            String qualifier = matchListener.getLineQualifier();
            Pattern qPattern = null;
            if (qualifier != null) {
                qPattern = Pattern.compile(qualifier, matchListener.getCompilerFlags());
            }
            CompiledPatternMatchListener notifier = new CompiledPatternMatchListener(pattern, qPattern, matchListener);
            fPatterns.add(notifier);
            matchListener.connect(fConsole);
            fMatchJob.schedule();
        }
    }

    /**
     * Removes the given pattern match listener from this console. The listener
     * will be disconnected and will no longer receive match notifications.
     * 
     * @param matchListener
     *            the pattern match listener to remove.
     */
    public void removePatternMatchListener(IPatternMatchListener matchListener) {
        synchronized (fPatterns) {
            for (Iterator iter = fPatterns.iterator(); iter.hasNext();) {
                CompiledPatternMatchListener element = (CompiledPatternMatchListener) iter.next();
                if (element.listener == matchListener) {
                    iter.remove();
                    matchListener.disconnect();
                }
            }
        }
    }

    public void disconnect() {
        fMatchJob.cancel();
        synchronized (fPatterns) {
            Iterator iterator = fPatterns.iterator();
            while (iterator.hasNext()) {
                CompiledPatternMatchListener notifier = (CompiledPatternMatchListener) iterator.next();
                notifier.dispose();
            }
            fPatterns.clear();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.text.IDocumentListener#documentAboutToBeChanged(org.eclipse.jface.text.DocumentEvent)
     */
    public void documentAboutToBeChanged(DocumentEvent event) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.text.IDocumentListener#documentChanged(org.eclipse.jface.text.DocumentEvent)
     */
    public void documentChanged(DocumentEvent event) {
        if (event.fLength > 0) {
            synchronized (fPatterns) {
                if (event.fDocument.getLength() == 0) {
                    // document has been cleared, reset match listeners
                    Iterator iter = fPatterns.iterator();
                    while (iter.hasNext()) {
                        CompiledPatternMatchListener notifier = (CompiledPatternMatchListener) iter.next();
                        notifier.end = 0;
                    }
                } else {
                    if (event.fOffset == 0) { 
                        //document was trimmed
                        Iterator iter = fPatterns.iterator();
                        while (iter.hasNext()) {
                            CompiledPatternMatchListener notifier = (CompiledPatternMatchListener) iter.next();
                            notifier.end = notifier.end > event.fLength ? notifier.end-event.fLength : 0;
                        }
                    }
                }
            }
        }
        fMatchJob.schedule();
    }

    
    public void forceFinalMatching() {
    	fScheduleFinal = true;
    	fMatchJob.schedule();
    }

}
