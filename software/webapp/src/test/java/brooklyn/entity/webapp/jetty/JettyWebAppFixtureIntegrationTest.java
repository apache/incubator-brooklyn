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
package brooklyn.entity.webapp.jetty;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.AbstractWebAppFixtureIntegrationTest;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.entity.TestApplication;

public class JettyWebAppFixtureIntegrationTest extends AbstractWebAppFixtureIntegrationTest {

    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TestApplication jettyApp = newTestApplication();
        Jetty6Server jetty = jettyApp.createAndManageChild(EntitySpec.create(Jetty6Server.class)
                .configure(Jetty6Server.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT)));
        
        return new JavaWebAppSoftwareProcess[][] {
                new JavaWebAppSoftwareProcess[] {jetty}
        };
    }

    // to be able to test on this class in Eclipse IDE
    @Override
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void testWarDeployAndUndeploy(JavaWebAppSoftwareProcess entity, String war, String urlSubPathToWebApp,
            String urlSubPathToPageToQuery) {
        super.testWarDeployAndUndeploy(entity, war, urlSubPathToWebApp, urlSubPathToPageToQuery);
    }
    
    public static void main(String ...args) throws Exception {
        JettyWebAppFixtureIntegrationTest t = new JettyWebAppFixtureIntegrationTest();
        t.setUp();
        t.canStartAndStop((SoftwareProcess) t.basicEntities()[0][0]);
        t.shutdownApp();
        t.shutdownMgmt();
    }

}
