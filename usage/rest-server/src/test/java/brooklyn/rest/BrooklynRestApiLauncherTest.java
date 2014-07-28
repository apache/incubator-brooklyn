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
package brooklyn.rest;

import static brooklyn.rest.BrooklynRestApiLauncher.StartMode.*;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.Test;

import brooklyn.rest.security.provider.AnyoneSecurityProvider;
import brooklyn.rest.util.BrooklynRestResourceUtilsTest.SampleNoOpApplication;
import brooklyn.test.HttpTestUtils;

public class BrooklynRestApiLauncherTest extends BrooklynRestApiLauncherTestFixture {

    @Test
    public void testFilterStart() throws Exception {
        checkRestCatalogApplications(useServerForTest(baseLauncher().mode(FILTER).start()));
    }

    @Test
    public void testServletStart() throws Exception {
        checkRestCatalogApplications(useServerForTest(baseLauncher().mode(SERVLET).start()));
    }

    @Test
    public void testWebAppStart() throws Exception {
        checkRestCatalogApplications(useServerForTest(baseLauncher().mode(WEB_XML).start()));
    }

    private BrooklynRestApiLauncher baseLauncher() {
        return BrooklynRestApiLauncher.launcher()
                .securityProvider(AnyoneSecurityProvider.class)
                .forceUseOfDefaultCatalogWithJavaClassPath(true);
    }
    
    private static void checkRestCatalogApplications(Server server) throws Exception {
        String rootUrl = "http://localhost:"+server.getConnectors()[0].getLocalPort();
        HttpTestUtils.assertHealthyStatusCode(
                HttpTestUtils.getHttpStatusCode(rootUrl+"/v1/catalog/applications"));
        HttpTestUtils.assertContentContainsText(rootUrl+"/v1/catalog/applications", SampleNoOpApplication.class.getSimpleName());
    }
    
}
