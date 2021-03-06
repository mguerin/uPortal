/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.portlet.container;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Locale;

import javax.portlet.CacheControl;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.Validate;
import org.apache.pluto.container.PortletContainer;
import org.apache.pluto.container.PortletMimeResponseContext;
import org.apache.pluto.container.PortletURLProvider;
import org.apache.pluto.container.PortletURLProvider.TYPE;
import org.apache.pluto.container.util.PrintWriterServletOutputStream;
import org.jasig.portal.portlet.container.cache.IPortletCacheControlService;
import org.jasig.portal.portlet.container.properties.IRequestPropertiesManager;
import org.jasig.portal.portlet.container.services.IPortletCookieService;
import org.jasig.portal.portlet.om.IPortletWindow;
import org.jasig.portal.portlet.om.IPortletWindowId;
import org.jasig.portal.portlet.rendering.IPortletRenderer;
import org.jasig.portal.portlet.url.PortletURLProviderImpl;
import org.jasig.portal.url.IPortalUrlBuilder;
import org.jasig.portal.url.IPortalUrlProvider;
import org.jasig.portal.url.IPortletUrlBuilder;
import org.jasig.portal.url.UrlType;

/**
 * @author Eric Dalquist
 * @version $Revision$
 */
public abstract class PortletMimeResponseContextImpl extends PortletResponseContextImpl implements PortletMimeResponseContext {
    
    private final IPortalUrlProvider portalUrlProvider;
    private final PrintWriter portletWriter;
    private OutputStream writerOutputStream;
    private IPortletCacheControlService portletCacheControlService;
    
    public PortletMimeResponseContextImpl(PortletContainer portletContainer, IPortletWindow portletWindow,
            HttpServletRequest containerRequest, HttpServletResponse containerResponse,
            IRequestPropertiesManager requestPropertiesManager, IPortalUrlProvider portalUrlProvider,
            IPortletCookieService portletCookieService, IPortletCacheControlService portletCacheControlService) {

        super(portletContainer, portletWindow, containerRequest, containerResponse, 
                requestPropertiesManager, portletCookieService);
        
        Validate.notNull(portalUrlProvider, "portalUrlProvider can not be null1");
        
        this.portletWriter = (PrintWriter)containerRequest.getAttribute(IPortletRenderer.ATTRIBUTE__PORTLET_PRINT_WRITER);
        this.portalUrlProvider = portalUrlProvider;
        this.portletCacheControlService = portletCacheControlService;
    }

    /**
	 * @return the portletCacheControlService
	 */
	public IPortletCacheControlService getPortletCacheControlService() {
		return portletCacheControlService;
	}

	/* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#flushBuffer()
     */
    @Override
    public void flushBuffer() throws IOException {
        this.checkContextStatus();
        
        if (this.portletWriter != null) {
            this.portletWriter.flush();
        }
        else {
            this.servletResponse.flushBuffer();
        }
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#getBufferSize()
     */
    @Override
    public int getBufferSize() {
        if (this.portletWriter != null) {
            return 0;
        }

        return this.servletResponse.getBufferSize();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#getCharacterEncoding()
     */
    @Override
    public String getCharacterEncoding() {
        this.checkContextStatus();
        
        return this.servletResponse.getCharacterEncoding();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#getContentType()
     */
    @Override
    public String getContentType() {
        this.checkContextStatus();
        return this.servletResponse.getContentType();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#getLocale()
     */
    @Override
    public Locale getLocale() {
        this.checkContextStatus();
        return this.servletResponse.getLocale();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#getOutputStream()
     */
    @Override
    public OutputStream getOutputStream() throws IOException, IllegalStateException {
        this.checkContextStatus();
        if (this.portletWriter != null) {
            if (this.writerOutputStream == null) {
                this.writerOutputStream = new PrintWriterServletOutputStream(this.portletWriter, getCharacterEncoding());
            }
            
            return writerOutputStream;
        }
        
        return this.servletResponse.getOutputStream();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#getPortletURLProvider(org.apache.pluto.container.PortletURLProvider.TYPE)
     */
    @Override
    public PortletURLProvider getPortletURLProvider(TYPE type) {
        final IPortletWindowId portletWindowId = this.portletWindow.getPortletWindowId();
        final UrlType urlType = UrlType.fromPortletUrlType(type);
        final IPortalUrlBuilder portalUrlBuilder = this.portalUrlProvider.getPortalUrlBuilderByPortletWindow(containerRequest, portletWindowId, urlType);
        final IPortletUrlBuilder portletUrlBuilder = portalUrlBuilder.getPortletUrlBuilder(portletWindowId);
        return new PortletURLProviderImpl(portletUrlBuilder);
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#getWriter()
     */
    @Override
    public PrintWriter getWriter() throws IOException, IllegalStateException {
        this.checkContextStatus();
        if (this.portletWriter != null) {
            return this.portletWriter;
        }

        return this.servletResponse.getWriter();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#isCommitted()
     */
    @Override
    public boolean isCommitted() {
        if (this.portletWriter != null) {
            return true;
        }
        
        return this.servletResponse.isCommitted();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#reset()
     */
    @Override
    public void reset() {
        this.checkContextStatus();
        if (this.portletWriter != null) {
            throw new IllegalStateException("Response is already committed");
        }
        this.servletResponse.reset();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#resetBuffer()
     */
    @Override
    public void resetBuffer() {
        this.checkContextStatus();
        if (this.portletWriter != null) {
            throw new IllegalStateException("Response is already committed");
        }
        this.servletResponse.resetBuffer();
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#setBufferSize(int)
     */
    @Override
    public void setBufferSize(int size) {
        this.checkContextStatus();
        if (this.portletWriter == null) {
            this.servletResponse.setBufferSize(size);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.pluto.container.PortletMimeResponseContext#setContentType(java.lang.String)
     */
    @Override
    public void setContentType(String contentType) {
        this.checkContextStatus();
        if (this.portletWriter == null) {
            this.servletResponse.setContentType(contentType);
        }
        //TODO what should the portal do about the portlet contentType?
    }

}
