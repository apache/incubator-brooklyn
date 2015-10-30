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
package org.apache.brooklyn.rest.jsgui;

import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.HttpTestUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.server.BrooklynServiceAttributes;
import org.apache.brooklyn.rest.BrooklynRestApiLauncherTestFixture;
import org.eclipse.jetty.server.NetworkConnector;

/** Convenience and demo for launching programmatically. */
public class BrooklynJavascriptGuiLauncherTest {

    Server server = null;
    
    @AfterMethod(alwaysRun=true)
    public void stopServer() throws Exception {
        if (server!=null) {
            ManagementContext mgmt = getManagementContextFromJettyServerAttributes(server);
            server.stop();
            if (mgmt!=null) Entities.destroyAll(mgmt);
            server = null;
        }
    }
    
    @Test
    public void testJavascriptWithoutRest() throws Exception {
        server = BrooklynJavascriptGuiLauncher.startJavascriptWithoutRest();
        checkUrlContains("/index.html", "Brooklyn");
    }

    @Test
    public void testJavascriptWithRest() throws Exception {
        server = BrooklynJavascriptGuiLauncher.startJavascriptAndRest();
        BrooklynRestApiLauncherTestFixture.forceUseOfDefaultCatalogWithJavaClassPath(server);
        BrooklynRestApiLauncherTestFixture.enableAnyoneLogin(server);
        checkEventuallyHealthy();
        checkUrlContains("/index.html", "Brooklyn");
        checkUrlContains("/v1/catalog/entities", "Tomcat");
    }

    protected void checkUrlContains(final String path, final String text) {
        //Server may return 403 until it loads completely, wait a bit
        //until it stabilizes.
        HttpTestUtils.assertContentEventuallyContainsText(rootUrl()+path, text);
    }

    protected void checkEventuallyHealthy() {
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(rootUrl(), 200);
    }

    protected String rootUrl() {
        return "http://localhost:"+((NetworkConnector)server.getConnectors()[0]).getLocalPort();
    }

    private ManagementContext getManagementContextFromJettyServerAttributes(Server server) {
        return (ManagementContext) ((ContextHandler)server.getHandler()).getAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT);
    }

}
