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

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.rest.domain.EntityConfigSummary;

import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/v1/applications/{application}/entities/{entity}/config")
@Apidoc("Entity Config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface EntityConfigApi {

  @GET
  @ApiOperation(value = "Fetch the config keys for a specific application entity",
      responseClass = "brooklyn.rest.domain.ConfigSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public List<EntityConfigSummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken
  ) ;

  // TODO support parameters  ?show=value,summary&name=xxx &format={string,json,xml}
  // (and in sensors class)
  @GET
  @Path("/current-state")
  @ApiOperation(value = "Fetch config key values in batch", notes="Returns a map of config name to value")
  public Map<String, Object> batchConfigRead(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken) ;

  @GET
  @Path("/{config}")
  @ApiOperation(value = "Fetch config value (text/plain)", responseClass = "Object")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or config key")
  })
  @Produces(MediaType.TEXT_PLAIN)
  public String getPlain(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      @ApiParam(value = "Config key ID", required = true)
      @PathParam("config") String configKeyName
  );

  @GET
  @Path("/{config}")
  @ApiOperation(value = "Fetch config value (json)", responseClass = "Object")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or config key")
  })
  @Produces(MediaType.APPLICATION_JSON)
  public Object get(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      @ApiParam(value = "Config key ID", required = true)
      @PathParam("config") String configKeyName
  );

}
