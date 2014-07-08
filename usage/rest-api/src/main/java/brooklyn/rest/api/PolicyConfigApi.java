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
import brooklyn.rest.domain.PolicyConfigSummary;
import com.wordnik.swagger.core.ApiError;
import com.wordnik.swagger.core.ApiErrors;
import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/v1/applications/{application}/entities/{entity}/policies/{policy}/config")
@Apidoc("Entity Policy Config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface PolicyConfigApi {

  @GET
  @ApiOperation(value = "Fetch the config keys for a specific policy",
      responseClass = "brooklyn.rest.domain.ConfigSummary",
      multiValueResponse = true)
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application or entity or policy")
  })
  public List<PolicyConfigSummary> list(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") final String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") final String entityToken,
      @ApiParam(value = "Policy ID or name", required = true)
      @PathParam("policy") final String policyToken
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
      @ApiParam(value = "Policy ID or name", required = true)
      @PathParam("policy") String policyToken) ;

  @GET
  @Path("/{config}")
  @ApiOperation(value = "Fetch config value", responseClass = "Object")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity, policy or config key")
  })
  public String get(
      @ApiParam(value = "Application ID or name", required = true)
      @PathParam("application") String application,
      @ApiParam(value = "Entity ID or name", required = true)
      @PathParam("entity") String entityToken,
      @ApiParam(value = "Policy ID or name", required = true)
      @PathParam("policy") String policyToken,
      @ApiParam(value = "Config key ID", required = true)
      @PathParam("config") String configKeyName
  ) ;

  @POST
  @Path("/{config}/set")
  @ApiOperation(value = "Sets the given config on this policy")
  @ApiErrors(value = {
      @ApiError(code = 404, reason = "Could not find application, entity, policy or config key")
  })
  public Response set(
          @ApiParam(value = "Application ID or name", required = true)
          @PathParam("application") String application,
          @ApiParam(value = "Entity ID or name", required = true)
          @PathParam("entity") String entityToken,
          @ApiParam(value = "Policy ID or name", required = true)
          @PathParam("policy") String policyToken,
          @ApiParam(value = "Config key ID", required = true)
          @PathParam("config") String configKeyName,
          @ApiParam(name = "value", value = "New value for the configuration", required = true)
          @QueryParam("value") String value
  ) ;
}
