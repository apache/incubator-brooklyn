/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.rest.security;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.entitlement.WebEntitlementContext;
import brooklyn.rest.security.provider.DelegatingSecurityProvider;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.sun.jersey.core.util.Base64;

/**
 * Provides basic HTTP authentication.
 */
public class BrooklynPropertiesSecurityFilter implements Filter {
    
    /** session attribute set for authenticated users; for reference
     * (but should not be relied up to confirm authentication, as
     * the providers may impose additional criteria such as timeouts,
     * or a null user (no login) may be permitted) */
    public static final String AUTHENTICATED_USER_SESSION_ATTRIBUTE = "brooklyn.user";
    
    private static final Logger log = LoggerFactory.getLogger(BrooklynPropertiesSecurityFilter.class);
    
    protected ManagementContext mgmt;
    protected DelegatingSecurityProvider provider;
    
    private static ThreadLocal<String> originalRequest = new ThreadLocal<String>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (provider==null) {
            log.warn("No security provider available: disallowing web access to brooklyn");
            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        
        if (authenticate((HttpServletRequest)request)) {
            String user = Strings.toString( ((HttpServletRequest)request).getSession().getAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE) );
            if (handleLogout((HttpServletRequest)request)) {
                log.debug("REST logging out "+user+" of session "+((HttpServletRequest)request).getSession());
                // do nothing here, fall through to below
            } else {
                WebEntitlementContext entitlementContext = null;
                String uri = ((HttpServletRequest)request).getRequestURI();
                try {
                    String uid = Integer.toHexString(hashCode());
                    entitlementContext = new WebEntitlementContext(user, ((HttpServletRequest)request).getRemoteAddr(), uri, uid);
                    if (originalRequest.get()==null) {
                        // initial filter application
                        originalRequest.set(uri);
                    } else {
                        // this filter is being applied *again*, probably due to forwarding (e.g. from '/' to '/index.html')
                        log.debug("REST request {} being forwarded from {} to {}", new Object[] { uid, originalRequest.get(), uri });
                        // clear the entitlement context before setting to avoid warnings
                        Entitlements.clearEntitlementContext();
                    }
                    Entitlements.setEntitlementContext(entitlementContext);
                    
                    log.debug("REST starting processing request {} with {}", uri, entitlementContext);
                    if (!((HttpServletRequest)request).getParameterMap().isEmpty()) {
                        log.debug("  REST req {} parameters: {}", uid, ((HttpServletRequest)request).getParameterMap());
                    }
                    if (((HttpServletRequest)request).getContentLength()>0) {
                        int len = ((HttpServletRequest)request).getContentLength();
                        log.debug("  REST req {} upload content type {}", uid, ((HttpServletRequest)request).getContentType()+" length "+len
                            // would be nice to do this, but the stream can only be read once;
                            // TODO figure out how we could peek at it
//                            +(len>0 && len<4096 ? ""+Streams.readFullyString(((HttpServletRequest)request).getInputStream()) : "") 
                            );
                    }
                    
                    chain.doFilter(request, response);
                    
                    log.debug("REST completed, code {}, processing request {} with {}", 
                        new Object[] { ((HttpServletResponse)response).getStatus(), uri, entitlementContext } );
                    return;
                    
                } catch (Throwable e) {
                    // NB errors are typically already caught at this point
                    if (log.isDebugEnabled()) {
                        log.debug("REST failed processing request "+uri+" with "+entitlementContext+": "+e, e);
                    }
                    
                    throw Exceptions.propagate(e);
                    
                } finally {
                    originalRequest.remove();
                    Entitlements.clearEntitlementContext();
                }
            }
        }
        
        ((HttpServletResponse) response).setHeader("WWW-Authenticate","Basic realm=\"brooklyn\"");
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    protected boolean authenticate(HttpServletRequest request) {
        if (provider.isAuthenticated( request.getSession() ))
            return true;
        
        String user = null, pass = null;
        String authorization=request.getHeader("Authorization");
        if (authorization!=null) {
            String userpass=Base64.base64Decode(authorization.substring(6));
            user=userpass.substring(0,userpass.indexOf(":"));
            pass=userpass.substring(userpass.indexOf(":")+1);
        }
        
        if (provider.authenticate(request.getSession(), user, pass)) {
            log.debug("Web API authenticated "+request.getSession()+" for user "+user);
            if (user!=null) {
                request.getSession().setAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE, user);
            }
            return true;
        }
        
        return false;
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        mgmt = (ManagementContext) config.getServletContext().getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        provider = new DelegatingSecurityProvider(mgmt);
    }
    
    @Override
    public void destroy() {
    }

    protected boolean handleLogout(HttpServletRequest request) {
        if ("/logout".equals(request.getRequestURI()) || "/v1/logout".equals(request.getRequestURI())) {
            log.info("Web API logging out "+request.getSession()+" for user "+
                    request.getSession().getAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE));
            provider.logout(request.getSession());
            request.getSession().removeAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE);
            request.getSession().invalidate();
            return true;
        }
        return false;
    }

}
