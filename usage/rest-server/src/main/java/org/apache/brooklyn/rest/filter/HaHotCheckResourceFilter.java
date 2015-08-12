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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.rest.domain.ApiError;

import brooklyn.util.text.Strings;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

/** 
 * Checks that if the method or resource class corresponding to a request
 * has a {@link HaHotStateRequired} annotation,
 * that the server is in that state (and up). 
 * Requests with {@link #SKIP_CHECK_HEADER} set as a header skip this check.
 * <p>
 * This follows a different pattern to {@link HaMasterCheckFilter} 
 * as this needs to know the method being invoked. 
 */
public class HaHotCheckResourceFilter implements ResourceFilterFactory {
    
    private static final Logger log = LoggerFactory.getLogger(HaHotCheckResourceFilter.class);
    
    private static final Set<ManagementNodeState> HOT_STATES = ImmutableSet.of(
            ManagementNodeState.MASTER, ManagementNodeState.HOT_STANDBY, ManagementNodeState.HOT_BACKUP);

    @Context
    private ManagementContext mgmt;

    public HaHotCheckResourceFilter() {}
    
    @VisibleForTesting
    public HaHotCheckResourceFilter(ManagementContext mgmt) {
        this.mgmt = mgmt;
    }
    
    private static class MethodFilter implements ResourceFilter, ContainerRequestFilter {

        private AbstractMethod am;
        private ManagementContext mgmt;

        public MethodFilter(AbstractMethod am, ManagementContext mgmt) {
            this.am = am;
            this.mgmt = mgmt;
        }

        @Override
        public ContainerRequestFilter getRequestFilter() {
            return this;
        }

        @Override
        public ContainerResponseFilter getResponseFilter() {
            return null;
        }

        private String lookForProblem(ContainerRequest request) {
            if (isSkipCheckHeaderSet(request)) 
                return null;
            
            if (!isHaHotStateRequired(request))
                return null;
            
            String problem = HaMasterCheckFilter.lookForProblemIfServerNotRunning(mgmt);
            if (Strings.isNonBlank(problem)) 
                return problem;
            
            if (!isHaHotStatus())
                return "server not in required HA hot state";
            if (isStateNotYetValid())
                return "server not yet completed loading data for required HA hot state";
            
            return null;
        }
        
        @Override
        public ContainerRequest filter(ContainerRequest request) {
            String problem = lookForProblem(request);
            if (Strings.isNonBlank(problem)) {
                log.warn("Disallowing web request as "+problem+": "+request+"/"+am+" (caller should set '"+HaMasterCheckFilter.SKIP_CHECK_HEADER+"' to force)");
                throw new WebApplicationException(ApiError.builder()
                    .message("This request is only permitted against an active hot Brooklyn server")
                    .errorCode(Response.Status.FORBIDDEN).build().asJsonResponse());
            }
            return request;
        }

        // Maybe there should be a separate state to indicate that we have switched state
        // but still haven't finished rebinding. (Previously there was a time delay and an
        // isRebinding check, but introducing RebindManager#isAwaitingInitialRebind() seems cleaner.)
        private boolean isStateNotYetValid() {
            return mgmt.getRebindManager().isAwaitingInitialRebind();
        }

        private boolean isHaHotStateRequired(ContainerRequest request) {
            return (am.getAnnotation(HaHotStateRequired.class) != null ||
                    am.getResource().getAnnotation(HaHotStateRequired.class) != null);
        }

        private boolean isSkipCheckHeaderSet(ContainerRequest request) {
            return "true".equalsIgnoreCase(request.getHeaderValue(HaMasterCheckFilter.SKIP_CHECK_HEADER));
        }

        private boolean isHaHotStatus() {
            ManagementNodeState state = mgmt.getHighAvailabilityManager().getNodeState();
            return HOT_STATES.contains(state);
        }

    }

    @Override
    public List<ResourceFilter> create(AbstractMethod am) {
        return Collections.<ResourceFilter>singletonList(new MethodFilter(am, mgmt));
    }

}
