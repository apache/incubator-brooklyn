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
package org.apache.brooklyn.rest.filter;

import java.io.IOException;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServiceAttributes;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.rest.domain.ApiError;
import org.apache.brooklyn.rest.util.WebResourceUtils;

import brooklyn.util.text.Strings;

import com.google.common.collect.Sets;

/**
 * Checks that for requests that want HA master state, the server is up and in that state.
 * <p>
 * Post POSTs and PUTs are assumed to need master state, with the exception of shutdown.
 * Requests with {@link #SKIP_CHECK_HEADER} set as a header skip this check.
 */
public class HaMasterCheckFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(HaMasterCheckFilter.class);
    
    public static final String SKIP_CHECK_HEADER = "Brooklyn-Allow-Non-Master-Access";
    private static final Set<String> SAFE_STANDBY_METHODS = Sets.newHashSet("GET", "HEAD");

    protected ServletContext servletContext;
    protected ManagementContext mgmt;

    @Override
    public void init(FilterConfig config) throws ServletException {
        servletContext = config.getServletContext();
        mgmt = (ManagementContext) servletContext.getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
    }

    static String lookForProblemIfServerNotRunning(ManagementContext mgmt) {
        if (mgmt==null) return "no management context available";
        if (!mgmt.isRunning()) return "server no longer running";
        if (!mgmt.isStartupComplete()) return "server not in required startup-completed state";
        return null;
    }
    
    private String lookForProblem(ServletRequest request) {
        if (isSkipCheckHeaderSet(request)) 
            return null;
        
        if (!isMasterRequiredForRequest(request))
            return null;
        
        String problem = lookForProblemIfServerNotRunning(mgmt);
        if (Strings.isNonBlank(problem)) 
            return problem;
        
        if (!isMaster()) 
            return "server not in required HA master state";
        
        return null;
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String problem = lookForProblem(request);
        if (problem!=null) {
            log.warn("Disallowing web request as "+problem+": "+request.getParameterMap()+" (caller should set '"+SKIP_CHECK_HEADER+"' to force)");
            WebResourceUtils.applyJsonResponse(servletContext, ApiError.builder()
                .message("This request is only permitted against an active master Brooklyn server")
                .errorCode(Response.Status.FORBIDDEN).build().asJsonResponse(), (HttpServletResponse)response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }

    private boolean isMaster() {
        return ManagementNodeState.MASTER.equals(mgmt.getHighAvailabilityManager().getNodeState());
    }

    private boolean isMasterRequiredForRequest(ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            
            String method = httpRequest.getMethod().toUpperCase();
            // gets usually okay
            if (SAFE_STANDBY_METHODS.contains(method)) return false;
            
            // explicitly allow calls to shutdown
            // (if stopAllApps is specified, the method itself will fail; but we do not want to consume parameters here, that breaks things!)
            // TODO combine with HaHotCheckResourceFilter and use an annotation HaAnyStateAllowed or similar
            if ("/v1/server/shutdown".equals(httpRequest.getRequestURI())) return false;
            
            // master required for everything else
            return true;
        }
        // previously non-HttpServletRequests were allowed but I don't think they should be
        return true;
    }

    private boolean isSkipCheckHeaderSet(ServletRequest httpRequest) {
        if (httpRequest instanceof HttpServletRequest)
            return "true".equalsIgnoreCase(((HttpServletRequest)httpRequest).getHeader(SKIP_CHECK_HEADER));
        return false;
    }

}
