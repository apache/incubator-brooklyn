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
      @PathParam("entity") String entityToken,
      @ApiParam(value = "Return raw config data instead of display values", required = false)
      @QueryParam("raw") @DefaultValue("false") final Boolean raw
      ) ;
  
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
      @PathParam("config") String configKeyName,
      @ApiParam(value = "Return raw config data instead of display values", required = false)
      @QueryParam("raw") @DefaultValue("false") final Boolean raw
      );

  // if user requests plain value we skip some json post-processing
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
      @PathParam("config") String configKeyName,
      @ApiParam(value = "Return raw config data instead of display values", required = false)
      @QueryParam("raw") @DefaultValue("false") final Boolean raw
  );

  @POST
  @ApiOperation(value = "Manually set multiple config values")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity")
  })
  public void setFromMap(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken,
      @ApiParam(value = "Apply the config to all pre-existing descendants", required = false)
      @QueryParam("recurse") @DefaultValue("false") final Boolean recurse,
      @ApiParam(value = "Map of config key names to values", required = true)
      Map<?,?> newValues
  ) ;

  @POST
  @Path("/{config}")
  @ApiOperation(value = "Manually set a config value")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity or config key")
  })
  public void set(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") final String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") final String entityToken,
          @ApiParam(value = "Config key name", required = true)
          @PathParam("config") String configName,
          @ApiParam(value = "Apply the config to all pre-existing descendants", required = false)
          @QueryParam("recurse") @DefaultValue("false") final Boolean recurse,
          @ApiParam(value = "Value to set")
          Object newValue
  ) ;

  // deletion of config is not supported; you can set it null

}
