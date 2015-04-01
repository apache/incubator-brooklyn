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
package foghorn.rest.api;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.domain.TaskSummary;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;
import foghorn.rest.domain.DeviceSummary;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/v1/deployments/{deployment}/devices")
@Apidoc("Devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface DeviceApi {

  @GET
  @ApiOperation(value = "Fetch the list of devices for a given deployment",
      responseClass = "brooklyn.rest.domain.DeviceSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Deployment not found")
  })
  public List<DeviceSummary> list(
          @ApiParam(value = "Deployment ID or name", required = true)
          @PathParam("deployment") final String deployment) ;

  @GET
  @Path("/{device}")
  @ApiOperation(value = "Fetch details about a specific deployment device",
      responseClass = "brooklyn.rest.domain.DeviceSummary")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Deployment or device missing")
  })
  public DeviceSummary get(
          @ApiParam(value = "Deployment ID or name", required = true)
          @PathParam("deployment") String deployment,
          @ApiParam(value = "Device ID or name", required = true)
          @PathParam("device") String device
  ) ;
}
