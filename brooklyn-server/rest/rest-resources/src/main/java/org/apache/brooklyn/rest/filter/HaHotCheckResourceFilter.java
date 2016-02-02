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
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Set;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.rest.domain.ApiError;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

/** 
 * Checks that if the method or resource class corresponding to a request
 * has a {@link HaHotStateRequired} annotation,
 * that the server is in that state (and up). 
 * Requests with {@link #SKIP_CHECK_HEADER} set as a header skip this check.
 * <p>
 * This follows a different pattern to {@link HaMasterCheckFilter} 
 * as this needs to know the method being invoked. 
 */
@Provider
public class HaHotCheckResourceFilter implements ContainerRequestFilter {
    public static final String SKIP_CHECK_HEADER = "Brooklyn-Allow-Non-Master-Access";
    
    private static final Logger log = LoggerFactory.getLogger(HaHotCheckResourceFilter.class);
    
    private static final Set<ManagementNodeState> HOT_STATES = ImmutableSet.of(
            ManagementNodeState.MASTER, ManagementNodeState.HOT_STANDBY, ManagementNodeState.HOT_BACKUP);

    // Not quite standards compliant. Should instead be:
    // @Context Providers providers
    // ....
    // ContextResolver<ManagementContext> resolver = providers.getContextResolver(ManagementContext.class, MediaType.WILDCARD_TYPE)
    // ManagementContext engine = resolver.get(ManagementContext.class);
    @Context
    private ContextResolver<ManagementContext> mgmt;

    @Context
    private ResourceInfo resourceInfo;
    
    public HaHotCheckResourceFilter() {
    }

    @VisibleForTesting
    public HaHotCheckResourceFilter(ContextResolver<ManagementContext> mgmt) {
        this.mgmt = mgmt;
    }

    private ManagementContext mgmt() {
        return mgmt.getContext(ManagementContext.class);
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String problem = lookForProblem(requestContext);
        if (Strings.isNonBlank(problem)) {
            log.warn("Disallowing web request as "+problem+": "+requestContext.getUriInfo()+"/"+resourceInfo.getResourceMethod()+" (caller should set '"+SKIP_CHECK_HEADER+"' to force)");
            requestContext.abortWith(ApiError.builder()
                .message("This request is only permitted against an active hot Brooklyn server")
                .errorCode(Response.Status.FORBIDDEN).build().asJsonResponse());
        }
    }

    private String lookForProblem(ContainerRequestContext requestContext) {
        if (isSkipCheckHeaderSet(requestContext)) 
            return null;
        
        if (!isHaHotStateRequired())
            return null;
        
        String problem = lookForProblemIfServerNotRunning(mgmt());
        if (Strings.isNonBlank(problem)) 
            return problem;
        
        if (!isHaHotStatus())
            return "server not in required HA hot state";
        if (isStateNotYetValid())
            return "server not yet completed loading data for required HA hot state";
        
        return null;
    }
    
    public static String lookForProblemIfServerNotRunning(ManagementContext mgmt) {
        if (mgmt==null) return "no management context available";
        if (!mgmt.isRunning()) return "server no longer running";
        if (!mgmt.isStartupComplete()) return "server not in required startup-completed state";
        return null;
    }
    
    // Maybe there should be a separate state to indicate that we have switched state
    // but still haven't finished rebinding. (Previously there was a time delay and an
    // isRebinding check, but introducing RebindManager#isAwaitingInitialRebind() seems cleaner.)
    private boolean isStateNotYetValid() {
        return mgmt().getRebindManager().isAwaitingInitialRebind();
    }

    private boolean isHaHotStateRequired() {
        // TODO support super annotations
        Method m = resourceInfo.getResourceMethod();
        return getAnnotation(m, HaHotStateRequired.class) != null;
    }
    
    private <T extends Annotation> T getAnnotation(Method m, Class<T> annotation) {
        T am = m.getAnnotation(annotation);
        if (am != null) {
            return am;
        }
        Class<?> superClass = m.getDeclaringClass();
        T ac = superClass.getAnnotation(annotation);
        if (ac != null) {
            return ac;
        }
        // TODO could look in super classes but not needed now, we are in control of where to put the annotation
        return null;
    }

    private boolean isSkipCheckHeaderSet(ContainerRequestContext requestContext) {
        return "true".equalsIgnoreCase(requestContext.getHeaderString(SKIP_CHECK_HEADER));
    }

    private boolean isHaHotStatus() {
        ManagementNodeState state = mgmt().getHighAvailabilityManager().getNodeState();
        return HOT_STATES.contains(state);
    }


}
