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
import javax.ws.rs.core.Response;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.typereg.BrooklynTypeRegistry;
import org.apache.brooklyn.test.http.TestHttpRequestHandler;
import org.apache.brooklyn.test.http.TestHttpServer;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.http.HttpTool;
import static org.testng.Assert.assertEquals;

@Test( // by using a different suite name we disallow interleaving other tests between the methods of this test class, which wrecks the test fixtures
    suiteName = "CatalogResetTest")
public class CatalogResetTest extends BrooklynRestResourceTest {

    private TestHttpServer server;
    private String serverUrl;

    @BeforeClass(alwaysRun=true)
    public void setUp() throws Exception {
        server = new TestHttpServer()
            .handler("/404", new TestHttpRequestHandler().code(Response.Status.NOT_FOUND.getStatusCode()).response("Not Found"))
            .handler("/200", new TestHttpRequestHandler().response("OK"))
            .start();
        serverUrl = server.getUrl();
    }

    @Override
    protected boolean useLocalScannedCatalog() {
        return true;
    }

    @AfterClass(alwaysRun=true)
    public void tearDown() throws Exception {
        if (server != null)
            server.stop();
    }

    @Test
    public void testConnectionError() throws Exception {
        Response response = reset("http://0.0.0.0/can-not-connect", false);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testConnectionErrorIgnore() throws Exception {
        reset("http://0.0.0.0/can-not-connect", true);
    }

    @Test
    public void testResourceMissingError() throws Exception {
        Response response = reset(serverUrl + "/404", false);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testResourceMissingIgnore() throws Exception {
        reset(serverUrl + "/404", true);
    }

    @Test
    public void testResourceInvalidError() throws Exception {
        Response response = reset(serverUrl + "/200", false);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void testResourceInvalidIgnore() throws Exception {
        reset(serverUrl + "/200", true);
    }

    private Response reset(String bundleLocation, boolean ignoreErrors) throws Exception {
        String xml = ResourceUtils.create(this).getResourceAsString("classpath://reset-catalog.xml");
        Response response = client().path("/catalog/reset")
            .query("ignoreErrors", Boolean.toString(ignoreErrors))
            .header("Content-type", MediaType.APPLICATION_XML)
            .post(xml.replace("${bundle-location}", bundleLocation));

        //if above succeeds assert catalog contents
        if (HttpTool.isStatusCodeHealthy(response.getStatus()))
            assertItems();
        
        return response;
    }
    
    private void assertItems() {
        BrooklynTypeRegistry types = getManagementContext().getTypeRegistry();
        assertNotNull(types.get("org.apache.brooklyn.entity.stock.BasicApplication", BrooklynCatalog.DEFAULT_VERSION));
        assertNotNull(types.get("org.apache.brooklyn.test.osgi.entities.SimpleApplication", BrooklynCatalog.DEFAULT_VERSION));
    }

}
