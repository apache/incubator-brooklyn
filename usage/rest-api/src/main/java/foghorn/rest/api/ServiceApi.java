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
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;
import foghorn.rest.domain.ServiceSummary;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;

@Path("/v1/deployments/{deployment}/devices/{device}/services")
@Apidoc("Devices")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ServiceApi {

  @GET
  @ApiOperation(value = "Fetch the list of services for a given device",
      responseClass = "brooklyn.rest.domain.ServiceSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Device not found")
  })
  public List<ServiceSummary> list(
          @ApiParam(value = "Deployment ID or name", required = true)
          @PathParam("deployment") final String deployment,
          @ApiParam(value = "Device ID or name", required = true)
          @PathParam("device") final String device
  ) ;

  @GET
  @Path("/{service}")
  @ApiOperation(value = "Fetch details about a specific service",
      responseClass = "brooklyn.rest.domain.ServiceSummary")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Deployment, device, or service missing")
  })
  public ServiceSummary get(
          @ApiParam(value = "Deployment ID or name", required = true)
          @PathParam("deployment") final String deployment,
          @ApiParam(value = "Device ID or name", required = true)
          @PathParam("device") final String device,
          @ApiParam(value = "Service ID or name", required = true)
          @PathParam("service") String service
  ) ;
}
