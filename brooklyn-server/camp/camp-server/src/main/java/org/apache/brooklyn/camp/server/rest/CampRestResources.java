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
package org.apache.brooklyn.camp.server.rest;

import java.util.ArrayList;
import java.util.List;

import org.apache.brooklyn.camp.server.rest.resource.AbstractCampRestResource;
import org.apache.brooklyn.camp.server.rest.resource.ApidocRestResource;
import org.apache.brooklyn.camp.server.rest.resource.ApplicationComponentRestResource;
import org.apache.brooklyn.camp.server.rest.resource.ApplicationComponentTemplateRestResource;
import org.apache.brooklyn.camp.server.rest.resource.AssemblyRestResource;
import org.apache.brooklyn.camp.server.rest.resource.AssemblyTemplateRestResource;
import org.apache.brooklyn.camp.server.rest.resource.PlatformComponentRestResource;
import org.apache.brooklyn.camp.server.rest.resource.PlatformComponentTemplateRestResource;
import org.apache.brooklyn.camp.server.rest.resource.PlatformRestResource;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.collect.Iterables;
import io.swagger.jaxrs.listing.SwaggerSerializers;

public class CampRestResources {

    public static Iterable<AbstractCampRestResource> getCampRestResources() {
        List<AbstractCampRestResource> resources = new ArrayList<>();
        resources.add(new PlatformRestResource());
        resources.add(new AssemblyTemplateRestResource());
        resources.add(new PlatformComponentTemplateRestResource());
        resources.add(new ApplicationComponentTemplateRestResource());
        resources.add(new AssemblyRestResource());
        resources.add(new PlatformComponentRestResource());
        resources.add(new ApplicationComponentRestResource());
        return resources;
    }

    public static Iterable<Object> getApidocResources() {
        List<Object> resources = new ArrayList<>();
        resources.add(new ApidocRestResource());
        return resources;
    }

    public static Iterable<Object> getMiscResources() {
        List<Object> resources = new ArrayList<>();
        resources.add(new SwaggerSerializers());
        resources.add(new JacksonJsonProvider());
        return resources;
    }

    public static Iterable<Object> getAllResources() {
        return Iterables.concat(getCampRestResources(), getApidocResources(), getMiscResources());
    }

}
