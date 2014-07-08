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
package io.brooklyn.camp.test.mock.web;

import javax.annotation.Nullable;

import brooklyn.util.guava.Maybe;
import io.brooklyn.camp.BasicCampPlatform;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformComponentTemplate;
import io.brooklyn.camp.spi.collection.BasicResourceLookup;
import io.brooklyn.camp.spi.collection.ResolvableLink;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.pdp.Artifact;
import io.brooklyn.camp.spi.pdp.AssemblyTemplateConstructor;
import io.brooklyn.camp.spi.pdp.Service;
import io.brooklyn.camp.spi.resolve.PdpMatcher;

public class MockWebPlatform {

    public static final ApplicationComponentTemplate WAR = 
            ApplicationComponentTemplate.builder()
                .name("io.camp.mock:WAR")
                .description("Mock WAR")
                .build();

    public static final PlatformComponentTemplate APPSERVER = 
            PlatformComponentTemplate.builder()
                .name("io.camp.mock:AppServer")
                .description("Mock Application Server")
                .build();

    public static final PlatformComponentTemplate DATABASE = 
            PlatformComponentTemplate.builder()
                .name("io.camp.mock:Database")
                .description("Mock Database")
                .build();

    public static final AssemblyTemplate ASSEMBLY1 =
            AssemblyTemplate.builder()
                .name("WebAppAssembly1")
                .description("Mock Web App Assembly Template")
                .applicationComponentTemplates(BasicResourceLookup.of(WAR))
                .instantiator(MockAssemblyTemplateInstantiator.class)
                .build();

    public static final PdpMatcher WAR_GETS_WAR_MATCHER = new PdpMatcher.ArtifactMatcher("com.java:WAR") {
        public boolean apply(Object art, AssemblyTemplateConstructor atc) {
            ApplicationComponentTemplate act = ApplicationComponentTemplate.builder()
                    .name( ((Artifact)art).getName() )
                    .description( ((Artifact)art).getDescription() )
                    .customAttribute("implementation", WAR.getName())
                    .customAttribute("artifactType", ((Artifact)art).getArtifactType())
                    .build();

            // TODO requirements, etc
            
            atc.add(act);
            
            return true;
        }
    };

    public static final PdpMatcher newLiteralServiceTypeToPlatformComponentTemplateMatcher(final BasicCampPlatform platform, @Nullable final Class<? extends AssemblyTemplateInstantiator> instantiator) {
        return new PdpMatcher() {
            public boolean apply(Object item, AssemblyTemplateConstructor atc) {
                if (!(item instanceof Service)) return false;
                Service svc = (Service)item;
                String type = svc.getServiceType();
                
                for (ResolvableLink<PlatformComponentTemplate> t: platform.platformComponentTemplates().links()) {
                    if (type.equals(t.getName())) {
                        PlatformComponentTemplate pct = PlatformComponentTemplate.builder()
                            .name(svc.getName())
                            .customAttribute("serviceType", type)
                            .description(Maybe.fromNullable(svc.getDescription()).or(t.resolve().getDescription()))
                            .build();
                        if (atc!=null) {
                            atc.add(pct);
                            if (instantiator!=null)
                                atc.instantiator(instantiator);
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean accepts(Object deploymentPlanItem) {
                return apply(deploymentPlanItem, null);
            }
        };
    }
    
    public static <T extends BasicCampPlatform> T populate(T platform) {
        return populate(platform, null);
    }
    public static <T extends BasicCampPlatform> T populate(T platform, @Nullable Class<? extends AssemblyTemplateInstantiator> instantiator) {
        platform.platformComponentTemplates().addAll(APPSERVER, DATABASE);
        platform.applicationComponentTemplates().add(WAR);
        platform.assemblyTemplates().add(ASSEMBLY1);
        
        platform.pdp().addMatcher(WAR_GETS_WAR_MATCHER);
        platform.pdp().addMatcher(newLiteralServiceTypeToPlatformComponentTemplateMatcher(platform, instantiator));
        
        return platform;
    }

    public static BasicCampPlatform newPlatform() {
        return MockWebPlatform.populate(new BasicCampPlatform());
    }
    
}
