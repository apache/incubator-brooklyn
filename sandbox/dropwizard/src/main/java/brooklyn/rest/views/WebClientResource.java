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
package brooklyn.rest.views;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.ApiOperation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URL;

@Path("/")
@Api(value = "/", description = "Loads the javascript client for this web service")
@Produces(MediaType.TEXT_HTML)
public class WebClientResource {

  @GET
  @ApiOperation(value = "JavaScript client GUI page")
  public Response backboneUi() {
    String pageContent = "";
    try {
      URL clientPage = Resources.getResource("assets/index.html");
      pageContent = Resources.toString(clientPage, Charsets.UTF_8);
    } catch (IOException e) {
      return Response.serverError().build();
    }
    return Response.ok(pageContent).build();
  }
  
}
