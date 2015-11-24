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

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.location.BasicLocationRegistry;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.rest.BrooklynRestApiLauncher;
import org.apache.brooklyn.rest.BrooklynRestApiLauncherTest;
import org.apache.brooklyn.rest.domain.ApplicationSummary;
import org.apache.brooklyn.rest.domain.CatalogLocationSummary;
import org.apache.brooklyn.rest.security.provider.TestSecurityProvider;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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

        api = BrooklynApi.newInstance("http://localhost:" + ((NetworkConnector)server.getConnectors()[0]).getPort() + "/",
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

    public void testCatalogCreate()throws Exception {
        final Response response = api.getCatalogApi().create(getFileContentsAsString("catalog/test-catalog.bom"));
        Asserts.assertEquals(response.getStatus(), 201);
        Asserts.assertStringContains(String.valueOf(response.getEntity()), "simple-tomcat:1.0");
    }



    public void testApplicationApiList() throws Exception {
        List<ApplicationSummary> apps = api.getApplicationApi().list(null);
        log.info("apps are: "+apps);
    }

    public void testApplicationApiCreate() throws Exception {
        Response r1 = api.getApplicationApi().createFromYaml("name: test-1234\n"
            + "services: [ { type: "+TestEntity.class.getName()+" } ]");
        HttpAsserts.assertHealthyStatusCode(r1.getStatus());
        log.info("creation result: "+r1.getEntity());
        List<ApplicationSummary> apps = api.getApplicationApi().list(null);
        log.info("apps with test: "+apps);
        Asserts.assertStringContains(apps.toString(), "test-1234");
    }

    public void testApplicationApiHandledError() throws Exception {
        Response r1 = api.getApplicationApi().createFromYaml("name: test");
        HttpAsserts.assertNotHealthyStatusCode(r1.getStatus());
        // new-style messages first, old-style messages after (during switch to TypePlanTransformer)
        Asserts.assertStringContainsAtLeastOne(r1.getEntity().toString().toLowerCase(),
            "invalid plan", "no services");
        Asserts.assertStringContainsAtLeastOne(r1.getEntity().toString().toLowerCase(),
            "format could not be recognized", "Unrecognized application blueprint format");
    }

    public void testApplicationApiThrownError() throws Exception {
        try {
            ApplicationSummary summary = api.getApplicationApi().get("test-5678");
            Asserts.shouldHaveFailedPreviously("got "+summary);
        } catch (Exception e) {
            Asserts.expectedFailureContainsIgnoreCase(e, "404", "not found");
        }
    }

    private String getFileContentsAsString(final String filename) throws Exception {
        final URL resource = getClass().getClassLoader().getResource(filename);
        Asserts.assertNotNull(resource);
        return new String(Files.readAllBytes(Paths.get(resource.toURI())), Charset.defaultCharset());
    }
}
