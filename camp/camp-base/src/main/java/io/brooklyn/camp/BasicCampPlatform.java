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
import io.brooklyn.camp.spi.collection.BasicResourceLookup;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A {@link CampPlatform} implementation which is empty but allows adding new items */
public class BasicCampPlatform extends CampPlatform {

    private static final Logger log = LoggerFactory.getLogger(BasicCampPlatform.class);
    
    public BasicCampPlatform() {
        this(PlatformRootSummary.builder().name("CAMP Platform").build());
    }
    
    public BasicCampPlatform(PlatformRootSummary root) {
        super(root);
    }

    BasicResourceLookup<PlatformComponentTemplate> platformComponentTemplates = new BasicResourceLookup<PlatformComponentTemplate>();
    BasicResourceLookup<ApplicationComponentTemplate> applicationComponentTemplates = new BasicResourceLookup<ApplicationComponentTemplate>();
    BasicResourceLookup<AssemblyTemplate> assemblyTemplates = new BasicResourceLookup<AssemblyTemplate>();

    BasicResourceLookup<PlatformComponent> platformComponents = new BasicResourceLookup<PlatformComponent>();
    BasicResourceLookup<ApplicationComponent> applicationComponents = new BasicResourceLookup<ApplicationComponent>();
    BasicResourceLookup<Assembly> assemblies = new BasicResourceLookup<Assembly>();

    public BasicResourceLookup<PlatformComponentTemplate> platformComponentTemplates() {
        return platformComponentTemplates;
    }

    @Override
    public BasicResourceLookup<ApplicationComponentTemplate> applicationComponentTemplates() {
        return applicationComponentTemplates;
    }

    public BasicResourceLookup<AssemblyTemplate> assemblyTemplates() {
        return assemblyTemplates;
    }
    
    public BasicResourceLookup<PlatformComponent> platformComponents() {
        return platformComponents;
    }

    @Override
    public BasicResourceLookup<ApplicationComponent> applicationComponents() {
        return applicationComponents;
    }

    public BasicResourceLookup<Assembly> assemblies() {
        return assemblies;
    }
    
    @Override
    public PlatformTransaction transaction() {
        return new BasicPlatformTransaction(this);
    }
    
    public static class BasicPlatformTransaction extends PlatformTransaction {
        private final BasicCampPlatform platform;
        private final AtomicBoolean committed = new AtomicBoolean(false);
        
        public BasicPlatformTransaction(BasicCampPlatform platform) {
            this.platform = platform;
        }
        
        @Override
        public void commit() {
            if (committed.getAndSet(true)) 
                throw new IllegalStateException("transaction being committed multiple times");
            
            for (Object o: additions) {
                if (o instanceof AssemblyTemplate) {
                    platform.assemblyTemplates.add((AssemblyTemplate) o);
                    continue;
                }
                if (o instanceof PlatformComponentTemplate) {
                    platform.platformComponentTemplates.add((PlatformComponentTemplate) o);
                    continue;
                }
                if (o instanceof ApplicationComponentTemplate) {
                    platform.applicationComponentTemplates.add((ApplicationComponentTemplate) o);
                    continue;
                }
                
                if (o instanceof Assembly) {
                    platform.assemblies.add((Assembly) o);
                    continue;
                }
                if (o instanceof PlatformComponent) {
                    platform.platformComponents.add((PlatformComponent) o);
                    continue;
                }
                if (o instanceof ApplicationComponent) {
                    platform.applicationComponents.add((ApplicationComponent) o);
                    continue;
                }

                throw new UnsupportedOperationException("Object "+o+" of type "+o.getClass()+" cannot be added to "+platform);
            }
        }
        
        @Override
        protected void finalize() throws Throwable {
            if (!committed.get())
                log.warn("transaction "+this+" was never applied");
            super.finalize();
        }
    }
    
}
