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

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.ApplicationSummary;
import org.apache.brooklyn.rest.domain.EntitySummary;

import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Path("/v1/applications")
@Api("Applications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ApplicationApi {

    @GET
    @Path("/fetch")
    @ApiOperation(
            value = "Fetch display details for all applications and optionally selected additional entities"
    )
    public List<EntitySummary> fetch(
            @ApiParam(value="Selected additional entity ID's to include, comma-separated", required=false)
            @DefaultValue("")
            @QueryParam("items") String items);

    @GET
    @ApiOperation(
            value = "Fetch list of applications, as ApplicationSummary objects",
            response = org.apache.brooklyn.rest.domain.ApplicationSummary.class
    )
    public List<ApplicationSummary> list(
            @ApiParam(value = "Regular expression to filter by", required = false)
            @DefaultValue(".*")
            @QueryParam("typeRegex") String typeRegex);

    // would be nice to have this on the API so default type regex not needed, but
    // not yet implemented, as per: https://issues.jboss.org/browse/RESTEASY-798
    // (this method was added to this class, but it breaks the rest client)
//    /** As {@link #list(String)}, filtering for <code>.*</code>. */
//    public List<ApplicationSummary> list();

    @GET
    @Path("/{application}")
    @ApiOperation(
            value = "Fetch a specific application",
            response = org.apache.brooklyn.rest.domain.ApplicationSummary.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Application not found")
    })
    public ApplicationSummary get(
            @ApiParam(
                    value = "ID or name of application whose details will be returned",
                    required = true)
            @PathParam("application") String application);

    @POST
    @Consumes({"application/x-yaml",
            // see http://stackoverflow.com/questions/332129/yaml-mime-type
            "text/yaml", "text/x-yaml", "application/yaml"})
    @ApiOperation(
            value = "Create and start a new application from YAML",
            response = org.apache.brooklyn.rest.domain.TaskSummary.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Undefined entity or location"),
            @ApiResponse(code = 412, message = "Application already registered")
    })
    public Response createFromYaml(
            @ApiParam(
                    name = "applicationSpec",
                    value = "App spec in CAMP YAML format",
                    required = true)
            String yaml);

    // TODO archives
//    @Consumes({"application/x-tar", "application/x-tgz", "application/x-zip"})

    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN})
    @ApiOperation(
            value = "Create and start a new application from miscellaneous types, including JSON either new CAMP format or legacy AppSpec format",
            response = org.apache.brooklyn.rest.domain.TaskSummary.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Undefined entity or location"),
            @ApiResponse(code = 412, message = "Application already registered")
    })
    public Response createPoly(
            @ApiParam(
                    name = "applicationSpec",
                    value = "App spec in JSON, YAML, or other (auto-detected) format",
                    required = true)
            byte[] autodetectedInput);

    @POST
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED})
    @ApiOperation(
            value = "Create and start a new application from form URL-encoded contents (underlying type autodetected)",
            response = org.apache.brooklyn.rest.domain.TaskSummary.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Undefined entity or location"),
            @ApiResponse(code = 412, message = "Application already registered")
    })
    public Response createFromForm(
            @ApiParam(
                    name = "applicationSpec",
                    value = "App spec in form-encoded YAML, JSON, or other (auto-detected) format",
                    required = true)
            @Valid String contents);

    @DELETE
    @Path("/{application}")
    @ApiOperation(
            value = "Delete a specified application",
            response = org.apache.brooklyn.rest.domain.TaskSummary.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Application not found")
    })
    public Response delete(
            @ApiParam(
                    name = "application",
                    value = "Application name",
                    required = true)
            @PathParam("application") String application);

    /** @deprecated since 0.7.0 the {@link ApplicationSpec} is being retired in favour of CAMP YAML/ZIP
     * (however in 0.7.0 you can still pass this object as JSON and it will be autodetected) */
    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN})
    @ApiOperation(
            value = "Create and start a new application from miscellaneous types, including JSON either new CAMP format or legacy AppSpec format",
            response = org.apache.brooklyn.rest.domain.TaskSummary.class
    )
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Undefined entity or location"),
            @ApiResponse(code = 412, message = "Application already registered")
    })
    @Path("/createLegacy")
    @Deprecated
    public Response create(ApplicationSpec applicationSpec);

    @GET
    @Path("/{application}/descendants")
    @ApiOperation(value = "Fetch entity info for all (or filtered) descendants",
            response = org.apache.brooklyn.rest.domain.EntitySummary.class)
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Application or entity missing")
    })
    public List<EntitySummary> getDescendants(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value="Regular expression for an entity type which must be matched", required=false)
            @DefaultValue(".*")
            @QueryParam("typeRegex") String typeRegex);

    @GET
    @Path("/{application}/descendants/sensor/{sensor}")
            @ApiOperation(value = "Fetch values of a given sensor for all (or filtered) descendants")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Application or entity missing")
    })
    public Map<String,Object> getDescendantsSensor(
            @ApiParam(value = "Application ID or name", required = true)
            @PathParam("application") String application,
            @ApiParam(value = "Sensor name", required = true)
            @PathParam("sensor") String sensor,
            @ApiParam(value="Regular expression for an entity type which must be matched", required=false)
            @DefaultValue(".*")
            @QueryParam("typeRegex") String typeRegex);

}
