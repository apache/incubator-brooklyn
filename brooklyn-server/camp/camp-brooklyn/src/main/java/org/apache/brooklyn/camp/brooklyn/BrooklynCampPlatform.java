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
package org.apache.brooklyn.camp.brooklyn;

import static com.google.common.base.Preconditions.checkState;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.ManagementContext.PropertiesReloadListener;
import org.apache.brooklyn.camp.AggregatingCampPlatform;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.spi.creation.BrooklynEntityMatcher;
import org.apache.brooklyn.camp.brooklyn.spi.dsl.BrooklynDslInterpreter;
import org.apache.brooklyn.camp.brooklyn.spi.platform.BrooklynImmutableCampPlatform;
import org.apache.brooklyn.camp.spi.PlatformRootSummary;
import org.apache.brooklyn.core.mgmt.HasBrooklynManagementContext;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.internal.CampYamlParser;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
            private static final long serialVersionUID = -3739276553334749184L;
            @Override public void reloaded() {
                setConfigKeyAtManagmentContext();
            }
        });
    }

    // --- brooklyn setup
    
    @Override
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
        ((ManagementContextInternal)bmc).getBrooklynProperties().put(BrooklynCampConstants.CAMP_PLATFORM, this);
        ((ManagementContextInternal)bmc).getBrooklynProperties().put(CampYamlParser.YAML_PARSER_KEY, new YamlParserImpl(this));
        return this;
    }
    
    public static class YamlParserImpl implements CampYamlParser {
        private final BrooklynCampPlatform platform;
        
        YamlParserImpl(BrooklynCampPlatform platform) {
            this.platform = platform;
        }
        
        public Map<String, Object> parse(Map<String, Object> map) {
            return platform.pdp().applyInterpreters(map);
        }
        
        public Object parse(String val) {
            Map<String, Object> result = platform.pdp().applyInterpreters(ImmutableMap.of("dummyKey", val));
            checkState(result.keySet().equals(ImmutableSet.of("dummyKey")), "expected single result, but got %s", result);
            return result.get("dummyKey");
        }
    }
}
