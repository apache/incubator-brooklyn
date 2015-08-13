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
package org.apache.brooklyn.entity.webapp.jetty;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.entity.webapp.AbstractWebAppFixtureIntegrationTest;
import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import org.apache.brooklyn.test.entity.TestApplication;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.basic.SoftwareProcess;
import org.apache.brooklyn.location.basic.PortRanges;

public class JettyWebAppFixtureIntegrationTest extends AbstractWebAppFixtureIntegrationTest {

    // FIXME Fails with this is in the jetty log:
    //     Caused by: java.lang.ClassNotFoundException: mx4j.tools.adaptor.http.HttpAdaptor

    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void canStartAndStop(final SoftwareProcess entity) {
        super.canStartAndStop(entity);
    }
    
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
