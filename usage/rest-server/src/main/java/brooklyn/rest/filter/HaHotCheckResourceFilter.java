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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.rebind.RebindManagerImpl.RebindTracker;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.rest.domain.ApiError;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableSet;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

public class HaHotCheckResourceFilter implements ResourceFilterFactory {
    
    private static final Logger log = LoggerFactory.getLogger(HaHotCheckResourceFilter.class);
    
    private static final Set<ManagementNodeState> HOT_STATES = ImmutableSet.of(
            ManagementNodeState.MASTER, ManagementNodeState.HOT_STANDBY, ManagementNodeState.HOT_BACKUP);
    private static final long STATE_CHANGE_SETTLE_OFFSET = Duration.seconds(10).toMilliseconds();

    @Context
    private ManagementContext mgmt;

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
                log.warn("Disallowed request to standby brooklyn: "+request+"/"+am+" (caller should set '"+HaMasterCheckFilter.SKIP_CHECK_HEADER+"' to force)");
                throw new WebApplicationException(ApiError.builder()
                    .message("This request is not permitted against a standby Brooklyn server")
                    .errorCode(Response.Status.FORBIDDEN).build().asJsonResponse());
            }
            return request;
        }

        private boolean isStateLoaded() {
            return isHaHotStatusOrDisabled() && !RebindTracker.isRebinding() && !recentlySwitchedState();
        }

        // Ideally there will be a separate state to indicate that we switched state
        // but still haven't finished rebinding. There's a gap between changing the state
        // and starting rebind so add a time offset just to be sure.
        private boolean recentlySwitchedState() {
            long lastStateChange = mgmt.getHighAvailabilityManager().getLastStateChange();
            if (lastStateChange == -1) return false;
            return System.currentTimeMillis() - lastStateChange < STATE_CHANGE_SETTLE_OFFSET;
        }

        private boolean isUnsafe(ContainerRequest request) {
            boolean isOverriden = "true".equalsIgnoreCase(request.getHeaderValue(HaMasterCheckFilter.SKIP_CHECK_HEADER));
            return !isOverriden &&
                    (am.getAnnotation(HaHotStateRequired.class) != null ||
                    am.getResource().getAnnotation(HaHotStateRequired.class) != null);
        }

        private boolean isHaHotStatusOrDisabled() {
            if (!mgmt.getHighAvailabilityManager().isRunning()) return true;
            ManagementNodeState state = mgmt.getHighAvailabilityManager().getNodeState();
            return HOT_STATES.contains(state);
        }

    }

    @Override
    public List<ResourceFilter> create(AbstractMethod am) {
        return Collections.<ResourceFilter>singletonList(new MethodFilter(am, mgmt));
    }

}
