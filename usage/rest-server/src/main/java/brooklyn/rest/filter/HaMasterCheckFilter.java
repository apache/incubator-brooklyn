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
package brooklyn.rest.filter;

import java.io.IOException;
import java.util.Set;

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
import brooklyn.management.ha.ManagementNodeState;

import com.google.common.collect.Sets;

/**
 * Checks that the request is appropriate given the high availability status of the server.
 *
 * @see brooklyn.management.ha.ManagementNodeState
 */
public class HaMasterCheckFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(HaMasterCheckFilter.class);
    
    public static final String SKIP_CHECK_HEADER = "Brooklyn-Allow-Non-Master-Access";
    private static final Set<String> SAFE_STANDBY_METHODS = Sets.newHashSet("GET", "HEAD");

    protected ManagementContext mgmt;

    @Override
    public void init(FilterConfig config) throws ServletException {
        mgmt = (ManagementContext) config.getServletContext().getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!isMaster() && !isRequestAllowedForNonMaster(request)) {
            log.warn("Disallowed request to non-HA master: "+request+"/"+request.getParameterMap()+" (caller should set '"+SKIP_CHECK_HEADER+"' to force)");
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.getWriter().write("{\"error\":403,\"message\":\"Requests should be made to the master Brooklyn server\"}");
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

    private boolean isRequestAllowedForNonMaster(ServletRequest request) {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String checkOverridden = httpRequest.getHeader(SKIP_CHECK_HEADER);
            if ("true".equalsIgnoreCase(checkOverridden)) return true;
            
            String method = httpRequest.getMethod().toUpperCase();
            if (SAFE_STANDBY_METHODS.contains(method)) return true;
            
            // explicitly allow calls to shutdown
            // (if stopAllApps is specified, the method itself will fail; but we do not want to consume parameters here, that breaks things!)
            // TODO combine with HaHotCheckResourceFilter and use an annotation HaAnyStateAllowed or similar
            if ("/v1/server/shutdown".equals(httpRequest.getRequestURI())) return true;
            
            return false;
        }
        // previously non-HttpServletRequests were allowed but I don't think they should be
        return false;
    }
}
