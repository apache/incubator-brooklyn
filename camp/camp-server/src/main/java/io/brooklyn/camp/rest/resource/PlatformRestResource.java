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

import io.brooklyn.camp.dto.PlatformDto;
import io.brooklyn.camp.rest.util.WebResourceUtils;
import io.brooklyn.camp.spi.AssemblyTemplate;

import java.io.InputStream;
import java.io.StringReader;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.rest.apidoc.Apidoc;

import com.wordnik.swagger.core.ApiOperation;

//import io.brooklyn.camp.rest.apidoc.Apidoc;

@Path(PlatformRestResource.CAMP_URI_PATH)
@Apidoc("Platform (root)")
@Produces("application/json")
public class PlatformRestResource extends AbstractCampRestResource {

    private static final Logger log = LoggerFactory.getLogger(PlatformRestResource.class);
    
    public static final String CAMP_URI_PATH = "/camp/v11";
    
    @ApiOperation(value = "Return the Platform (root) resource",
            responseClass = PlatformDto.CLASS_NAME)
    @GET
    public PlatformDto get() {
        return dto().adapt(camp().root());
    }
    
    @POST
    @Consumes({MediaType.APPLICATION_JSON})
    public Response postJson(@Context UriInfo info, String json) {
        return postYaml(info, json);
    }

    @POST
    @Consumes({"application/x-yaml"})
    public Response postYaml(@Context UriInfo info, String yaml) {
        log.debug("YAML pdp:\n"+yaml);
        AssemblyTemplate template = camp().pdp().registerDeploymentPlan(new StringReader(yaml));
        return created(info, template);
    }

    @POST
    @Consumes({"application/x-tar", "application/x-tgz", "application/x-zip"})
    public Response postArchive(@Context UriInfo info, InputStream archiveInput) {
        log.debug("ARCHIVE pdp");
        AssemblyTemplate template = camp().pdp().registerPdpFromArchive(archiveInput);
        return created(info, template);
    }

    protected Response created(UriInfo info, AssemblyTemplate template) {
        return WebResourceUtils.created(info, dto().adapt(template).getUri());
    }

}
