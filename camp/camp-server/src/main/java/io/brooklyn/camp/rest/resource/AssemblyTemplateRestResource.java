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
package io.brooklyn.camp.rest.resource;

import io.brooklyn.camp.dto.AssemblyTemplateDto;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.rest.apidoc.Apidoc;
import brooklyn.util.exceptions.Exceptions;

import com.wordnik.swagger.core.ApiOperation;
import com.wordnik.swagger.core.ApiParam;

@Path(AssemblyTemplateRestResource.URI_PATH)
@Apidoc("Assembly Template resources")
@Produces("application/json")
public class AssemblyTemplateRestResource extends AbstractCampRestResource {

    private static final Logger log = LoggerFactory.getLogger(AssemblyTemplateRestResource.class);
    
    public static final String URI_PATH = PlatformRestResource.CAMP_URI_PATH + "/assembly-templates";

    @Path("/{id}")
    @ApiOperation(value = "Get a specific assembly template",
            responseClass = AssemblyTemplateDto.CLASS_NAME)
    @GET
    public AssemblyTemplateDto get(
            @ApiParam(value = "ID of item being retrieved", required = true)
            @PathParam("id") String id) {
        return dto().adapt(lookup(camp().assemblyTemplates(), id));
    }

    @Path("/{id}")
    @ApiOperation(value = "Instantiate a specific assembly template"
    // TODO AssemblyDto, or location thereto?
//            , responseClass = AssemblyTemplateDto.CLASS_NAME
            )
    @POST
    public Response post(
            @Context UriInfo info,
            @ApiParam(value = "ID of item being retrieved", required = true)
            @PathParam("id") String id) {
        try {
            log.info("CAMP REST instantiating AT "+id);
            AssemblyTemplate at = lookup(camp().assemblyTemplates(), id);
            Assembly assembly = at.getInstantiator().newInstance().instantiate(at, camp());
            // see http://stackoverflow.com/questions/13702481/javax-response-prepends-method-path-when-setting-location-header-path-on-status
            // for why we have to return absolute path
            URI assemblyUri = info.getBaseUriBuilder().path( dto().adapt(assembly).getUri() ).build();
            return Response.created(assemblyUri).build();
        } catch (Exception e) {
            log.error("Unable to create AT "+id+": "+e);
            throw Exceptions.propagate(e);
        }
    }
    
}
