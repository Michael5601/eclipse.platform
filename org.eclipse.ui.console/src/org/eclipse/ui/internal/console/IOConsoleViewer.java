package org.eclipse.ui.internal.console;

import org.eclipse.jface.resource.JFaceColors;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentAdapter;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.LineBackgroundEvent;
import org.eclipse.swt.custom.LineBackgroundListener;
import org.eclipse.swt.custom.LineStyleEvent;
import org.eclipse.swt.custom.LineStyleListener;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleHyperlink;

/**
 * This class is new and experimental. It will likely be subject to significant change before
 * it is finalized.
 * 
 * @since 3.1
 *
 */
public class IOConsoleViewer extends TextViewer implements LineStyleListener, LineBackgroundListener, MouseTrackListener, MouseMoveListener, MouseListener, PaintListener {

    private boolean autoScroll = false;
    private IOConsoleDocumentAdapter documentAdapter;
    private IConsoleHyperlink hyperlink;
    private Cursor handCursor;
    private Cursor textCursor;
    private int consoleWidth = -1;
    
    public IOConsoleViewer(Composite parent, IDocument document) {
        super(parent, SWT.H_SCROLL | SWT.V_SCROLL);
        setDocument(document);
        
        StyledText text = getTextWidget();
        text.setDoubleClickEnabled(true);
        text.addLineStyleListener(this);
        text.addLineBackgroundListener(this);
        text.setEditable(true);
        text.setFont(JFaceResources.getFont(IConsoleConstants.CONSOLE_FONT));
        text.addMouseTrackListener(this);
        text.addPaintListener(this);
		
        document.addDocumentListener(new IDocumentListener() {
            public void documentAboutToBeChanged(DocumentEvent event) {
            }
            public void documentChanged(DocumentEvent event) {
                revealEndOfDocument();
            }
        });
    }
    
    public boolean isAutoScroll() {
        return autoScroll;
    }	
    
    public void setAutoScroll(boolean scroll) {
        autoScroll = scroll;
    }
    
