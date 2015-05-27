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
package brooklyn.rest.client;

import java.util.List;
import java.util.Map;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.BrooklynRestApiLauncher;
import brooklyn.rest.BrooklynRestApiLauncherTest;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.CatalogLocationSummary;
import brooklyn.rest.security.provider.TestSecurityProvider;

@Test
public class BrooklynApiRestClientTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynApiRestClientTest.class);

    private ManagementContext manager;

    private BrooklynApi api;

    protected synchronized ManagementContext getManagementContext() throws Exception {
        if (manager == null) {
            manager = new LocalManagementContext();
            BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(manager);
            BasicLocationRegistry.setupLocationRegistryForTesting(manager);
            BrooklynRestApiLauncherTest.enableAnyoneLogin(manager);
        }
        return manager;
    }

    @BeforeClass
    public void setUp() throws Exception {
        WebAppContext context;

        // running in source mode; need to use special classpath        
        context = new WebAppContext("src/test/webapp", "/");
        context.setExtraClasspath("./target/test-rest-server/");
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, getManagementContext());

        Server server = BrooklynRestApiLauncher.launcher()
                .managementContext(manager)
                .securityProvider(TestSecurityProvider.class)
                .customContext(context)
                .start();

        api = new BrooklynApi("http://localhost:" + server.getConnectors()[0].getPort() + "/",
                TestSecurityProvider.USER, TestSecurityProvider.PASSWORD);
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        for (Application app : getManagementContext().getApplications()) {
            try {
                ((StartableApplication) app).stop();
            } catch (Exception e) {
                log.warn("Error stopping app " + app + " during test teardown: " + e);
            }
        }
        Entities.destroyAll(getManagementContext());
    }

    public void testLocationApi() throws Exception {
        log.info("Testing location API");
        Map<String, Map<String, Object>> locations = api.getLocationApi().getLocatedLocations();
        log.info("locations located are: "+locations);
    }

    public void testCatalogApiLocations() throws Exception {
        List<CatalogLocationSummary> locations = api.getCatalogApi().listLocations(".*", null, false);
        log.info("locations from catalog are: "+locations);
    }

    public void testApplicationApiList() throws Exception {
        List<ApplicationSummary> apps = api.getApplicationApi().list(null);
        log.info("apps are: "+apps);
    }

}
