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
package brooklyn.rest.api;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.HighAvailabilitySummary;
import brooklyn.rest.domain.VersionSummary;

import com.google.common.annotations.Beta;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/server")
@Apidoc("Server")
@Produces(MediaType.APPLICATION_JSON)
@Beta
public interface ServerApi {

    @POST
    @Path("/properties/reload")
    @ApiOperation(value = "Reload brooklyn.properties")
    public void reloadBrooklynProperties();

    @POST
    @Path("/shutdown")
    @ApiOperation(value = "Terminate this Brooklyn server instance")
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED})
    public void shutdown(
        @ApiParam(name = "stopAppsFirst", value = "Whether to stop running applications before shutting down")
        @FormParam("stopAppsFirst") @DefaultValue("false") boolean stopAppsFirst,
        @ApiParam(name = "forceShutdownOnError", value ="Force shutdown if apps fail to stop or timeout")
        @FormParam("forceShutdownOnError") @DefaultValue("false") boolean forceShutdownOnError,
        @ApiParam(name = "shutdownTimeout", value = "A maximum delay to wait for apps to gracefully stop before giving up or forcibly exiting, 0 to wait infinitely")
        @FormParam("shutdownTimeout") @DefaultValue("20s") String shutdownTimeout,
        @ApiParam(name = "requestTimeout", value = "Maximum time to block the request for the shutdown to finish, 0 to wait infinitely")
        @FormParam("requestTimeout") @DefaultValue("20s") String requestTimeout,
        @ApiParam(name = "delayForHttpReturn", value = "The delay before exiting the process, to permit the REST response to be returned")
        @FormParam("delayForHttpReturn") @DefaultValue("5s") String delayForHttpReturn,
        @ApiParam(name = "delayMillis", value = "Deprecated, analogous to delayForHttpReturn")
        @FormParam("delayMillis") Long delayMillis);

    @GET
    @Path("/version")
    @ApiOperation(value = "Return version identifier information for this Brooklyn instance", responseClass = "String", multiValueResponse = false)
    public VersionSummary getVersion();

    @Deprecated /** @deprecated since 0.7.0 use /ha/node (which returns correct JSON) */
    @GET
    @Path("/status")
    @ApiOperation(value = "Returns the status of this Brooklyn instance",
        responseClass = "String",
        multiValueResponse = false)
    public String getStatus();

    @Deprecated /** @deprecated since 0.7.0 use /ha/states */
    @GET
    @Path("/highAvailability")
    @ApiOperation(value = "Returns the status of all Brooklyn instances in the management plane",
        responseClass = "brooklyn.rest.domain.HighAvailabilitySummary")
    public HighAvailabilitySummary getHighAvailability();
    
    @GET
    @Path("/ha/state")
    @ApiOperation(value = "Returns the HA state of this management node")
    public ManagementNodeState getHighAvailabilityNodeState();
    
    @POST
    @Path("/ha/state")
    @ApiOperation(value = "Changes the HA state of this management node")
    public ManagementNodeState setHighAvailabilityNodeState(
            @ApiParam(name = "state", value = "The state to change to")
            @FormParam("mode") HighAvailabilityMode mode);

    @GET
    @Path("/ha/states")
    @ApiOperation(value = "Returns the HA states and detail for all nodes in this management plane",
        responseClass = "brooklyn.rest.domain.HighAvailabilitySummary")
    public HighAvailabilitySummary getHighAvailabilityPlaneStates();

    @GET
    @Path("/ha/priority")
    @ApiOperation(value = "Returns the HA node priority for MASTER failover")
    public long getHighAvailabitlityPriority();
    
    @POST
    @Path("/ha/priority")
    @ApiOperation(value = "Sets the HA node priority for MASTER failover")
    public long setHighAvailabilityPriority(
            @ApiParam(name = "priority", value = "The priority to be set")
            @FormParam("priority") long priority);

    @GET
    @Path("/user")
    @ApiOperation(value = "Return user information for this Brooklyn instance", responseClass = "String", multiValueResponse = false)
    public String getUser(); 

}
