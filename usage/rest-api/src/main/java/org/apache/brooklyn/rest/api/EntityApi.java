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

import org.apache.brooklyn.swagger.annotations.Apidoc;
import org.apache.brooklyn.rest.domain.EntitySummary;
import org.apache.brooklyn.rest.domain.LocationSummary;
import org.apache.brooklyn.rest.domain.TaskSummary;

import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/v1/applications/{application}/entities")
@Apidoc("Entities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface EntityApi {

    @GET
    @ApiOperation(value = "Fetch the list of entities for a given application",
            responseClass = "org.apache.brooklyn.rest.domain.EntitySummary",
            multiValueResponse = true)
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application not found")
    })
    public List<EntitySummary> list(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") final String application) ;

    @GET
    @Path("/{entity}")
    @ApiOperation(value = "Fetch details about a specific application entity",
            responseClass = "org.apache.brooklyn.rest.domain.EntitySummary")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application or entity missing")
    })
    public EntitySummary get(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") String entity);

    // TODO rename as "/children" ?
    @GET
    @ApiOperation(value = "Fetch details about a specific application entity's children",
            responseClass = "org.apache.brooklyn.rest.domain.EntitySummary")
    @Path("/{entity}/children")
    public List<EntitySummary> getChildren(
            @PathParam("application") final String application,
            @PathParam("entity") final String entity);

    /** @deprecated since 0.7.0 use /children */
    @Deprecated
    @Path("/{entity}/entities")
    public List<EntitySummary> getChildrenOld(
            @PathParam("application") final String application,
            @PathParam("entity") final String entity);

    @POST
    @ApiOperation(value = "Add a child or children to this entity given a YAML spec",
            responseClass = "org.apache.brooklyn.rest.domain.TaskSummary")
    @Consumes({"application/x-yaml",
            // see http://stackoverflow.com/questions/332129/yaml-mime-type
            "text/yaml", "text/x-yaml", "application/yaml", MediaType.APPLICATION_JSON})
    @Path("/{entity}/children")
    public Response addChildren(
            @PathParam("application") final String application,
            @PathParam("entity") final String entity,

            @ApiParam(
                    name = "start",
                    value = "Whether to automatically start this child; if omitted, true for Startable entities")
            @QueryParam("start") final Boolean start,

            @ApiParam(name = "timeout", value = "Delay before server should respond with incomplete activity task, rather than completed task: " +
                    "'never' means block until complete; " +
                    "'0' means return task immediately; " +
                    "and e.g. '20ms' (the default) will wait 20ms for completed task information to be available", 
                    required = false, defaultValue = "20ms")
            @QueryParam("timeout") final String timeout,

            @ApiParam(
                    name = "childrenSpec",
                    value = "Entity spec in CAMP YAML format (including 'services' root element)",
                    required = true)
            String yaml);

    @GET
    @Path("/{entity}/activities")
    @ApiOperation(value = "Fetch list of tasks for this entity")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Could not find application or entity")
    })
    public List<TaskSummary> listTasks(
            @ApiParam(value = "Application ID or name", required = true) @PathParam("application") String applicationId,
            @ApiParam(value = "Entity ID or name", required = true) @PathParam("entity") String entityId);

    @GET
    @Path("/{entity}/activities/{task}")
    @ApiOperation(value = "Fetch task details", responseClass = "org.apache.brooklyn.rest.domain.TaskSummary")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Could not find application, entity or task")
    })
    @Produces("text/json")
    public TaskSummary getTask(
            @ApiParam(value = "Application ID or name", required = true) @PathParam("application") final String application,
            @ApiParam(value = "Entity ID or name", required = true) @PathParam("entity") final String entityToken,
            @ApiParam(value = "Task ID", required = true) @PathParam("task") String taskId);

    @GET
    @ApiOperation(value = "Returns an icon for the entity, if defined")
    @Path("/{entity}/icon")
    public Response getIcon(
            @PathParam("application") final String application,
            @PathParam("entity") final String entity);

    @GET
    @Path("/{entity}/tags")
    @ApiOperation(value = "Fetch list of tags on this entity")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Could not find application or entity")
    })
    public List<Object> listTags(
            @ApiParam(value = "Application ID or name", required = true) @PathParam("application") String applicationId,
            @ApiParam(value = "Entity ID or name", required = true) @PathParam("entity") String entityId);

    @POST
    @ApiOperation(
            value = "Rename an entity"
    )
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Undefined application or entity")
    })
    @Path("/{entity}/name")
    public Response rename(
            @ApiParam(value = "Application ID or name", required = true) @PathParam("application") final String applicationId, 
            @ApiParam(value = "Entity ID or name", required = true) @PathParam("entity") final String entityId, 
            @ApiParam(value = "New name for this entity", required = true) @QueryParam("name") final String name);

    @POST
    @ApiOperation(
            value = "Expunge an entity",
            responseClass = "org.apache.brooklyn.rest.domain.TaskSummary"
    )
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Undefined application or entity")
    })
    @Path("/{entity}/expunge")
    public Response expunge(
            @ApiParam(value = "Application ID or name", required = true) @PathParam("application") final String applicationId, 
            @ApiParam(value = "Entity ID or name", required = true) @PathParam("entity") final String entityId, 
            @ApiParam(value = "Whether to gracefully release all resources", required = true) @QueryParam("release") final boolean release);

    @GET
    @Path("/{entity}/descendants")
    @ApiOperation(value = "Fetch entity info for all (or filtered) descendants",
            responseClass = "org.apache.brooklyn.rest.domain.EntitySummary")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application or entity missing")
    })
    public List<EntitySummary> getDescendants(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") String entity,
            @ApiParam(value="Regular expression for an entity type which must be matched", required=false)
            @DefaultValue(".*")
            @QueryParam("typeRegex") String typeRegex);

    @GET
    @Path("/{entity}/descendants/sensor/{sensor}")
    @ApiOperation(value = "Fetch values of a given sensor for all (or filtered) descendants")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application or entity missing")
    })
    public Map<String,Object> getDescendantsSensor(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") String entity,
            @ApiParam(value = "Sensor name", required = true)
            @PathParam("sensor") String sensor,
            @ApiParam(value="Regular expression applied to filter descendant entities based on their type", required=false)
            @DefaultValue(".*")
            @QueryParam("typeRegex") String typeRegex);

    @GET
    @Path("/{entity}/locations")
    @ApiOperation(value = "List the locations set on the entity")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application or entity missing")
    })
    public List<LocationSummary> getLocations(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") String entity);

    @GET
    @Path("/{entity}/spec")
    @ApiOperation(value = "Get the YAML spec used to create the entity, if available")
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application or entity missing")
    })
    public String getSpec(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Entity ID or name", required = true)
            @PathParam("entity") String entity);
}
