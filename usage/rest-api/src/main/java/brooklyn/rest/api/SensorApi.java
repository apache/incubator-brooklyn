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
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.SensorSummary;

import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path("/v1/applications/{application}/entities/{entity}/sensors")
@Apidoc("Entity Sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface SensorApi {

  @GET
  @ApiOperation(value = "Fetch the sensor list for a specific application entity",
      responseClass = "brooklyn.rest.domain.SensorSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<SensorSummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken
  ) ;

  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch sensor values in batch", notes="Returns a map of sensor name to value")
  public Map<String, Object> batchSensorRead(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken,
          @ApiParam(value = "Return raw sensor data instead of display values", required = false)
          @QueryParam("raw") @DefaultValue("false") final Boolean raw
      ) ;

  @GET
  @Path("/{sensor}")
  @ApiOperation(value = "Fetch sensor value (text/plain)", responseClass = "String")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or sensor")
  })
  @Produces(MediaType.TEXT_PLAIN)
  public String getPlain(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken,
          @ApiParam(value = "Sensor name", required = true)
          @PathParam("sensor") String sensorName,
          @ApiParam(value = "Return raw sensor data instead of display values", required = false)
          @QueryParam("raw") @DefaultValue("false") final Boolean raw
  ) ;

  @GET
  @Path("/{sensor}")
  @ApiOperation(value = "Fetch sensor value (json)", responseClass = "Object")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or sensor")
  })
  public Object get(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken,
          @ApiParam(value = "Sensor name", required = true)
          @PathParam("sensor") String sensorName,
          @ApiParam(value = "Return raw sensor data instead of display values", required = false)
          @QueryParam("raw") @DefaultValue("false") final Boolean raw
  ) ;

}
