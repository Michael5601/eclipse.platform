/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.help.internal.webapp.data;
import java.text.*;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.eclipse.help.internal.*;
import org.eclipse.help.internal.base.*;
import org.eclipse.help.internal.model.*;
import org.eclipse.help.internal.search.*;
import org.eclipse.help.internal.webapp.*;
import org.eclipse.help.internal.webapp.servlet.*;
import org.eclipse.help.internal.workingset.*;

/**
 * Helper class for searchView.jsp initialization
 */
public class SearchData extends RequestData {
	private WebappWorkingSetManager wsmgr;

	// Request parameters
	private String topicHref;
	private String selectedTopicId = "";
	private String searchWord;
	private String workingSetName;

	// search results
	SearchHit[] hits;

	// percentage of indexing completion
	private int indexCompletion = 100;
	// QueryException if any
	private QueryTooComplexException queryException=null;

	/**
	 * Constructs the xml data for the search resuls page.
	 * @param context
	 * @param request
	 */
	public SearchData(
		ServletContext context,
		HttpServletRequest request,
		HttpServletResponse response) {
		super(context, request, response);
		wsmgr = new WebappWorkingSetManager(request, response, getLocale());
		this.topicHref = request.getParameter("topic");
		if (topicHref != null && topicHref.length() == 0)
			topicHref = null;

		searchWord = request.getParameter("searchWord");

		// try loading search results or get the indexing progress info.
		if (isSearchRequest() && !isScopeRequest()) {
			loadSearchResults();
			if(queryException!=null){
				return;
			}
			if (!isProgressRequest()) {
				for (int i = 0; i < hits.length; i++) {
					// the following assume topic numbering as in searchView.jsp
					if (hits[i].getHref().equals(topicHref)) {
						selectedTopicId = "a" + i;
						break;
					}
				}
			}
		}

	}

	/**
	 * Returns true when there is a search request
	 * @return boolean
	 */
	public boolean isSearchRequest() {
		return (request.getParameter("searchWord") != null);
	}

	/**
	 * Return indexed completion percentage
	 */
	public boolean isProgressRequest() {
		return (hits == null && indexCompletion != 100);
	}

	/**
	 * Returns true when there is a request to change the scope (working set)
	 */
	public boolean isScopeRequest() {
		return (request.getParameter("workingSet") != null);
	}

	/**
	 * Return the number of links
	 * @return int
	 */
	public int getResultsCount() {
		return hits.length;
	}

	public String getSelectedTopicId() {
		return selectedTopicId;
	}

	public String getTopicHref(int i) {
		return UrlUtil.getHelpURL(hits[i].getHref());
	}

	public String getTopicLabel(int i) {
		return UrlUtil.htmlEncode(hits[i].getLabel());
	}

	public String getTopicScore(int i) {
		try {
			float score = hits[i].getScore();
			NumberFormat percentFormat =
				NumberFormat.getPercentInstance(UrlUtil.getLocaleObj(request, response));
			return percentFormat.format(score);
		} catch (NumberFormatException nfe) {
			// will display original score string
			return String.valueOf(hits[i].getScore());
		}
	}

	public String getTopicTocLabel(int i) {
		if (hits[i].getToc() != null)
			return UrlUtil.htmlEncode(hits[i].getToc().getLabel());
		else
			return "";
	}
	
