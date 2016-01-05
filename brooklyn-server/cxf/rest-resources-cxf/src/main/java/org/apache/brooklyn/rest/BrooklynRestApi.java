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
package org.apache.brooklyn.rest;

import java.util.ArrayList;
import java.util.List;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;

import org.apache.brooklyn.rest.resources.AbstractBrooklynRestResource;
import org.apache.brooklyn.rest.resources.AccessResource;
import org.apache.brooklyn.rest.resources.ActivityResource;
import org.apache.brooklyn.rest.resources.ApplicationResource;
import org.apache.brooklyn.rest.resources.CatalogResource;
import org.apache.brooklyn.rest.resources.EffectorResource;
import org.apache.brooklyn.rest.resources.EntityConfigResource;
import org.apache.brooklyn.rest.resources.EntityResource;
import org.apache.brooklyn.rest.resources.LocationResource;
import org.apache.brooklyn.rest.resources.PolicyConfigResource;
import org.apache.brooklyn.rest.resources.PolicyResource;
import org.apache.brooklyn.rest.resources.ScriptResource;
import org.apache.brooklyn.rest.resources.SensorResource;
import org.apache.brooklyn.rest.resources.ServerResource;
import org.apache.brooklyn.rest.resources.UsageResource;
import org.apache.brooklyn.rest.resources.VersionResource;
import org.apache.brooklyn.rest.util.DefaultExceptionMapper;
import org.apache.brooklyn.rest.util.FormMapProvider;
import org.apache.brooklyn.rest.util.json.BrooklynJacksonJsonProvider;


@SuppressWarnings("deprecation")
public class BrooklynRestApi {

    public static List<AbstractBrooklynRestResource> getResources(ManagementContext mgmt) {
        List<AbstractBrooklynRestResource> resources = new ArrayList<>();
        resources.add(new LocationResource());
        resources.add(new CatalogResource());
        resources.add(new ApplicationResource());
        resources.add(new EntityResource());
        resources.add(new EntityConfigResource());
        resources.add(new SensorResource());
        resources.add(new EffectorResource());
        resources.add(new PolicyResource());
        resources.add(new PolicyConfigResource());
        resources.add(new ActivityResource());
        resources.add(new AccessResource());
        resources.add(new ScriptResource());
        resources.add(new ServerResource());
        resources.add(new UsageResource());
        resources.add(new VersionResource());

        for (Object resource : resources ) {
            if (resource instanceof ManagementContextInjectable) {
                ((ManagementContextInjectable)resource).setManagementContext(mgmt);
            }
        }

        return resources;
    }

    public static List<Object> getProviders(ManagementContext mgmt) {
        List<Object> providers = new ArrayList<>();
        providers.add(new DefaultExceptionMapper());
        providers.add(new BrooklynJacksonJsonProvider());
        providers.add(new FormMapProvider());

        for (Object provider : providers ) {
            if (provider instanceof ManagementContextInjectable) {
                ((ManagementContextInjectable)provider).setManagementContext(mgmt);
            }
        }

        return providers;
    }

}
