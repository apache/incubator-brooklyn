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
package io.brooklyn.camp;

import io.brooklyn.camp.rest.resource.AbstractCampRestResource;
import io.brooklyn.camp.rest.resource.ApidocRestResource;
import io.brooklyn.camp.rest.resource.ApplicationComponentRestResource;
import io.brooklyn.camp.rest.resource.ApplicationComponentTemplateRestResource;
import io.brooklyn.camp.rest.resource.AssemblyRestResource;
import io.brooklyn.camp.rest.resource.AssemblyTemplateRestResource;
import io.brooklyn.camp.rest.resource.PlatformComponentRestResource;
import io.brooklyn.camp.rest.resource.PlatformComponentTemplateRestResource;
import io.brooklyn.camp.rest.resource.PlatformRestResource;

import java.util.ArrayList;
import java.util.List;

import brooklyn.rest.apidoc.ApidocHelpMessageBodyWriter;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.collect.Iterables;

public class CampRestResources {

    public static Iterable<AbstractCampRestResource> getCampRestResources() {
        List<AbstractCampRestResource> resources = new ArrayList<AbstractCampRestResource>();
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
        List<Object> resources = new ArrayList<Object>();
        resources.add(new ApidocHelpMessageBodyWriter());
        resources.add(new ApidocRestResource());
        return resources;
    }

    public static Iterable<Object> getMiscResources() {
        List<Object> resources = new ArrayList<Object>();
        resources.add(new JacksonJsonProvider());
        return resources;
    }

    public static Iterable<Object> getAllResources() {
        return Iterables.concat(getCampRestResources(), getApidocResources(), getMiscResources());
    }

}