	/**
	 * @param i
	 * @return true of result belong to an enabled TOC
	 */
	public boolean isEnabled(int i) {
		String href = hits[i].getHref();
		if (href == null) {
			return false;
		}
		int ix=href.indexOf("?resultof=");
		if(ix>=0){
			href=href.substring(0, ix);
		}
		// Find out if description topic for enabled top level TOCs matches the
		// topic
		ITocElement[] tocs = HelpPlugin.getTocManager().getTocs(getLocale());
		for (int t = 0; t < tocs.length; t++) {
			String descriptionHref = tocs[t].getTocTopicHref();
			if (descriptionHref != null && descriptionHref.length()>0
					&& descriptionHref.equals(href)
					&& HelpBasePlugin.getActivitySupport().isEnabled(
							tocs[t].getHref())) {
				return true;
			}
		}
		// Find out if any contributed toc that is enabled contains the topic
		return isInTocSubtree(href, Arrays.asList(tocs));
	}
	/**
	 * @param href
	 *            href of a topic
	 * @param tocList
	 *            List of ITocElement
	 * @return true if given topic belongs to one of enabled ITocElements or
	 *         their children
	 */
	private boolean isInTocSubtree(String href, List tocList) {
		for (Iterator it = tocList.iterator(); it.hasNext();) {
			ITocElement toc = (ITocElement) it.next();
			if (!HelpBasePlugin.getActivitySupport().isEnabled(toc.getHref())) {
				// TOC is not enabled, check other TOCs
				continue;
			}
			// Check topics in navigation
			if (toc.getOwnedTopic(href) != null) {
				return true;
			}
			// Check extra dir
			if (toc.getOwnedExtraTopic(href)!=null){
				return true;
			}
			// check children TOCs
			if (isInTocSubtree(href, toc.getChildrenTocs())) {
				return true;
			} else {
				// try other TOCs at this level
			}
		}
		return false;
	}

	/**
	 * Return indexed completion percentage
	 */
	public String getIndexedPercentage() {
		return String.valueOf(indexCompletion);
	}

	/**
	 * Returns the search query
	 */
	public String getSearchWord() {
		if (searchWord == null)
			return "";
		else
			return searchWord;
	}

	/**
	 * Returns the list of selected TOC's 
	 */
	public String[] getSelectedTocs() {
		String[] books = request.getParameterValues("scope");
		if (books == null) {
			// select all books
			TocData tocData = new TocData(context, request, response);
			books = new String[tocData.getTocCount()];
			for (int i = 0; i < books.length; i++)
				books[i] = tocData.getTocHref(i);
		}
		return books;
	}

