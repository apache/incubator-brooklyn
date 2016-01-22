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

import org.apache.brooklyn.rest.domain.LocationSpec;
import org.apache.brooklyn.rest.domain.LocationSummary;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@SuppressWarnings("deprecation")
@Path("/v1/locations")
@Api("Locations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface LocationApi {

    // this is here to support the web GUI's circles
    @GET
    @Path("/usage/LocatedLocations")
    @ApiOperation(value = "Return a summary of all usage", notes="interim API, expected to change")
    public Map<String,Map<String,Object>> getLocatedLocations();

    /**
     * WARNING: behaviour will change in a future release; this will only return location instances.
     * See {@link CatalogApi#getLocation(String, String)} for retrieving location definitions.
     */
    @GET
    @Path("/{locationId}")
    @ApiOperation(value = "Fetch details about a location instance, or a location definition",
            response = org.apache.brooklyn.rest.domain.LocationSummary.class,
            responseContainer = "List")
    public LocationSummary get(
            @ApiParam(value = "Location id to fetch", required = true)
            @PathParam("locationId") String locationId,
            @ApiParam(value = "Whether full (inherited) config should be compiled", required = false)
            @DefaultValue("false")
            @QueryParam("full") String fullConfig);

    /** @deprecated since 0.7.0 use {@link CatalogApi#create(String)} with a location definition */
    @POST
    @ApiOperation(value = "Create a new location definition", response = String.class)
    @Deprecated
    public Response create(
            @ApiParam(name = "locationSpec", value = "Location specification object", required = true)
            @Valid LocationSpec locationSpec);

}
