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

import java.util.HashSet;
import java.util.Set;

import org.apache.brooklyn.api.location.LocationRegistry;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.mgmt.ManagementContextInjectable;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.server.BrooklynServerConfig;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.rest.BrooklynRestApi;
import org.apache.brooklyn.rest.util.BrooklynRestResourceUtils;
import org.apache.brooklyn.rest.util.ManagementContextProvider;
import org.apache.brooklyn.rest.util.ShutdownHandlerProvider;
import org.apache.brooklyn.rest.util.TestShutdownHandler;
import org.apache.brooklyn.rest.util.json.BrooklynJacksonJsonProvider;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.reflections.util.ClasspathHelper;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

public abstract class BrooklynRestApiTest {

    public static final String SCANNING_CATALOG_BOM_URL = "classpath://brooklyn/scanning.catalog.bom";

    protected ManagementContext manager;
    
    
    protected TestShutdownHandler shutdownListener = createShutdownHandler();
    protected final static String ENDPOINT_ADDRESS_LOCAL = "local://";
    protected final static String ENDPOINT_ADDRESS_HTTP = "http://localhost:9998/";

    protected Set<Class<?>> resourceClasses;
    protected Set<Object> resourceBeans;

    @BeforeClass(alwaysRun = true)
    public void setUpClass() throws Exception {
        if (!isMethodInit()) {
            initClass();
        }
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() throws Exception {
        if (!isMethodInit()) {
            destroyClass();
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void setUpMethod() throws Exception {
        if (isMethodInit()) {
            initClass();
        }
        initMethod();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownMethod() throws Exception {
        if (isMethodInit()) {
            destroyClass();
        }
        destroyMethod();
    }
    
    protected void initClass() throws Exception {
        resourceClasses = new HashSet<>();
        resourceBeans = new HashSet<>();
    }

    protected void destroyClass() throws Exception {
        destroyManagementContext();
        resourceClasses = null;
        resourceBeans = null;
    }

    protected void initMethod() throws Exception {
        resetShutdownListener();
    }

    protected void destroyMethod() throws Exception {
    }
    
    /**
     * @return true to start/destroy the test environment for each method.
     *          Returns false by default to speed up testing.
     */
    protected boolean isMethodInit() {
        return false;
    }

    protected void resetShutdownListener() {
        shutdownListener.reset();
    }

    protected void destroyManagementContext() {
        if (manager!=null) {
            Entities.destroyAll(manager);
            resourceClasses = null;
            resourceBeans = null;
            manager = null;
        }
    }

    protected boolean useLocalScannedCatalog() {
        return false;
    }
    
    private TestShutdownHandler createShutdownHandler() {
        return new TestShutdownHandler();
    }

    protected synchronized ManagementContext getManagementContext() {
        if (manager==null) {
            if (useLocalScannedCatalog()) {
                manager = new LocalManagementContext();
                forceUseOfDefaultCatalogWithJavaClassPath();
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
    
    protected String getEndpointAddress() {
        return ENDPOINT_ADDRESS_HTTP;
    }

    protected ObjectMapper mapper() {
        return BrooklynJacksonJsonProvider.findSharedObjectMapper(null, getManagementContext());
    }

    public LocationRegistry getLocationRegistry() {
        return new BrooklynRestResourceUtils(getManagementContext()).getLocationRegistry();
    }

    protected final void addResource(Object resource) {
        if (resource instanceof Class) {
            resourceClasses.add((Class<?>)resource);
        } else {
            resourceBeans.add(resource);
        }
        if (resource instanceof ManagementContextInjectable) {
            ((ManagementContextInjectable)resource).setManagementContext(getManagementContext());
        }
    }

    protected final void addProvider(Class<?> provider) {
        addResource(provider);
    }

    protected void addDefaultResources() {
        addResource(new ShutdownHandlerProvider(shutdownListener));
        addResource(new ManagementContextProvider(getManagementContext()));
    }


    /** intended for overriding if you only want certain resources added, or additional ones added */
    protected void addBrooklynResources() {
        for (Object r: BrooklynRestApi.getBrooklynRestResources())
            addResource(r);
    }

    protected final void setUpResources() {
        addDefaultResources();
        addBrooklynResources();
        for (Object r: BrooklynRestApi.getMiscResources())
            addResource(r);
    }

    public <T> T resource(Class<T> clazz) {
        return JAXRSClientFactory.create(getEndpointAddress(), clazz);
    }

    public <T> T resource(String uri, Class<T> clazz) {
        return JAXRSClientFactory.create(getEndpointAddress() + uri, clazz);
    }

    private void forceUseOfDefaultCatalogWithJavaClassPath() {
        // don't use any catalog.xml which is set
        ((BrooklynProperties)manager.getConfig()).put(BrooklynServerConfig.BROOKLYN_CATALOG_URL, SCANNING_CATALOG_BOM_URL);
        // sets URLs for a surefire
        ((LocalManagementContext)manager).setBaseClassPathForScanning(ClasspathHelper.forJavaClassPath());
        // this also works
//        ((LocalManagementContext)manager).setBaseClassPathForScanning(ClasspathHelper.forPackage("brooklyn"));
        // but this (near-default behaviour) does not
//        ((LocalManagementContext)manager).setBaseClassLoader(getClass().getClassLoader());
    }

}
