/**
 * Copyright � 2004 The JA-SIG Collaborative.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the JA-SIG Collaborative
 *    (http://www.jasig.org/)."
 *
 * THIS SOFTWARE IS PROVIDED BY THE JA-SIG COLLABORATIVE "AS IS" AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE JA-SIG COLLABORATIVE OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.jasig.portal.layout.channels;

import org.jasig.portal.ChannelRegistryManager;
import org.jasig.portal.ChannelStaticData;
import org.jasig.portal.PortalException;
import org.jasig.portal.PortalControlStructures;
import org.jasig.portal.utils.CommonUtils;
import org.jasig.portal.utils.XSLT;
import org.jasig.portal.utils.DocumentFactory;
import org.jasig.portal.layout.IAggregatedLayout;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import java.util.Vector;
import java.util.Set;
import java.util.Iterator;
import java.util.Enumeration;
import java.util.Collection;

  /**
   * A channel for adding new content to a layout.
   * @author Michael Ivanov, mvi@immagic.com
   * @version $Revision$
   */
public class CContentSubscriber extends FragmentManager {

	private static final String sslLocation = "/org/jasig/portal/channels/CContentSubscriber/CContentSubscriber.ssl";
	private static Document channelRegistry;
	private Document registry;
	private Vector expandedItems;
	private Vector condensedItems;
	
	private final static String CHANNEL = "channel";
	private final static String FRAGMENT = "fragment";
	private final static String CATEGORY = "category";	 


    private class ListItem {
      
      private String itemId;
      private String name;
      private String categoryId;
      
      public ListItem ( String categoryId, String itemId, String name ) {
      	this.categoryId = categoryId;
      	this.itemId = itemId; 		
      	this.name = name;
      }
      
      public String getItemId() {
      	return itemId;
      }
      
      public String getCategoryId() {
      	return categoryId;
      }
      
      public String getName() {
      	return name;
      }
      
      public boolean equals ( Object obj ) {
      	if ( obj == null || !(obj instanceof ListItem) )
      	  return false; 
      	  ListItem item = (ListItem) obj;	
      	  return ( categoryId.equals(item.getCategoryId()) 
      	           && itemId.equals(item.getItemId()) 
      	           && name.equals(item.getName()) ); 	
      }
      
    }

    public CContentSubscriber() {
       super();
	   expandedItems = new Vector();
	   condensedItems = new Vector();
    }

