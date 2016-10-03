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
package org.apache.brooklyn.rest.api;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.rest.domain.HighAvailabilitySummary;
import org.apache.brooklyn.rest.domain.VersionSummary;

import com.google.common.annotations.Beta;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Path("/v1/server")
@Api("Server")
@Produces(MediaType.APPLICATION_JSON)
@Beta
public interface ServerApi {

    public final String MIME_TYPE_ZIP = "application/zip";
    // TODO support TGZ, and check mime type
    public final String MIME_TYPE_TGZ = "application/gzip";

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
    @ApiOperation(value = "Return version identifier information for this Brooklyn instance",
            response = String.class,
            responseContainer = "List")
    public VersionSummary getVersion();

    @GET
    @Path("/up")
    @ApiOperation(value = "Returns whether this server is up - fully started, and not stopping, though it may have errors")
    public boolean isUp();

    @GET
    @Path("/shuttingDown")
    @ApiOperation(value = "Returns whether this server is shutting down")
    public boolean isShuttingDown();

    @GET
    @Path("/healthy")
    @ApiOperation(value = "Returns whether this node is healthy - fully started, not stopping, and no errors")
    public boolean isHealthy();

    @Deprecated /** @deprecated since 0.7.0 use /ha/node (which returns correct JSON) */
    @GET
    @Path("/status")
    @ApiOperation(value = "Returns the status of this Brooklyn instance [DEPRECATED; see ../ha/state]",
            response = String.class,
            responseContainer = "List")
    public String getStatus();

    @GET
    @Path("/up/extended")
    @ApiOperation(value = "Returns extended server-up information, a map including up (/up), shuttingDown (/shuttingDown), healthy (/healthy), and ha (/ha/states) (qv)")
    public Map<String,Object> getUpExtended();

    @GET
    @Path("/config/{configKey}")
    @ApiOperation(value = "Get the value of the specified config key from brooklyn properties")
    @ApiResponses(value = {
            // TODO: This should probably return a 404 if the key is not present, and should return a predictable
            // value if the value is not set. Behaviour should be consistent with EntityConfigApi.get()
            @ApiResponse(code = 204, message = "Could not find config key")
    })
    public String getConfig(
            @ApiParam(value = "Config key ID", required = true)
            @PathParam("configKey") String configKey);

    @Deprecated /** @deprecated since 0.7.0 use /ha/states */
    @GET
    @Path("/highAvailability")
    @ApiOperation(value = "Returns the status of all Brooklyn instances in the management plane [DEPRECATED; see ../ha/states]",
            response = org.apache.brooklyn.rest.domain.HighAvailabilitySummary.class)
    public HighAvailabilitySummary getHighAvailability();

    @GET
    @Path("/ha/state")
    @ApiOperation(value = "Returns the HA state of this management node")
    public ManagementNodeState getHighAvailabilityNodeState();

    @GET
    @Path("/ha/metrics")
    @ApiOperation(value = "Returns a collection of HA metrics")
    public Map<String,Object> getHighAvailabilityMetrics();

    @POST
    @Path("/ha/state")
    @ApiOperation(value = "Changes the HA state of this management node")
    public ManagementNodeState setHighAvailabilityNodeState(
            @ApiParam(name = "mode", value = "The state to change to")
            @FormParam("mode") HighAvailabilityMode mode);

    @GET
    @Path("/ha/states")
    @ApiOperation(value = "Returns the HA states and detail for all nodes in this management plane",
            response = org.apache.brooklyn.rest.domain.HighAvailabilitySummary.class)
    public HighAvailabilitySummary getHighAvailabilityPlaneStates();

    @POST
    @Path("/ha/states/clear")
    @ApiOperation(value = "Clears HA node information for non-master nodes; active nodes will repopulate and other records will be erased")
    public Response clearHighAvailabilityPlaneStates();

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
    @Produces(MIME_TYPE_ZIP)
    @Path("/ha/persist/export")
    @ApiOperation(value = "Retrieves the persistence store data, as an archive")
    public Response exportPersistenceData(
        @ApiParam(name = "origin", value = "Whether to take from LOCAL or REMOTE state; default to AUTO detect, "
                + "using LOCAL as master and REMOTE for other notes")
        @QueryParam("origin") @DefaultValue("AUTO") String origin);

    // TODO would be nice to allow setting, as a means to recover / control more easily than messing with persistent stores
//    @POST
//    @Consumes({MediaType.APPLICATION_FORM_URLENCODED})
//    @Path("/ha/persist/import")
//    @ApiOperation(value = "Causes the supplied persistence data (tgz) to be imported and added "
//        + "(fails if the node is not master), optionally removing any items not referenced")
//    public Response importPersistenceData(
//          // question: do we want the MementoCopyMode, cf export above?
//        @ApiParam(name = "clearOthers", value = "Whether to clear all existing items before adding these", required = false, defaultValue = "false")
//        @FormParam("clearOthers") Boolean clearOthers,
//        @ApiParam(name = "data",
//        value = "TGZ contents of a persistent directory to be imported", required = true)
//    @Valid String dataTgz);

    // TODO /ha/persist/backup set of endpoints, to list and retrieve specific backups

    @GET
    @Path("/user")
    @ApiOperation(value = "Return user information for this Brooklyn instance",
            response = String.class,
            responseContainer = "List")
    public String getUser();

}
