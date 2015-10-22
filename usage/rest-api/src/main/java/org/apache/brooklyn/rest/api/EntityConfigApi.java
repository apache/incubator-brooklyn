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

import io.swagger.annotations.Api;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.brooklyn.rest.domain.EntityConfigSummary;

import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/config")
@Api("Entity Config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface EntityConfigApi {

    @GET
    @ApiOperation(value = "Fetch the config keys for a specific application entity",
            response = org.apache.brooklyn.rest.domain.ConfigSummary.class,
            responseContainer = "List")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Could not find application or entity")
    })
    public List<EntityConfigSummary> list(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") final String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") final String entityToken);

    // TODO support parameters  ?show=value,summary&name=xxx &format={string,json,xml}
    // (and in sensors class)
    @GET
    @Path("/current-state")
    @ApiOperation(value = "Fetch config key values in batch", notes="Returns a map of config name to value")
    public Map<String, Object> batchConfigRead(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") String entityToken,
            @ApiParam(value = "Return raw config data instead of display values", required = false)
            @QueryParam("raw") @DefaultValue("false") final Boolean raw);

    //To call this endpoint set the Accept request field e.g curl -H "Accept: application/json" ...
    @GET
    @Path("/{config}")
    @ApiOperation(value = "Fetch config value (json)", response = Object.class)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Could not find application, entity or config key")
    })
    @Produces(MediaType.APPLICATION_JSON)
    public Object get(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") String entityToken,
            @ApiParam(value = "Config key ID", required = true)
            @PathParam("config") String configKeyName,
            @ApiParam(value = "Return raw config data instead of display values", required = false)
            @QueryParam("raw") @DefaultValue("false") final Boolean raw);

    // if user requests plain value we skip some json post-processing
    @GET
    @Path("/{config}")
    @ApiOperation(value = "Fetch config value (text/plain)", response = Object.class)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Could not find application, entity or config key")
    })
    @Produces(MediaType.TEXT_PLAIN)
    public String getPlain(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") String entityToken,
            @ApiParam(value = "Config key ID", required = true)
            @PathParam("config") String configKeyName,
            @ApiParam(value = "Return raw config data instead of display values", required = false)
            @QueryParam("raw") @DefaultValue("false") final Boolean raw);

    @POST
    @ApiOperation(value = "Manually set multiple config values")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Could not find application or entity")
    })
    @SuppressWarnings("rawtypes")
    public void setFromMap(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") final String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") final String entityToken,
            @ApiParam(value = "Apply the config to all pre-existing descendants", required = false)
            @QueryParam("recurse") @DefaultValue("false") final Boolean recurse,
            @ApiParam(value = "Map of config key names to values", required = true)
            Map newValues);

    @POST
    @Path("/{config}")
    @ApiOperation(value = "Manually set a config value")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Could not find application, entity or config key")
    })
    public void set(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") final String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") final String entityToken,
            @ApiParam(value = "Config key name", required = true)
            @PathParam("config") String configName,
            @ApiParam(value = "Apply the config to all pre-existing descendants", required = false)
            @QueryParam("recurse") @DefaultValue("false") final Boolean recurse,
            @ApiParam(value = "Value to set")
            Object newValue);

    // deletion of config is not supported; you can set it null
}
