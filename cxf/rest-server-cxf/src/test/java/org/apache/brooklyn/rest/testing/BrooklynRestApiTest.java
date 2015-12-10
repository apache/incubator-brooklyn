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

import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.codehaus.jackson.map.ObjectMapper;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;

import org.apache.brooklyn.rest.util.BrooklynRestResourceUtils;
import org.apache.brooklyn.rest.util.TestShutdownHandler;
import org.apache.brooklyn.rest.util.json.BrooklynJacksonJsonProvider;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;

public abstract class BrooklynRestApiTest {

    protected ManagementContext manager;
    
    protected boolean useLocalScannedCatalog = false;
    protected TestShutdownHandler shutdownListener = createShutdownHandler();

    protected final static String ENDPOINT_ADDRESS = "local://v1";

    @BeforeMethod(alwaysRun = true)
    public void resetShutdownListener() {
        shutdownListener.reset();
    }

    @AfterClass
    public void destroyManagementContext() {
        if (manager!=null) {
            Entities.destroyAll(manager);
            manager = null;
        }
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
//                BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(manager);
                throw new UnsupportedOperationException("FIXME: this test should be part of integration tests, not unit tests");
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
    
    public LocationRegistry getLocationRegistry() {
        return new BrooklynRestResourceUtils(getManagementContext()).getLocationRegistry();
    }

    public WebClient client() {
        return WebClient.create(ENDPOINT_ADDRESS);
    }

    public <T> T resource(Class<T> clazz) {
        return JAXRSClientFactory.create(ENDPOINT_ADDRESS, clazz);
    }

    public <T> T resource(String uri, Class<T> clazz) {
        return JAXRSClientFactory.create(ENDPOINT_ADDRESS + uri, clazz);
    }
}
