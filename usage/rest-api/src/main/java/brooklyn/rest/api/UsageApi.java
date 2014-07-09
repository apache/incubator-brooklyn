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

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.UsageStatistics;

import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/usage")
@Apidoc("Usage")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface UsageApi {

    // TODO should `/applications?start=...` only return those applications matching the constraint?
    // Or return all applications, but with empty statistics for some?
    // Currently it returns only those applications that match.
    
    @GET
    @Path("/applications")
    @ApiOperation(
            value = "Retrieve usage information about all applications",
            responseClass = "brooklyn.rest.domain.UsageStatistics"
    )
    @ApiErrors(value = {})
    public List<UsageStatistics> listApplicationsUsage(
            @ApiParam(
                    name = "start",
                    value = "timestamp of start marker for usage reporting, in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("start") String startDate,
            @ApiParam(
                    name = "end",
                    value = "timestamp of end marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("end") String endDate) ;

    @GET
    @Path("/applications/{application}")
    @ApiOperation(
            value = "Retrieve usage information about a specified application",
            responseClass = "brooklyn.rest.domain.UsageStatistics"
    )
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application not found")
    })
    public UsageStatistics getApplicationUsage(
            @ApiParam(
                    name = "application",
                    value = "Application id",
                    required = true
            )
            @PathParam("application") String applicationId,
            @ApiParam(
                    name = "start",
                    value = "timestamp of start marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("start") String startDate,
            @ApiParam(
                    name = "end",
                    value = "timestamp of end marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("end") String endDate) ;

    @GET
    @Path("/machines")
    @ApiOperation(
            value = "Retrieve usage information about all machine locations, optionally filtering for a specific application and/or time range",
            responseClass = "brooklyn.rest.domain.UsageStatistics"
    )
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Application not found")
    })
    public List<UsageStatistics> listMachinesUsage(
            @ApiParam(
                    name = "application",
                    value = "Application id",
                    required = false
            )
            @QueryParam("application") String application,
            @ApiParam(
                    name = "start",
                    value = "timestamp of start marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("start") String startDate,
            @ApiParam(
                    name = "end",
                    value = "timestamp of end marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("end") String endDate) ;

    @GET
    @Path("/machines/{machine}")
    @ApiOperation(
            value = "Retrieve usage information about a specific machine location",
            responseClass = "brooklyn.rest.domain.UsageStatistics"
    )
    @ApiErrors(value = {
            @ApiError(code = 404, reason = "Machine not found")
    })
    public UsageStatistics getMachineUsage(
            @ApiParam(
                    name = "machine",
                    value = "Machine id",
                    required = true
            )
            @PathParam("machine") String machine,
            @ApiParam(
                    name = "start",
                    value = "timestamp of start marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("start") String startDate,
            @ApiParam(
                    name = "end",
                    value = "timestamp of end marker for usage reporting in format UTC millis or yyyy-MM-dd'T'HH:mm:ssZ",
                    required = false
            )
            @QueryParam("end") String endDate) ;
}
