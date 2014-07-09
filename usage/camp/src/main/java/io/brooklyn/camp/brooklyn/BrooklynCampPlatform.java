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
package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.AggregatingCampPlatform;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityMatcher;
import io.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslInterpreter;
import io.brooklyn.camp.brooklyn.spi.platform.BrooklynImmutableCampPlatform;
import io.brooklyn.camp.spi.PlatformRootSummary;
import brooklyn.camp.brooklyn.api.HasBrooklynManagementContext;
import brooklyn.config.BrooklynProperties;
import brooklyn.management.ManagementContext;
import brooklyn.management.ManagementContext.PropertiesReloadListener;

/** {@link CampPlatform} implementation which includes Brooklyn entities 
 * (via {@link BrooklynImmutableCampPlatform})
 * and allows customisation / additions */
public class BrooklynCampPlatform extends AggregatingCampPlatform implements HasBrooklynManagementContext {

    private final ManagementContext bmc;

    public BrooklynCampPlatform(PlatformRootSummary root, ManagementContext managementContext) {
        super(root);
        addPlatform(new BrooklynImmutableCampPlatform(root, managementContext));
        
        this.bmc = managementContext;
        
        addMatchers();
        addInterpreters();
        
        managementContext.addPropertiesReloadListener(new PropertiesReloadListener() {
            @Override public void reloaded() {
                setConfigKeyAtManagmentContext();
            }
        });
    }

    // --- brooklyn setup
    
    public ManagementContext getBrooklynManagementContext() {
        return bmc;
    }
    
    protected void addMatchers() {
        // TODO artifacts
        pdp().addMatcher(new BrooklynEntityMatcher(bmc));
    }
    
    protected void addInterpreters() {
        pdp().addInterpreter(new BrooklynDslInterpreter());
    }

    public BrooklynCampPlatform setConfigKeyAtManagmentContext() {
        ((BrooklynProperties)bmc.getConfig()).put(BrooklynCampConstants.CAMP_PLATFORM, this);
        return this;
    }

}
