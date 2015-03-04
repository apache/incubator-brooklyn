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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.rebind.RebindManagerImpl.RebindTracker;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.ManagementNodeState;

import com.google.common.collect.ImmutableSet;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

public class HaStateCheckResourceFilter implements ResourceFilterFactory {
    private static final Set<ManagementNodeState> HOT_STATES = ImmutableSet.of(
            ManagementNodeState.MASTER, ManagementNodeState.HOT_STANDBY, ManagementNodeState.HOT_BACKUP);

    @Context
    private ServletContext servletContext;

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

        @Override
        public ContainerRequest filter(ContainerRequest request) {
            if (!isStateLoaded() && isUnsafe(request)) {
                Response response = Response.status(Response.Status.FORBIDDEN)
                        .type(MediaType.APPLICATION_JSON)
                        .entity("{\"error\":403,\"message\":\"Requests should be made to the master Brooklyn server\"}")
                        .build();
                throw new WebApplicationException(response);
            }
            return request;
        }

        private boolean isStateLoaded() {
            return isHaHotStatus() && !RebindTracker.isRebinding();
        }

        private boolean isUnsafe(ContainerRequest request) {
            boolean isOverriden = "true".equalsIgnoreCase(request.getHeaderValue(HaMasterCheckFilter.SKIP_CHECK_HEADER));
            return !isOverriden &&
                    (am.getAnnotation(HaHotStateRequired.class) != null ||
                    am.getResource().getAnnotation(HaHotStateRequired.class) != null);
        }

        private boolean isHaHotStatus() {
            ManagementNodeState state = mgmt.getHighAvailabilityManager().getNodeState();
            return HOT_STATES.contains(state);
        }

    }

    @Override
    public List<ResourceFilter> create(AbstractMethod am) {
        ManagementContext mgmt = (ManagementContext)servletContext.getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
        return Collections.<ResourceFilter>singletonList(new MethodFilter(am, mgmt));
    }

}
