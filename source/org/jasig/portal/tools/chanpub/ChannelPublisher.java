/**
 * Copyright � 2003 The JA-SIG Collaborative.  All rights reserved.
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

package org.jasig.portal.tools.chanpub;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jasig.portal.ChannelCategory;
import org.jasig.portal.ChannelDefinition;
import org.jasig.portal.ChannelParameter;
import org.jasig.portal.ChannelRegistryStoreFactory;
import org.jasig.portal.IChannelRegistryStore;
import org.jasig.portal.groups.IGroupMember;
import org.jasig.portal.security.IAuthorizationPrincipal;
import org.jasig.portal.security.IPermission;
import org.jasig.portal.security.IPerson;
import org.jasig.portal.security.IUpdatingPermissionManager;
import org.jasig.portal.security.provider.PersonImpl;
import org.jasig.portal.services.AuthorizationService;
import org.jasig.portal.services.GroupService;
import org.jasig.portal.services.LogService;
import org.jasig.portal.utils.ResourceLoader;
import org.jasig.portal.utils.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This is a Channel Publisher tool to install uPortal channels from outside of the portal.
 * Currently configured to be executed via Jakarta Ant.
 *
 * Sample of command line arguments:
 * ant publish -Dchannel=all  (this will publish all channels that have a corresponding xml file)
 * ant publish -Dchannel=webmail.xml  (this will publish the specified channels)
 *
 * @author Freddy Lopez, flopez@interactivebusiness.com
 * @author Ken Weiner, kweiner@interactivebusiness.com
 * @version $Revision$
 */
public class ChannelPublisher {
  private static final String FRAMEWORK_OWNER = "UP_FRAMEWORK";
  private static final String SUBSCRIBER_ACTIVITY = "SUBSCRIBE";
  private static final String GRANT_PERMISSION_TYPE = "GRANT";

  private static int MODE;

  private static Properties channelTypesProperties;
  private static Properties categoriesProperties;
  private static Properties groupsProperties;

  private static IPerson systemUser;
  private static DocumentBuilder domParser;
  private static IChannelRegistryStore crs;

  public static void main(String[] args) {

	/*

	Channel Publisher Tool Workflow.
	1) read all or specified channel.xml file

	ant publish -Dchannel=all or -Dchannel=webmail.xml

	2) validate each against the channel-definition.dtd file

	3) load properties files
	  - channelTypes.properties
	  - categories.properties
	  - groups.properties
      
	4) publish one channel at a time

	*/

	try {

	  // determine whether user wants to publish one or all of the channels in current directory
	  if (args[1] != null && args[1].length() > 0) {
	   // MODE = 0 for all channels in directory
	   // MODE = 1 for individual channel
	   if (args[1].equals("all"))
		 MODE = 0;
	   else
		 MODE = 1;

	  }

	  // initialize channel registry store
	  crs = ChannelRegistryStoreFactory.getChannelRegistryStoreImpl();
	  // load properties files
	  loadProps();
	  // create IPerson object for the portal's system user
	  setupSystemUser();
	  // setup DOM Parser with dtd validation
	  setupDomParser();
	  // load all existing channels inorder to avoid duplicates
	  //loadChannels();

	  // determine what mode we are in
	  if (MODE == 1) {
		System.out.println ("You have chosen to publish one channel.....");
		System.out.print ("Publishing channel " + args[1] + ".....");
		// lets publish one channel only
		ChannelInfo ci = getChannelInfo(args[1]);
		// once xml file has been read start inserting into database
		publishChannel(ci);
		System.out.println ("Done");
	  } else {
		// lets publish all channels in directory
		System.out.println ("You have chosen to publish all channels.....");

		// user has selected to publish all channel in the /channels directory
		// lets publish all channel one by one that is
		// create InputStream object to pass to next method
		File f = ResourceLoader.getResourceAsFile(ChannelPublisher.class, "/properties/chanpub/channel-definitions/");
		if (f.isDirectory()) {
		  File[] files = f.listFiles();
		  for (int j=0; j < files.length; j++) {
			try {
			  System.out.print("Publishing channel " + files[j].getName() + ".....");
			  // lets publish one at a time
			  ChannelInfo ci = getChannelInfo(files[j].getName());
			  // once xml file has been read start inserting into database
			  publishChannel(ci);
			  System.out.println ("Done!");              
			} catch (Exception e) {
			  System.out.println ("FAILED!");
			  LogService.log(LogService.ERROR, "Exception occurred while publishing " +files[j].getName()+" channel. "+e);
			}
		  }
		}
	  }

	} catch (Exception e) {
	   // Need to revisit this and handle the error!
	  System.out.println ("Exception occurred in main method. "+e+". Please see portal.log for details");
	  LogService.log(LogService.ERROR, "main() :: Exception occurred in main method."+e);
	  e.printStackTrace();
	}
    
	// Done!
	System.out.println("Publishing finished.");
	System.exit(0);
  }

