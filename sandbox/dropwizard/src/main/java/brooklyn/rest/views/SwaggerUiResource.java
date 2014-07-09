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


import com.sun.jersey.api.core.ResourceConfig;
import com.wordnik.swagger.core.Api;
import freemarker.template.TemplateException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Set;

@Path(SwaggerUiResource.RESOURCE_PATH)
@Produces(MediaType.TEXT_HTML)
public class SwaggerUiResource {

  public static final String RESOURCE_PATH = "/v1/api/docs";

  @GET
  public SwaggerUiView showRestDocumentation(@Context ResourceConfig config)
      throws IOException, TemplateException {
    Set<Class<?>> classList = config.getRootResourceClasses();
    for (Object singleton : config.getRootResourceSingletons()) {
      if (singleton.getClass().isAnnotationPresent(Api.class)) {
        classList.add(singleton.getClass());
      }
    }
    return new SwaggerUiView(classList);
  }

}