	/**
	 * Returns true if book is within a search scope
	 */
	public boolean isTocSelected(int toc) {
		TocData tocData = new TocData(context, request, response);
		String href = tocData.getTocHref(toc);
		String[] books = request.getParameterValues("scope");
		if (books == null)
			return false;
		for (int i = 0; i < books.length; i++) {
			if (books[i].equals(href)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the working set selected. This is used to display the working set
	 * name in the search banner.
	 * @return String
	 */
	public String getScope() {
		if (workingSetName != null)
			return workingSetName;

		if (isScopeRequest()) {
			workingSetName = request.getParameter("workingSet");
		} else if (isSearchRequest()) {
			workingSetName = request.getParameter("scope");
			// if we have already set the working set, then use it.
			if (workingSetName == null)
				workingSetName = request.getParameter("workingSet");
		} else {
			workingSetName =wsmgr.getCurrentWorkingSet();
		}

		if (workingSetName == null
			|| workingSetName.length() == 0
			|| getMode() == RequestData.MODE_INFOCENTER
			&& wsmgr.getWorkingSet(
		workingSetName)
				== null)
			workingSetName = ServletResources.getString("All", request);
		return workingSetName;
	}

	/**
	 * This method is used to persist the working set name and is called from
	 * the search view, after each search
	 */
	public void saveScope() {
		// if a working set is defined, set it in the preferences
		String workingSet = request.getParameter("scope");
		String lastWS =
			wsmgr.getCurrentWorkingSet();
		if (workingSet != null && !workingSet.equals(lastWS)) {
			wsmgr.setCurrentWorkingSet(workingSet);
		} else if (
			workingSet == null && lastWS != null && lastWS.length() > 0) {
			wsmgr.setCurrentWorkingSet("");
		}
	}
	/**
	 * Call the search engine, and get results or the percentage of 
	 * indexed documents.
	 */
	private void loadSearchResults() {
		try {
			SearchProgressMonitor pm =
				SearchProgressMonitor.getProgressMonitor(getLocale());
			if (pm.isDone()) {
				this.indexCompletion = 100;
				SearchResults results = createHitCollector();
				BaseHelpSystem.getSearchManager().search(
					createSearchQuery(),
					results,
					pm);
				hits = results.getSearchHits();
				if (hits == null) {
					HelpWebappPlugin.logError(
						HelpBaseResources.getString("index_is_busy"),
						null);
				}
				return;
			} else {
				// progress
				indexCompletion = pm.getPercentage();
				if (indexCompletion >= 100) {
					// 38573 We do not have results, so index cannot be 100
					indexCompletion = 100 - 1;
				}
				return;
			}
		}catch (QueryTooComplexException qe){
			queryException=qe;
		} catch (Exception e) {
			this.indexCompletion = 0;
		}

	}
	private ISearchQuery createSearchQuery() {
		String fieldSearchStr = request.getParameter("fieldSearch");
		boolean fieldSearch =
			fieldSearchStr != null
				? new Boolean(fieldSearchStr).booleanValue()
				: false;
		return new SearchQuery(
			searchWord,
			fieldSearch,
			new ArrayList(),
			getLocale());
	}
	private SearchResults createHitCollector() {
		WorkingSet[] workingSets;
		if (request.getParameterValues("scopedSearch") == null) {
			// scopes are working set names
			workingSets = getWorkingSets();
		} else {
			// scopes are books (advanced search)
			workingSets = createTempWorkingSets();
		}

		int maxHits = 500;
		String maxHitsStr = request.getParameter("maxHits");
		if (maxHitsStr != null) {
			try {
				int clientmaxHits = Integer.parseInt(maxHitsStr);
				if (0 < clientmaxHits && clientmaxHits < 500) {
					maxHits = clientmaxHits;
				}
			} catch (NumberFormatException nfe) {
			}
		}
		return new SearchResults(workingSets, maxHits, getLocale());
	}
	/**
	 * @return WorkingSet[] or null
	 */
	private WorkingSet[] getWorkingSets() {
		String[] scopes = request.getParameterValues("scope");
		if (scopes == null) {
			return null;
		}
		// confirm working set exists and use it
		ArrayList workingSetCol = new ArrayList(scopes.length);
		for (int s = 0; s < scopes.length; s++) {
			WorkingSet ws = wsmgr.getWorkingSet(scopes[s]);
			if (ws != null) {
				workingSetCol.add(ws);
			}
		}
		if (workingSetCol.size() == 0) {
			return null;
		}
		return (WorkingSet[]) workingSetCol.toArray(
			new WorkingSet[workingSetCol.size()]);
	}

	/**
	 * @return WorkingSet[] or null
	 */
	private WorkingSet[] createTempWorkingSets() {
		String[] scopes = request.getParameterValues("scope");
		if (scopes == null) {
			// it is possible that filtering is used, but all books are deselected
			return new WorkingSet[0];
		}
		if (scopes.length
			== HelpPlugin.getTocManager().getTocs(getLocale()).length) {
			// do not filter if all books are selected
			return null;
		}
		// create working set from books
		ArrayList tocs = new ArrayList(scopes.length);
		for (int s = 0; s < scopes.length; s++) {
			AdaptableToc toc = wsmgr.getAdaptableToc(scopes[s]);
			if (toc != null) {
				tocs.add(toc);
			}
		}
		AdaptableToc[] adaptableTocs =
			(AdaptableToc[]) tocs.toArray(new AdaptableToc[tocs.size()]);
		WorkingSet[] workingSets = new WorkingSet[1];
		workingSets[0] = wsmgr.createWorkingSet("temp", adaptableTocs);
		return workingSets;
	}
	public String getQueryExceptionMessage(){
		if (queryException==null){
			return null;
		}
		return ServletResources.getString("searchTooComplex",request);
	}

}