  private static InputStream getFileAsInputStream (File f) throws Exception {
	return new FileInputStream (f);
  }


  private static void setupSystemUser() {
	systemUser = new PersonImpl();
	systemUser.setID(0);
  }

  private static void loadProps() throws Exception {
	// Load channelTypes.properties
	channelTypesProperties = ResourceLoader.getResourceAsProperties(ChannelPublisher.class, "/properties/chanpub/channelTypes.properties");
	LogService.log(LogService.INFO, "loadProps() :: channelTypes.properties file loaded. ");

	// Load categories.properties
	categoriesProperties = ResourceLoader.getResourceAsProperties(ChannelPublisher.class, "/properties/chanpub/categories.properties");
	LogService.log(LogService.INFO, "loadProps() :: categories.properties file loaded. ");

	// Load groups.properties
	groupsProperties = ResourceLoader.getResourceAsProperties(ChannelPublisher.class, "/properties/chanpub/groups.properties");
	LogService.log(LogService.INFO, "loadProps() :: groups.properties file loaded. ");
  }

  private static void publishChannel(ChannelInfo ci) throws Exception {

	try {
	  crs.saveChannelDefinition(ci.chanDef);

	  // Permission for everyone to subscribe to channel
	  AuthorizationService authService = AuthorizationService.instance();
	  String target = "CHAN_ID." + ci.chanDef.getId();
	  IUpdatingPermissionManager upm = authService.newUpdatingPermissionManager(FRAMEWORK_OWNER);

	  // set up groups and permissions for this channel
	  // must loop because multiple groups can be declared
	  IPermission[] newPermissions = new IPermission[ci.groups.length];
	  for (int j=0; j < ci.groups.length; j++) {
		IAuthorizationPrincipal authPrincipal = authService.newPrincipal(ci.groups[j]);
		newPermissions[j] = upm.newPermission(authPrincipal);
		newPermissions[j].setType(GRANT_PERMISSION_TYPE);
		newPermissions[j].setActivity(SUBSCRIBER_ACTIVITY);
		newPermissions[j].setTarget(target);
	  }
	  // save to store
	  IPermission[] oldPermissions = upm.getPermissions(SUBSCRIBER_ACTIVITY, target);
	  upm.removePermissions(oldPermissions);
	  upm.addPermissions(newPermissions);

	  // Categories
	  // First, remove channel from its categories
	  ChannelCategory[] categories = crs.getParentCategories(ci.chanDef);
	  for (int i = 0; i < categories.length; i++) {
		crs.removeChannelFromCategory(ci.chanDef, categories[i]);
	  }
	  // Now add channel to assigned categories
	  for (int k=0; k < ci.categories.length; k++) {
		crs.addChannelToCategory(ci.chanDef, ci.categories[k]);
	  }

	  // need to approve channel
	  crs.approveChannelDefinition(ci.chanDef, systemUser, new Date());

	} catch (Exception e) {
	  LogService.log(LogService.ERROR, "publishChannel() :: Exception while attempting to publish channel to database. Channel name = "+ci.chanDef.getName());
	  throw e;
	}

  }

  private static void setupDomParser () throws Exception {
	try {
	  DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	  dbf.setValidating(true);
	  domParser = dbf.newDocumentBuilder();
	} catch (Exception e) {
	  LogService.log(LogService.ERROR, "setupDomParser() :: creating Dom Parser. "+e);
	  throw e;
	}
  }

