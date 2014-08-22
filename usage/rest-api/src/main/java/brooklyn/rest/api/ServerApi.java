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

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.HighAvailabilitySummary;
import brooklyn.rest.domain.VersionSummary;

import com.google.common.annotations.Beta;
import com.wordnik.swagger.core.ApiOperation;

@Path("/v1/server")
@Apidoc("Server")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)

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
        @FormParam("stopAppsFirst") @DefaultValue("false") boolean stopAppsFirst,
        @FormParam("delayMillis") @DefaultValue("250") long delayMillis);

    @GET
    @Path("/version")
    @ApiOperation(value = "Return version identifier information for this Brooklyn instance", responseClass = "String", multiValueResponse = false)
    public VersionSummary getVersion();

    @GET
    @Path("/status")
    @ApiOperation(value = "Returns the status of this Brooklyn instance",
        responseClass = "String",
        multiValueResponse = false)
    public String getStatus();

    @GET
    @Path("/highAvailability")
    @ApiOperation(value = "Fetches the status of all Brooklyn instances in the management plane",
        responseClass = "brooklyn.rest.domain.HighAvailabilitySummary")
    public HighAvailabilitySummary getHighAvailability();
    
    @GET
    @Path("/user")
    @ApiOperation(value = "Return user information for this Brooklyn instance", responseClass = "String", multiValueResponse = false)
    public String getUser();
}
