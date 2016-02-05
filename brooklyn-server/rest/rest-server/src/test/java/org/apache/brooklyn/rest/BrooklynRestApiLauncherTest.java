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
package org.apache.brooklyn.rest;

import static org.apache.brooklyn.rest.BrooklynRestApiLauncher.StartMode.SERVLET;
import static org.apache.brooklyn.rest.BrooklynRestApiLauncher.StartMode.WEB_XML;

import java.util.concurrent.Callable;

import org.apache.brooklyn.entity.brooklynnode.BrooklynNode;
import org.apache.brooklyn.rest.security.provider.AnyoneSecurityProvider;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.http.HttpStatus;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.Test;

public class BrooklynRestApiLauncherTest extends BrooklynRestApiLauncherTestFixture {

    @Test
    public void testServletStart() throws Exception {
        checkRestCatalogEntities(useServerForTest(baseLauncher().mode(SERVLET).start()));
    }

    @Test
    public void testWebAppStart() throws Exception {
        checkRestCatalogEntities(useServerForTest(baseLauncher().mode(WEB_XML).start()));
    }

    private BrooklynRestApiLauncher baseLauncher() {
        return BrooklynRestApiLauncher.launcher()
                .securityProvider(AnyoneSecurityProvider.class)
                .forceUseOfDefaultCatalogWithJavaClassPath(true);
    }
    
    private static void checkRestCatalogEntities(Server server) throws Exception {
        final String rootUrl = "http://localhost:"+((NetworkConnector)server.getConnectors()[0]).getLocalPort();
        int code = Asserts.succeedsEventually(new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                int code = HttpTool.getHttpStatusCode(rootUrl+"/v1/catalog/entities");
                if (code == HttpStatus.SC_FORBIDDEN) {
                    throw new RuntimeException("Retry request");
                } else {
                    return code;
                }
            }
        });
        HttpAsserts.assertHealthyStatusCode(code);
        HttpAsserts.assertContentContainsText(rootUrl+"/v1/catalog/entities", BrooklynNode.class.getSimpleName());
    }
    
}
