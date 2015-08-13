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
package org.apache.brooklyn.rest.resources;

import static org.testng.Assert.assertNotNull;

import javax.ws.rs.core.MediaType;

import org.apache.http.HttpStatus;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;

import brooklyn.test.TestHttpRequestHandler;
import brooklyn.test.TestHttpServer;
import brooklyn.util.ResourceUtils;

import com.sun.jersey.api.client.UniformInterfaceException;

public class CatalogResetTest extends BrooklynRestResourceTest {

    private TestHttpServer server;
    private String serverUrl;

    @BeforeClass(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        useLocalScannedCatalog();
        super.setUp();
        server = new TestHttpServer()
            .handler("/404", new TestHttpRequestHandler().code(HttpStatus.SC_NOT_FOUND).response("Not Found"))
            .handler("/200", new TestHttpRequestHandler().response("OK"))
            .start();
        serverUrl = server.getUrl();
    }

    @Override
    protected void addBrooklynResources() {
        addResource(new CatalogResource());
    }

    @AfterClass(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        server.stop();
    }

    @Test(expectedExceptions=UniformInterfaceException.class, expectedExceptionsMessageRegExp="Client response status: 500")
    public void testConnectionError() throws Exception {
        reset("http://0.0.0.0/can-not-connect", false);
    }

    @Test
    public void testConnectionErrorIgnore() throws Exception {
        reset("http://0.0.0.0/can-not-connect", true);
    }

    @Test(expectedExceptions=UniformInterfaceException.class, expectedExceptionsMessageRegExp="Client response status: 500")
    public void testResourceMissingError() throws Exception {
        reset(serverUrl + "/404", false);
    }

    @Test
    public void testResourceMissingIgnore() throws Exception {
        reset(serverUrl + "/404", true);
    }

    @Test(expectedExceptions=UniformInterfaceException.class, expectedExceptionsMessageRegExp="Client response status: 500")
    public void testResourceInvalidError() throws Exception {
        reset(serverUrl + "/200", false);
    }

    @Test
    public void testResourceInvalidIgnore() throws Exception {
        reset(serverUrl + "/200", true);
    }

    private void reset(String bundleLocation, boolean ignoreErrors) throws Exception {
        String xml = ResourceUtils.create(this).getResourceAsString("classpath://reset-catalog.xml");
        client().resource("/v1/catalog/reset")
            .queryParam("ignoreErrors", Boolean.toString(ignoreErrors))
            .header("Content-type", MediaType.APPLICATION_XML)
            .post(xml.replace("${bundle-location}", bundleLocation));
        //if above succeeds assert catalog contents
        assertItems();
    }
    
    private void assertItems() {
        BrooklynCatalog catalog = getManagementContext().getCatalog();
        assertNotNull(catalog.getCatalogItem("brooklyn.entity.basic.BasicApplication", BrooklynCatalog.DEFAULT_VERSION));
        assertNotNull(catalog.getCatalogItem("brooklyn.osgi.tests.SimpleApplication", BrooklynCatalog.DEFAULT_VERSION));
    }

}
