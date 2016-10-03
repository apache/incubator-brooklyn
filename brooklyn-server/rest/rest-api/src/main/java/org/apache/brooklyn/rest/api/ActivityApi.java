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

import org.apache.brooklyn.rest.domain.TaskSummary;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/v1/activities")
@Api("Activities")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ActivityApi {

    @GET
    @Path("/{task}")
    @ApiOperation(value = "Fetch task details", response = org.apache.brooklyn.rest.domain.TaskSummary.class)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Could not find task")
    })
//  @Produces("text/json")
    public TaskSummary get(
            @ApiParam(value = "Task ID", required = true) @PathParam("task") String taskId
            );

    @GET
    @Path("/{task}/children")
    @ApiOperation(value = "Fetch list of children tasks of this task")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Could not find task")
    })
    public List<TaskSummary> children(
            @ApiParam(value = "Task ID", required = true) @PathParam("task") String taskId);

    @GET
    @Path("/{task}/stream/{streamId}")
    @ApiOperation(value = "Return the contents of the given stream")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Could not find task or stream")
    })
    public String stream(
            @ApiParam(value = "Task ID", required = true) @PathParam("task") String taskId,
            @ApiParam(value = "Stream ID", required = true) @PathParam("streamId") String streamId);
}
