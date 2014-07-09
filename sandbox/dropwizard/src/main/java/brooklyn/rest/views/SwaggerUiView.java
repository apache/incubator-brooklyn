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


import com.google.common.collect.Lists;
import com.wordnik.swagger.core.Api;
import com.wordnik.swagger.core.Documentation;
import com.wordnik.swagger.core.DocumentationEndPoint;
import com.wordnik.swagger.jaxrs.JaxrsApiReader;
import com.yammer.dropwizard.views.View;

import javax.ws.rs.Path;
import java.util.List;
import java.util.Set;

public class SwaggerUiView extends View {

  private final Set<Class<?>> resourceList;
  private final List<Documentation> apiList;
  private final Documentation apiListing;

  public final static String BASE_PATH = "api";
  public final static String API_VERSION = "0.1";
  public final static String SWAGGER_VERSION = "1.0";

  public SwaggerUiView(Set<Class<?>> resourceClasses) {
    super("swagger-ui.ftl");
    resourceList = resourceClasses;
    apiListing = readApiListing(resourceList);
    apiList = readApis(resourceList);
  }

  public List<Documentation> getApiList() {
    return apiList;
  }

  public Set<Class<?>> getResourceList() {
    return resourceList;
  }

  public Documentation getApiListing() {
    return apiListing;
  }

  /**
   * Returns a {@link Documentation} object that contains just the list of resources.
   *
   * @param resourceList
   * @return
   */
  public static Documentation readApiListing(Set<Class<?>> resourceList) {
    Documentation doc = new Documentation();
    doc.setSwaggerVersion(SWAGGER_VERSION);
    doc.setApiVersion(API_VERSION);
    doc.setBasePath(BASE_PATH);
    doc.setResourcePath(SwaggerUiResource.RESOURCE_PATH);
    for (Class<?> clazz : resourceList) {
      if (clazz.isAnnotationPresent(Api.class)) {
        Api apiAnnotation = clazz.getAnnotation(Api.class);
        DocumentationEndPoint api = new DocumentationEndPoint(apiAnnotation.value(), apiAnnotation.description());
        doc.addApi(api);
      }
    }
    return doc;
  }

  public static List<Documentation> readApis(Set<Class<?>> resourceList) {
    List<Documentation> apis = Lists.newArrayList();
    for (Class<?> clazz : resourceList) {
      apis.add(buildDocumentation(clazz));
    }
    return apis;
  }

  public static Documentation buildDocumentation(Class<?> clazz) {
    return JaxrsApiReader.read(clazz, API_VERSION, SWAGGER_VERSION, BASE_PATH, readApiPath(clazz));
  }

  private static String readApiPath(Class<?> clazz) {
    if (clazz.isAnnotationPresent(Api.class)) {
      return clazz.getAnnotation(Api.class).value();
    }
    throw new IllegalStateException("Class is not decorated with " + Api.class.getCanonicalName());
  }

  public static String buildPath(Class<?> clazz) {
    if (clazz.isAnnotationPresent(Path.class)) {
      return clazz.getAnnotation(Path.class).value();
    }
    throw new IllegalStateException("Class is not decorated with " + Path.class.getCanonicalName());
  }

  public static String toString(Api apiAnnotation) {
    return String.format("Value %s, description %s, listing class %s, listing path %s, open %b", apiAnnotation.value(),
        apiAnnotation.description(), apiAnnotation.listingClass(), apiAnnotation.listingPath(), apiAnnotation.open());
  }
}
