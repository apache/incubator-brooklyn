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
import java.util.Enumeration;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.entitlement.WebEntitlementContext;
import brooklyn.rest.security.provider.DelegatingSecurityProvider;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.sun.jersey.core.util.Base64;

/**
 * Provides basic HTTP authentication.
 */
public class BrooklynPropertiesSecurityFilter implements Filter {

    /**
     * The session attribute set for authenticated users; for reference
     * (but should not be relied up to confirm authentication, as
     * the providers may impose additional criteria such as timeouts,
     * or a null user (no login) may be permitted)
     */
    public static final String AUTHENTICATED_USER_SESSION_ATTRIBUTE = "brooklyn.user";

    /**
     * The session attribute set to indicate the remote address of the HTTP request.
     * Corresponds to {@link javax.servlet.http.HttpServletRequest#getRemoteAddr()}.
     */
    public static final String REMOTE_ADDRESS_SESSION_ATTRIBUTE = "request.remoteAddress";

    private static final Logger log = LoggerFactory.getLogger(BrooklynPropertiesSecurityFilter.class);
    
    protected DelegatingSecurityProvider provider;
    
    private static ThreadLocal<String> originalRequest = new ThreadLocal<String>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (provider == null) {
            log.warn("No security provider available: disallowing web access to brooklyn");
            httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }

        if (handleLogout(httpRequest) || !authenticate(httpRequest)) {
            httpResponse.setHeader("WWW-Authenticate", "Basic realm=\"brooklyn\"");
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        WebEntitlementContext entitlementContext = null;
        String uri = httpRequest.getRequestURI();
        String uid = Identifiers.makeRandomId(6);
        String user = Strings.toString(httpRequest.getSession().getAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE));
        try {
            entitlementContext = new WebEntitlementContext(user, httpRequest.getRemoteAddr(), uri, uid);
            if (originalRequest.get() == null) {
                // initial filter application
                originalRequest.set(uri);
            } else {
                // this filter is being applied *again*, probably due to forwarding (e.g. from '/' to '/index.html')
                log.debug("REST {} being forwarded from {} to {}", new Object[]{uid, originalRequest.get(), uri});
                // clear the entitlement context before setting to avoid warnings
                Entitlements.clearEntitlementContext();
            }
            Entitlements.setEntitlementContext(entitlementContext);

            log.debug("REST {} starting processing request {} with {}", new Object[]{uid, uri, entitlementContext});
            chain.doFilter(request, response);

            // This logging must NOT happen before chain.doFilter, or FormMapProvider will not work as expected.
            // Getting the parameter map consumes the request body and only resource methods using @FormParam
            // will work as expected.
            if (log.isDebugEnabled()) {
                log.debug("REST {} complete, responding {} for `request {} with {}",
                        new Object[]{uid, httpResponse.getStatus(), uri, entitlementContext});
                log.debug("  source: " + httpRequest.getRemoteAddr());
                if (!httpRequest.getParameterMap().isEmpty()) {
                    log.debug("  parameters were: {}", httpRequest.getParameterMap());
                }
                if (httpRequest.getContentLength() > 0) {
                    int len = httpRequest.getContentLength();
                    log.debug("  upload content type was {}, length={}", httpRequest.getContentType(), len);
                }
                if (log.isTraceEnabled()) {
                    Enumeration<String> headerNames = httpRequest.getHeaderNames();
                    if (headerNames.hasMoreElements()) {
                        log.trace("  headers:");
                        while (headerNames.hasMoreElements()) {
                            String headerName = headerNames.nextElement();
                            log.trace("    {}: {}", headerName, httpRequest.getHeader(headerName));
                        }
                    }
                }
            }
        } catch (Throwable e) {
            // errors are typically already caught at this point, except for serialization errors
            log.warn("REST " + uid + " failed processing request " + uri + " with " + entitlementContext + ": " + e, e);
            httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            originalRequest.remove();
            Entitlements.clearEntitlementContext();
        }

    }

    protected boolean authenticate(HttpServletRequest request) {
        HttpSession session = request.getSession();
        if (provider.isAuthenticated(session)) {
            return true;
        }
        session.setAttribute(REMOTE_ADDRESS_SESSION_ATTRIBUTE, request.getRemoteAddr());
        String user = null, pass = null;
        String authorization=request.getHeader("Authorization");
        if (authorization != null) {
            String userpass = Base64.base64Decode(authorization.substring(6));
            user = userpass.substring(0, userpass.indexOf(":"));
            pass = userpass.substring(userpass.indexOf(":") + 1);
        }
        if (provider.authenticate(session, user, pass)) {
            log.debug("REST authenticated {} for user {}", session.getId(), user);
            if (user != null) {
                session.setAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE, user);
            }
            return true;
        }
        
        return false;
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        ManagementContext mgmt = (ManagementContext) config.getServletContext().getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        provider = new DelegatingSecurityProvider(mgmt);
    }
    
    @Override
    public void destroy() {
    }

    protected boolean handleLogout(HttpServletRequest request) {
        if ("/logout".equals(request.getRequestURI()) || "/v1/logout".equals(request.getRequestURI())) {
            log.info("Web API logging {} out of session {}",
                    request.getSession().getAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE), request.getSession().getId());
            provider.logout(request.getSession());
            request.getSession().removeAttribute(AUTHENTICATED_USER_SESSION_ATTRIBUTE);
            request.getSession().removeAttribute(REMOTE_ADDRESS_SESSION_ATTRIBUTE);
            request.getSession().invalidate();
            return true;
        }
        return false;
    }

}
