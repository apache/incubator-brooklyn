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
package org.apache.brooklyn.rest.testing;

import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.codehaus.jackson.map.ObjectMapper;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;

import com.google.common.base.Preconditions;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.JerseyTest;
import com.sun.jersey.test.framework.LowLevelAppDescriptor;

import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContextInjectable;
import brooklyn.management.internal.LocalManagementContext;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.BrooklynRestApi;
import org.apache.brooklyn.rest.BrooklynRestApiLauncherTest;
import org.apache.brooklyn.rest.util.BrooklynRestResourceUtils;
import org.apache.brooklyn.rest.util.NullHttpServletRequestProvider;
import org.apache.brooklyn.rest.util.NullServletConfigProvider;
import org.apache.brooklyn.rest.util.ShutdownHandlerProvider;
import org.apache.brooklyn.rest.util.TestShutdownHandler;
import org.apache.brooklyn.rest.util.json.BrooklynJacksonJsonProvider;

import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.exceptions.Exceptions;

public abstract class BrooklynRestApiTest {

    protected ManagementContext manager;
    
    protected boolean useLocalScannedCatalog = false;
    protected TestShutdownHandler shutdownListener = createShutdownHandler();

    @BeforeMethod(alwaysRun = true)
    public void setUpMethod() {
        shutdownListener.reset();
    }
    
    protected synchronized void useLocalScannedCatalog() {
        if (manager!=null && !useLocalScannedCatalog)
            throw new IllegalStateException("useLocalScannedCatalog must be specified before manager is accessed/created");
        useLocalScannedCatalog = true;
    }
    
    private TestShutdownHandler createShutdownHandler() {
        return new TestShutdownHandler();
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
    
    protected ObjectMapper mapper() {
        return BrooklynJacksonJsonProvider.findSharedObjectMapper(null, getManagementContext());
    }
    
    @AfterClass
    public void tearDown() throws Exception {
        destroyManagementContext();
    }

    protected void destroyManagementContext() {
        if (manager!=null) {
            Entities.destroyAll(manager);
            manager = null;
        }
    }
    
    public LocationRegistry getLocationRegistry() {
        return new BrooklynRestResourceUtils(getManagementContext()).getLocationRegistry();
    }

    private JerseyTest jerseyTest;
    protected DefaultResourceConfig config = new DefaultResourceConfig();
    
    protected final void addResource(Object resource) {
        Preconditions.checkNotNull(config, "Must run before setUpJersey");
        
        if (resource instanceof Class)
            config.getClasses().add((Class<?>)resource);
        else
            config.getSingletons().add(resource);
        
        if (resource instanceof ManagementContextInjectable) {
            ((ManagementContextInjectable)resource).injectManagementContext(getManagementContext());
        }
    }
    
    protected final void addProvider(Class<?> provider) {
        Preconditions.checkNotNull(config, "Must run before setUpJersey");
        
        config.getClasses().add(provider);
    }
    
    protected void addDefaultResources() {
        // seems we have to provide our own injector because the jersey test framework 
        // doesn't inject ServletConfig and it all blows up
        // and the servlet config provider must be an instance; addClasses doesn't work for some reason
        addResource(new NullServletConfigProvider());
        addProvider(NullHttpServletRequestProvider.class);
        addResource(new ShutdownHandlerProvider(shutdownListener));
    }

    protected final void setUpResources() {
        addDefaultResources();
        addBrooklynResources();
        for (Object r: BrooklynRestApi.getMiscResources())
            addResource(r);
    }

    /** intended for overriding if you only want certain resources added, or additional ones added */
    protected void addBrooklynResources() {
        for (Object r: BrooklynRestApi.getBrooklynRestResources())
            addResource(r);
    }

    protected void setUpJersey() {
        setUpResources();
        
        jerseyTest = new JerseyTest() {
            @Override
            protected AppDescriptor configure() {
                return new LowLevelAppDescriptor.Builder(config).build();
            }
        };
        config = null;
        try {
            jerseyTest.setUp();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    protected void tearDownJersey() {
        if (jerseyTest != null) {
            try {
                jerseyTest.tearDown();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        }
        config = new DefaultResourceConfig();
    }

    public Client client() {
        Preconditions.checkNotNull(jerseyTest, "Must run setUpJersey first");
        return jerseyTest.client();
    }
}
