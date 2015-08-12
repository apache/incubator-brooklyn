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
package org.apache.brooklyn.rest.client;

import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.brooklyn.test.HttpTestUtils;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.internal.LocalManagementContext;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.BrooklynRestApiLauncher;
import org.apache.brooklyn.rest.BrooklynRestApiLauncherTest;
import org.apache.brooklyn.rest.domain.ApplicationSummary;
import org.apache.brooklyn.rest.domain.CatalogLocationSummary;
import org.apache.brooklyn.rest.security.provider.TestSecurityProvider;
import org.apache.brooklyn.test.HttpTestUtils;

import brooklyn.test.entity.TestEntity;

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
        Server server = BrooklynRestApiLauncher.launcher()
                .managementContext(manager)
                .securityProvider(TestSecurityProvider.class)
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
    
    public void testApplicationApiCreate() throws Exception {
        Response r1 = api.getApplicationApi().createFromYaml("name: test-1234\n"
            + "services: [ { type: "+TestEntity.class.getName()+" } ]");
        HttpTestUtils.assertHealthyStatusCode(r1.getStatus());
        log.info("creation result: "+r1.getEntity());
        List<ApplicationSummary> apps = api.getApplicationApi().list(null);
        log.info("apps with test: "+apps);
        Assert.assertTrue(apps.toString().contains("test-1234"), "should have had test-1234 as an app; instead: "+apps);
    }
    
    public void testApplicationApiHandledError() throws Exception {
        Response r1 = api.getApplicationApi().createFromYaml("name: test");
        Assert.assertTrue(r1.getStatus()/100 != 2, "needed an unhealthy status, not "+r1.getStatus());
        Object entity = r1.getEntity();
        Assert.assertTrue(entity.toString().indexOf("Unrecognized application blueprint format: no services defined")>=0,
            "Missing expected text in response: "+entity.toString());
    }

    public void testApplicationApiThrownError() throws Exception {
        try {
            ApplicationSummary summary = api.getApplicationApi().get("test-5678");
            Assert.fail("Should have thrown, not given: "+summary);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.assertTrue(e.toString().toLowerCase().contains("not found"),
                "Missing expected text in response: "+e.toString());
        }
    }
}