	protected void analyzeParameters( XSLT xslt ) throws PortalException {
		
	  try {
		  
		
			String fragmentId = CommonUtils.nvl(runtimeData.getParameter("uPcCS_fragmentID"));
		    String channelId = CommonUtils.nvl(runtimeData.getParameter("uPcCS_channelID"));
		    String categoryId = CommonUtils.nvl(runtimeData.getParameter("uPcCS_categoryID"));
			String action = CommonUtils.nvl(runtimeData.getParameter("uPcCS_action"));
		    String channelState = CommonUtils.nvl(runtimeData.getParameter("channelState"));
			boolean all = false,
			        expand = action.equals("expand"),
			        condense = action.equals("condense");    
	  	     
	  	     Vector tagNames = new Vector();         
		           
		if ( expand || condense ) {
			 		 	
				if ( fragmentId.equals("all") ) {
				   all = true;
				   tagNames.add(FRAGMENT); 		 
				}      
				
				if ( channelId.equals("all") ) { 
				   all = true;
				   tagNames.add(CHANNEL);
				}   
				
				if ( categoryId.equals("all") ) {
				   all = true;
				   tagNames.add(CATEGORY);
				}   
				   	 
				   
			if ( !all  ) {
				  String itemName = CHANNEL;
				  String itemId = channelId;
				  if ( fragmentId.length() > 0 ) {
			          itemId = fragmentId;
					  itemName = FRAGMENT;
				  } else if ( categoryId.length() > 0 && channelId.length() == 0 ) {
				      itemId = categoryId;
					  itemName = CATEGORY;
				  }	  	

				  ListItem item = new ListItem(categoryId,itemId,itemName);
			
				  if ( expand ) {
				    expandedItems.add(item);
				    condensedItems.remove(item);
				  } else {
				    condensedItems.add(item);  
				    expandedItems.remove(item);
				  }           
			}
				 
			
		} else if ( action.equals("init") ) {
			 	//if ( alm.isFragmentLoaded() )
			 	alm.loadUserLayout();	
			 	refreshFragmentMap(); 
			 	initRegistry();
		}
			 
		Vector removedItems = new Vector(); 
						 
		if ( !all ) {   
				
		     Vector items = expandedItems;		
		     for ( int k = 0; k < 2; items = condensedItems, k++ ) {		 
			  for ( int i = 0; i < items.size(); i++ ) {	 
			   for ( Iterator iter = items.iterator(); iter.hasNext(); ) {
			    ListItem item = (ListItem) iter.next();
			    String xPathQuery = null;
			    if ( CHANNEL.equals(item.getName()) )
			     xPathQuery = "//channel[../@ID='"+item.getCategoryId()+"' and @ID='"+item.getItemId()+"']";
			    else 
			     xPathQuery = "//*[@ID='"+item.getItemId()+"']";   
				Element elem = (Element) XPathAPI.selectSingleNode(registry,xPathQuery);
				if ( elem != null ) 
				 elem.setAttribute("view",(k==0)?"expanded":"condensed");
				else
				 removedItems.add(item);
			   } 
			  }
			    items.removeAll(removedItems);
		     }	           
			  
		} else { 
		    
		      for ( int i = 0; i < tagNames.size(); i++ ) {
		        String tagName = (String) tagNames.get(i);	
			    for ( Iterator iter = expandedItems.iterator(); iter.hasNext(); ) {
			   	 ListItem item = (ListItem)iter.next();
			  	 if ( tagName.equals(item.getName()) )
			  	  removedItems.add(item);
			    }		
			      expandedItems.removeAll(removedItems);
			    for ( Iterator iter = condensedItems.iterator(); iter.hasNext(); ) {
				 ListItem item = (ListItem)iter.next();
				 if ( tagName.equals(item.getName()) )
				  removedItems.add(item);
			    }	
				  condensedItems.removeAll(removedItems);
			    NodeList nodeList = registry.getElementsByTagName(tagName);
			    for ( int k = 0; k < nodeList.getLength(); k++ ) {
				 Element node = (Element) nodeList.item(k);
				 node.setAttribute("view",(expand)?"expanded":"condensed");
			   } 
		      }
		     
		 }  
		     
		    
		     passAllParameters(xslt);
		     
	  } catch ( Exception e ) {
	  	  e.printStackTrace();
	  	  throw new PortalException(e.getMessage());	     
	  }
			 
	}		 	

    private void passAllParameters ( XSLT xslt ) {
       for ( Enumeration params = runtimeData.getParameterNames(); params.hasMoreElements(); ) {
         String paramName = (String) params.nextElement();
         xslt.setStylesheetParameter(paramName,runtimeData.getParameter(paramName));
       }  	 
    }

    // Returns only subscribable pulled fragments that do not exist the current user layout
	protected Collection getFragments() throws PortalException {
		 Collection pulledFragments = alm.getSubscribableFragments();
		 Set layoutFragments = ((IAggregatedLayout)alm.getUserLayout()).getFragmentIds();
		 pulledFragments.removeAll(layoutFragments); 
		 return pulledFragments;    
	}

    public void initRegistry() throws PortalException {
      	registry = DocumentFactory.getNewDocument();
      	registry.appendChild(registry.importNode(channelRegistry.getDocumentElement(),true));	
        getFragmentList(registry,registry.getDocumentElement());
    }

	/**
		 * Passes portal control structure to the channel.
		 * @see PortalControlStructures
		 */
	public void setPortalControlStructures(PortalControlStructures pcs) throws PortalException {
			super.setPortalControlStructures(pcs);
			if ( alm == null )  
			  throw new PortalException ("The layout manager must have type IAgreggatedUserLayoutManager!");  
	}

    public void setStaticData (ChannelStaticData sd) throws PortalException {
       super.setStaticData(sd);
       if ( channelRegistry == null )
        channelRegistry = ChannelRegistryManager.getChannelRegistry(staticData.getPerson());
    }

    public void renderXML (ContentHandler out) throws PortalException {
    	
      
      XSLT xslt = XSLT.getTransformer(this, runtimeData.getLocales());
      analyzeParameters(xslt);
	  //System.out.println ( "registry:\n" + org.jasig.portal.utils.XML.serializeNode(registry));    
      xslt.setXML(registry);
      xslt.setXSL(sslLocation, "contentSubscriber", runtimeData.getBrowserInfo());
      xslt.setTarget(out);
      xslt.setStylesheetParameter("baseActionURL", runtimeData.getBaseActionURL());
      xslt.transform();
      
    }

  }