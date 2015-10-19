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

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.brooklyn.swagger.annotations.Apidoc;
import org.apache.brooklyn.rest.domain.AccessSummary;

import com.google.common.annotations.Beta;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Beta
@Path("/v1/access")
@Apidoc("Access Control")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface AccessApi {

    // TODO First access use-case is to disable location-provisioning (for Citrix's Cloud Portal Business Manager (CPBM)).
    // We rely on location implementations calling `managementContext.getAccessController().canProvisionLocation(parent)`,
    // which isn't ideal (because some impls might forget to do this). We can't just do it in the core management-context
    // because things like JcloudsLocation will provision the VM and only then create the JcloudsSshMachineLocation.
    
    @GET
    @ApiOperation(
            value = "Fetch access control summary",
            response = org.apache.brooklyn.rest.domain.AccessSummary.class
            )
    public AccessSummary get();

    @POST
    @Path("/locationProvisioningAllowed")
    @ApiOperation(value = "Sets whether location provisioning is permitted (beta feature)")
    public Response locationProvisioningAllowed(
            @ApiParam(name = "allowed", value = "Whether allowed or not", required = true)
            @QueryParam("allowed") boolean allowed);
}
