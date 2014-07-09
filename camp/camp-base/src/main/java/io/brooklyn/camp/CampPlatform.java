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

import io.brooklyn.camp.spi.ApplicationComponent;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponent;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.PlatformTransaction;
import io.brooklyn.camp.spi.collection.ResourceLookup;
import io.brooklyn.camp.spi.resolve.PdpProcessor;

import com.google.common.base.Preconditions;

public abstract class CampPlatform {

    private final PlatformRootSummary root;
    private final PdpProcessor pdp;

    public CampPlatform(PlatformRootSummary root) {
        this.root = Preconditions.checkNotNull(root, "root");
        pdp = createPdpProcessor();
    }

    // --- root
    
    public PlatformRootSummary root() {
        return root;
    }

    // --- other aspects
    
    public PdpProcessor pdp() {
        return pdp;
    }

    
    // --- required custom implementation hooks
    
    public abstract ResourceLookup<PlatformComponentTemplate> platformComponentTemplates();
    public abstract ResourceLookup<ApplicationComponentTemplate> applicationComponentTemplates();
    public abstract ResourceLookup<AssemblyTemplate> assemblyTemplates();

    public abstract ResourceLookup<PlatformComponent> platformComponents();
    public abstract ResourceLookup<ApplicationComponent> applicationComponents();
    public abstract ResourceLookup<Assembly> assemblies();

    /** returns object where changes to a PDP can be made; note all changes must be committed */
    public abstract PlatformTransaction transaction();

    // --- optional customisation overrides
    
    protected PdpProcessor createPdpProcessor() {
        return new PdpProcessor(this);
    }

}
