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

import static brooklyn.entity.webapp.nodejs.NodeJsWebAppFixtureIntegrationTest.*;
import static org.testng.Assert.assertNotNull;

import org.testng.annotations.Test;

import brooklyn.entity.AbstractSoftlayerLiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;

import com.google.common.collect.ImmutableList;

/**
 * A simple test of installing+running on Softlayer, using various OS distros and versions.
 */
public class NodeJsWebAppSoftlayerLiveTest extends AbstractSoftlayerLiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        final NodeJsWebAppService server = app.createAndManageChild(EntitySpec.create(NodeJsWebAppService.class)
                .configure("gitRepoUrl", GIT_REPO_URL)
                .configure("appFileName", APP_FILE)
                .configure("appName", APP_NAME));

        app.start(ImmutableList.of(loc));

        String url = server.getAttribute(NodeJsWebAppService.ROOT_URL);

        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentContainsText(url, "Hello");

        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertNotNull(server.getAttribute(NodeJsWebAppService.REQUEST_COUNT));
                assertNotNull(server.getAttribute(NodeJsWebAppService.ERROR_COUNT));
                assertNotNull(server.getAttribute(NodeJsWebAppService.TOTAL_PROCESSING_TIME));
            }});
    }

    @Test(groups = {"Live", "Live-sanity"})
    @Override
    public void test_Ubuntu_12_0_4() throws Exception {
        super.test_Ubuntu_12_0_4();
    }
}
