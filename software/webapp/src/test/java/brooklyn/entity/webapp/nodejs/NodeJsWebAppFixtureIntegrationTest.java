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
package brooklyn.entity.webapp.nodejs;

import org.testng.annotations.DataProvider;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.AbstractWebAppFixtureIntegrationTest;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.location.basic.PortRanges;
import brooklyn.test.entity.TestApplication;

public class NodeJsWebAppFixtureIntegrationTest extends AbstractWebAppFixtureIntegrationTest {

    public static final String GIT_REPO_URL = "https://github.com/grkvlt/node-hello-world.git";
    public static final String APP_FILE = "app.js";
    public static final String APP_NAME = "node-hello-world";

    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TestApplication nodejsApp = newTestApplication();
        NodeJsWebAppService nodejs = nodejsApp.createAndManageChild(EntitySpec.create(NodeJsWebAppService.class)
                .configure(NodeJsWebAppService.HTTP_PORT, PortRanges.fromString(DEFAULT_HTTP_PORT))
                .configure("gitRepoUrl", GIT_REPO_URL)
                .configure("appFileName", APP_FILE)
                .configure("appName", APP_NAME));

        return new WebAppService[][] {
                new WebAppService[] { nodejs }
        };
    }

    public static void main(String ...args) throws Exception {
        NodeJsWebAppFixtureIntegrationTest t = new NodeJsWebAppFixtureIntegrationTest();
        t.setUp();
        t.testReportsServiceDownWhenKilled((SoftwareProcess) t.basicEntities()[0][0]);
        t.shutdownApp();
        t.shutdownMgmt();
    }

}