  private static ChannelInfo getChannelInfo(String chanDefFile) throws Exception {
	ChannelInfo ci = new ChannelInfo();
	Document doc = null;
    
	try {
	  // Build a DOM tree out of Channel_To_Publish.xml
	  InputStream is = ResourceLoader.getResourceAsStream(ChannelPublisher.class, "/properties/chanpub/channel-definitions/"+chanDefFile);
	  doc = domParser.parse(is);
      
	  Element chanDefE = doc.getDocumentElement();
	  String fname = null;
	  for (Node n = chanDefE.getFirstChild(); n != null; n = n.getNextSibling()) {
		if (n.getNodeType() == Node.ELEMENT_NODE) {
		  if (n.getNodeName().equals("fname")) {
			fname = XML.getElementText((Element)n);
		  }
		}
	  }
      
	  // Complain if we don't find an fname
	  if (fname == null)
		throw new Exception(chanDefFile + " is missing required fname element");
      
	  // Use existing channel definition if it exists,
	  // otherwise make a new one with a new ID
	  ci.chanDef = crs.getChannelDefinition(fname);
	  if (ci.chanDef == null) {
		ci.chanDef = crs.newChannelDefinition();
	  }

	  for (Node param = chanDefE.getFirstChild(); param != null; param = param.getNextSibling()) {
		if (!(param instanceof Element))
		  continue; // whitespace (typically \n) between tags
		Element pele = (Element)param;
		String tagname = pele.getTagName();
		String value = XML.getElementText(pele);

		// each tagname corresponds to an object data field
		if (tagname.equals("title"))
		  ci.chanDef.setTitle(value);
		else if (tagname.equals("name"))
		  ci.chanDef.setName(value);
		else if (tagname.equals("fname"))
		  ci.chanDef.setFName(value);
		else if (tagname.equals("desc"))
		  ci.chanDef.setDescription(value);
		else if (tagname.equals("type")) {
		  // need to lookup corresponding channel type as declared in channelTypes.properties
		  // i.e.: Custom = -1, WebProxy = 4
		  String typeId = channelTypesProperties.getProperty(value);
		  if (typeId != null) {
			ci.chanDef.setTypeId(Integer.parseInt(typeId));
		  } else {
			throw new Exception ("Invalid entry in " + chanDefFile + " for Channel Type entry. Please fix before running Channel Publishing Tool");
		  }
		} else if (tagname.equals("class"))
		  ci.chanDef.setJavaClass(value);
		else if (tagname.equals("timeout"))
		  ci.chanDef.setTimeout(Integer.parseInt(value));
		else if (tagname.equals("hasedit"))
		  ci.chanDef.setEditable((value != null && value.equals("Y")) ? true : false);
		else if (tagname.equals("hashelp"))
		  ci.chanDef.setHasHelp((value != null && value.equals("Y")) ? true : false);
		else if (tagname.equals("hasabout"))
		  ci.chanDef.setHasAbout((value != null && value.equals("Y")) ? true : false);
		else if (tagname.equals("categories")) {
		  NodeList anodes = pele.getElementsByTagName("category");
		  if (anodes.getLength() != 0) {
			ci.categories = new ChannelCategory[anodes.getLength()];
			for (int j=0; j < anodes.getLength(); j++) {
			  Element anode = (Element)anodes.item(j);
			  String catString = XML.getElementText(anode);
			  // need to look up corresponding category id
			  // ie: Applications = local.50
			  //     Entertainment = local.51
			  String cat = categoriesProperties.getProperty(catString);
			  if (cat != null)
				ci.categories[j] = crs.getChannelCategory(cat);
			  else
				throw new Exception ("Invalid entry in " + chanDefFile + " for category entry. Please fix before running Channel Publishing Tool");
			}
		  }
		} else if (tagname.equals("groups")) {
		  NodeList anodes = pele.getElementsByTagName("group");
		  if (anodes.getLength() != 0) {
			ci.groups = new IGroupMember[anodes.getLength()];
			for (int j=0; j < anodes.getLength(); j++) {
			  Element anode = (Element) anodes.item(j);
			  String groupStr = XML.getElementText(anode);
			  // need to look up corresponding group id
			  // ie: Everyone = local.0
			  //     Developers = local.4
			  String group = groupsProperties.getProperty(groupStr);
			  if (group != null)
				ci.groups[j] = GroupService.findGroup(group);
			  else
				throw new Exception ("Invalid entry in " + chanDefFile + " for groups entry. Please fix before running Channel Publishing Tool");
			}
		  }
		} else if (tagname.equals("parameters")) {
		  NodeList anodes = pele.getElementsByTagName("parameter");
		  if (anodes.getLength() > 0) {
			for (int j=0; j < anodes.getLength(); j++) {
			  String pname = null;
			  String pvalue = null;
			  String povrd = null;
			  String pdescr = null;
			  Element anode = (Element)anodes.item(j);
			  NodeList namenodes = anode.getElementsByTagName("name");
			  if (namenodes.getLength() > 0) {
				pname = XML.getElementText((Element)namenodes.item(0));
			  }
			  NodeList valuenodes = anode.getElementsByTagName("value");
			  if (valuenodes.getLength() > 0) {
				pvalue = XML.getElementText((Element)valuenodes.item(0));
			  }
			  NodeList descnodes = anode.getElementsByTagName("description");
			  if (descnodes.getLength() > 0) {
				pdescr = XML.getElementText((Element)descnodes.item(0));
			  }
			  NodeList ovrdnodes = anode.getElementsByTagName("ovrd");
			  if (ovrdnodes.getLength() > 0) {
				povrd = XML.getElementText((Element)ovrdnodes.item(0));
			  }
			  ChannelParameter chanParam = new ChannelParameter(pname, pvalue, povrd);
			  chanParam.setDescription(pdescr);
			  ci.chanDef.addParameter(chanParam);
			}
		  }
		}
	  }
	} catch (Exception e) {
	  LogService.log(LogService.ERROR, "getChannelInfo() :: Exception reading channel definition file: " + chanDefFile);
	  LogService.log(LogService.ERROR, e);
	  throw e;
	}
	return ci;
  }  

  private static class ChannelInfo {
	ChannelDefinition chanDef;
	IGroupMember[] groups;
	ChannelCategory[] categories;
  }
}