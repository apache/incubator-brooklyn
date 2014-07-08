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
package io.brooklyn.camp.brooklyn.spi.platform;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.spi.lookup.AssemblyBrooklynLookup;
import io.brooklyn.camp.brooklyn.spi.lookup.AssemblyTemplateBrooklynLookup;
import io.brooklyn.camp.brooklyn.spi.lookup.PlatformComponentBrooklynLookup;
import io.brooklyn.camp.brooklyn.spi.lookup.PlatformComponentTemplateBrooklynLookup;
import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.PlatformTransaction;
import io.brooklyn.camp.spi.collection.BasicResourceLookup;
import io.brooklyn.camp.spi.collection.ResourceLookup;
import io.brooklyn.camp.spi.collection.ResourceLookup.EmptyResourceLookup;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.management.ManagementContext;

/** Immutable CAMP platform which reflects things in the underlying Brooklyn system */
public class BrooklynImmutableCampPlatform extends CampPlatform implements HasBrooklynManagementContext {

    private final ManagementContext bmc;
    private final AssemblyTemplateBrooklynLookup ats;
    private final PlatformComponentTemplateBrooklynLookup pcts;
    private final BasicResourceLookup<ApplicationComponentTemplate> acts;
    private final PlatformComponentBrooklynLookup pcs;
    private final AssemblyBrooklynLookup assemblies;

    public BrooklynImmutableCampPlatform(PlatformRootSummary root, ManagementContext managementContext) {
        super(root);
        this.bmc = managementContext;
        
        // these come from brooklyn
        pcts = new PlatformComponentTemplateBrooklynLookup(root(), getBrooklynManagementContext());
        ats = new AssemblyTemplateBrooklynLookup(root(), getBrooklynManagementContext());
        pcs = new PlatformComponentBrooklynLookup(root(), getBrooklynManagementContext());
        assemblies = new AssemblyBrooklynLookup(root(), getBrooklynManagementContext(), pcs);
        
        // ACT's are not known in brooklyn (everything comes in as config) -- to be extended to support!
        acts = new BasicResourceLookup<ApplicationComponentTemplate>();
    }

    // --- brooklyn setup
    
    public ManagementContext getBrooklynManagementContext() {
        return bmc;
    }
    
    // --- camp comatibility setup
    
    @Override
    public ResourceLookup<PlatformComponentTemplate> platformComponentTemplates() {
        return pcts;
    }

    @Override
    public ResourceLookup<ApplicationComponentTemplate> applicationComponentTemplates() {
        return acts;
    }

    @Override
    public ResourceLookup<AssemblyTemplate> assemblyTemplates() {
        return ats;
    }
    
    @Override
    public ResourceLookup<PlatformComponent> platformComponents() {
        return pcs;
    }

    @Override
    public ResourceLookup<ApplicationComponent> applicationComponents() {
        return new EmptyResourceLookup<ApplicationComponent>();
    }

    @Override
    public ResourceLookup<Assembly> assemblies() {
        return assemblies;
    }
    
    @Override
    public PlatformTransaction transaction() {
        throw new IllegalStateException(this+" does not support adding new items");
    }
    
}