    public void setTabWidth(int tabWidth) {
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.jface.text.TextViewer#handleVerifyEvent(org.eclipse.swt.events.VerifyEvent)
     */
    protected void handleVerifyEvent(VerifyEvent e) {
        IDocument doc = getDocument();        
        String[] legalLineDelimiters = doc.getLegalLineDelimiters();
        String text = e.text;
        
        try {
            IOConsolePartition partition = (IOConsolePartition) doc.getPartition(e.start);
            if (!partition.isReadOnly()) {
                boolean isCarriageReturn = false;
                for (int i = 0; i < legalLineDelimiters.length; i++) {
                    if(e.text.equals(legalLineDelimiters[i])) {
                        isCarriageReturn = true;
                        break;
                    }
                }
                
                if (!isCarriageReturn) {
                    super.handleVerifyEvent(e);
                    return;
                } 
            }
        } catch (BadLocationException e1) {
        }
        
        int length = doc.getLength();
        if (e.start == length) {
            super.handleVerifyEvent(e);
        } else {
            try {
                doc.replace(length, 0, text);
            } catch (BadLocationException e1) {
            }
//            getTextWidget().setCaretOffset(doc.getLength());
            e.doit = false;
        }
    }
    
    protected void revealEndOfDocument() {
        if (autoScroll) {
            StyledText text = getTextWidget();
            int charCount = text.getCharCount();
            text.setCaretOffset(charCount);
        }
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.LineStyleListener#lineGetStyle(org.eclipse.swt.custom.LineStyleEvent)
     */
    public void lineGetStyle(LineStyleEvent event) {
        IDocument document = getDocument();
        if (document.getLength() > 0){
            IOConsolePartition[] partitions = (IOConsolePartition[]) document.getDocumentPartitioner().computePartitioning(event.lineOffset, event.lineText.length());
            StyleRange[] styles = new StyleRange[partitions.length];        
            for (int i = 0; i < partitions.length; i++) {                
                int rangeStart = Math.max(partitions[i].getOffset(), event.lineOffset);
                int rangeLength = partitions[i].getLength();
                styles[i] = partitions[i].getStyleRange(rangeStart, rangeLength);
            }
            event.styles = styles;
        }
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.LineBackgroundListener#lineGetBackground(org.eclipse.swt.custom.LineBackgroundEvent)
     */
    public void lineGetBackground(LineBackgroundEvent event) {
        event.lineBackground = null;
    }

    public void setWordWrap(final boolean wordWrap) {
        ConsolePlugin.getStandardDisplay().asyncExec(new Runnable() {
            public void run() {
                getTextWidget().setWordWrap(wordWrap);
                getTextWidget().redraw();       
            }
        });
    }
 
	/**
	 * @see org.eclipse.swt.events.PaintListener#paintControl(org.eclipse.swt.events.PaintEvent)
	 */
	public void paintControl(PaintEvent e) {
		if (hyperlink != null) {
			IDocument doc = getDocument();
			StyledText text = getTextWidget();
			
			if (doc == null || text == null) {
				return;
			}
			IOConsolePartitioner partitioner = (IOConsolePartitioner)doc.getDocumentPartitioner();
			if (partitioner == null) {
				return;
			}
			
			IRegion linkRegion = partitioner.getRegion(hyperlink);
			if (linkRegion != null) {
				int start = linkRegion.getOffset();
				int end = start + linkRegion.getLength();

//				try {
					Color fontColor = JFaceColors.getActiveHyperlinkText(Display.getCurrent());
					Color color = e.gc.getForeground();
					e.gc.setForeground(fontColor);
					FontMetrics metrics = e.gc.getFontMetrics();
					int height = metrics.getHeight();
					int width = metrics.getAverageCharWidth();
					
					int startLine = text.getLineAtOffset(start);
					int endLine = text.getLineAtOffset(end);

					for (int i = startLine; i <= endLine; i++) {
					    int styleStart = i==startLine ? start : text.getOffsetAtLine(i);
						int styleEnd = i==endLine ? end : text.getOffsetAtLine(i+1);  
												
						Point p1 = text.getLocationAtOffset(styleStart);
						Point p2 = text.getLocationAtOffset(styleEnd-1);
						
						e.gc.drawLine(p1.x, p1.y + height, p2.x + width, p2.y + height);
						
//						String content = doc.get(styleStart, styleEnd-styleStart);
//						e.gc.drawText(content, p1.x, p1.y);
						
					}
					
					e.gc.setForeground(color);
//				} catch (BadLocationException ex) {
//				}
			}
		}
	}
	
	protected Cursor getHandCursor() {
		if (handCursor == null) {
			handCursor = new Cursor(ConsolePlugin.getStandardDisplay(), SWT.CURSOR_HAND);
		}
		return handCursor;
	}
	
	protected Cursor getTextCursor() {
		if (textCursor == null) {
			textCursor = new Cursor(ConsolePlugin.getStandardDisplay(), SWT.CURSOR_IBEAM);
		}
		return textCursor;
	}	
	
	protected void linkEntered(IConsoleHyperlink link) {
		Control control = getTextWidget();
		control.setRedraw(false);
		if (hyperlink != null) {
			linkExited(hyperlink);
		}
		hyperlink = link;
		hyperlink.linkEntered();
		control.setCursor(getHandCursor());
		control.setRedraw(true);
		control.redraw();
		control.addMouseListener(this);
	}
	
	protected void linkExited(IConsoleHyperlink link) {
		link.linkExited();
		hyperlink = null;
		Control control = getTextWidget();
		control.setCursor(getTextCursor());
		control.redraw();
		control.removeMouseListener(this);
	}	

    /* (non-Javadoc)
     * @see org.eclipse.swt.events.MouseTrackListener#mouseEnter(org.eclipse.swt.events.MouseEvent)
     */
    public void mouseEnter(MouseEvent e) {
        getTextWidget().addMouseMoveListener(this);
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.events.MouseTrackListener#mouseExit(org.eclipse.swt.events.MouseEvent)
     */
    public void mouseExit(MouseEvent e) {
        getTextWidget().removeMouseMoveListener(this);
		if (hyperlink != null) {
			linkExited(hyperlink);
		}
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.events.MouseTrackListener#mouseHover(org.eclipse.swt.events.MouseEvent)
     */
    public void mouseHover(MouseEvent e) {
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.events.MouseMoveListener#mouseMove(org.eclipse.swt.events.MouseEvent)
     */
	public void mouseMove(MouseEvent e) {
		int offset = -1;
		try {
			Point p = new Point(e.x, e.y);
			offset = getTextWidget().getOffsetAtLocation(p);
		} catch (IllegalArgumentException ex) {
			// out of the document range
		}
		updateLinks(offset);	
	}
	
	
	/**
	 * The cursor has just be moved to the given offset, the mouse has
	 * hovered over the given offset. Update link rendering.
	 * 
	 * @param offset
	 */
	protected void updateLinks(int offset) {
		if (offset >= 0) {
			IConsoleHyperlink link = getHyperlink(offset);
			if (link != null) {
				if (link.equals(hyperlink)) {
					return;
				} 
				linkEntered(link);
				return;
			}
		}
		if (hyperlink != null) {
			linkExited(hyperlink);
		}		
	}

	
	public IConsoleHyperlink getHyperlink(int offset) {
		if (offset >= 0 && getDocument() != null) {
			Position[] positions = null;
			try {
				positions = getDocument().getPositions(IOConsoleHyperlinkPosition.HYPER_LINK_CATEGORY);
			} catch (BadPositionCategoryException ex) {
				// no links have been added
				return null;
			}
			for (int i = 0; i < positions.length; i++) {
				Position position = positions[i];
				if (offset >= position.getOffset() && offset <= (position.getOffset() + position.getLength())) {
					return ((IOConsoleHyperlinkPosition)position).getHyperLink();
				}
			}
		}
		return null;
	}

    /* (non-Javadoc)
     * @see org.eclipse.swt.events.MouseListener#mouseDoubleClick(org.eclipse.swt.events.MouseEvent)
     */
    public void mouseDoubleClick(MouseEvent e) {
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.events.MouseListener#mouseDown(org.eclipse.swt.events.MouseEvent)
     */
    public void mouseDown(MouseEvent e) {
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.events.MouseListener#mouseUp(org.eclipse.swt.events.MouseEvent)
     */
	public void mouseUp(MouseEvent e) {
	    if (hyperlink != null) {
	        String selection = getTextWidget().getSelectionText();
	        if (selection.length() <= 0) {
	            if (e.button == 1) {
	                hyperlink.linkActivated();
	            }
	        }
	    }
	}

    /* (non-Javadoc)
     * @see org.eclipse.jface.text.TextViewer#createDocumentAdapter()
     */
    protected IDocumentAdapter createDocumentAdapter() {
        if(documentAdapter == null) {
            documentAdapter = new IOConsoleDocumentAdapter(getDocument(), consoleWidth = -1);
        }
        return documentAdapter;
    }

    /**
     * @param consoleWidth
     */
    public void setConsoleWidth(int width) {
        consoleWidth = width;
        if (documentAdapter != null) {
            documentAdapter.setWidth(consoleWidth);
            
        }
    }

}
