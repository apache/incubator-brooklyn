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
package brooklyn.rest.testing;

import brooklyn.rest.util.FormMapProvider;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;

import org.testng.annotations.AfterClass;

import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.BrooklynRestApi;
import brooklyn.rest.BrooklynRestApiLauncherTest;
import brooklyn.rest.resources.AbstractBrooklynRestResource;
import brooklyn.rest.util.BrooklynRestResourceUtils;
import brooklyn.rest.util.DefaultExceptionMapper;
import brooklyn.rest.util.NullHttpServletRequestProvider;
import brooklyn.rest.util.NullServletConfigProvider;
import brooklyn.test.entity.LocalManagementContextForTests;

import com.yammer.dropwizard.testing.ResourceTest;

public abstract class BrooklynRestApiTest extends ResourceTest {

    private ManagementContext manager;
    
    protected boolean useLocalScannedCatalog = false;
    
    protected synchronized void useLocalScannedCatalog() {
        if (manager!=null && !useLocalScannedCatalog)
            throw new IllegalStateException("useLocalScannedCatalog must be specified before manager is accessed/created");
        useLocalScannedCatalog = true;
    }
    
    protected synchronized ManagementContext getManagementContext() {
        if (manager==null) {
            if (useLocalScannedCatalog) {
                manager = new LocalManagementContext();
                BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(manager);
            } else {
                manager = new LocalManagementContextForTests();
            }
            manager.getHighAvailabilityManager().disabled();
            BasicLocationRegistry.setupLocationRegistryForTesting(manager);
            
            new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(manager)
                .launch();
        }
        return manager;
    }
    
    @AfterClass
    public void tearDown() throws Exception {
        if (manager!=null) {
            Entities.destroyAll(manager);
            manager = null;
        }
    }
    
    public LocationRegistry getLocationRegistry() {
        return new BrooklynRestResourceUtils(getManagementContext()).getLocationRegistry();
    }

    @Override
    protected final void addResource(Object resource) {
        // seems we have to provide our own injector because the jersey test framework 
        // doesn't inject ServletConfig and it all blows up
        addProvider(NullServletConfigProvider.class);
        addProvider(NullHttpServletRequestProvider.class);
        
      super.addResource(resource);
      if (resource instanceof AbstractBrooklynRestResource) {
          ((AbstractBrooklynRestResource)resource).injectManagementContext(getManagementContext());
      }
    }
    
    protected void addResources() {
        addProvider(DefaultExceptionMapper.class);
        addProvider(FormMapProvider.class);
        for (Object r: BrooklynRestApi.getBrooklynRestResources())
            addResource(r);
    }

    /** intended for overriding if you only want certain resources added, or additional ones added */
    @Override
    protected void setUpResources() throws Exception {
        addResources();
    }

}
