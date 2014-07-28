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
package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;

import java.net.URI;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import brooklyn.rest.BrooklynRestApiLauncher;
import brooklyn.rest.BrooklynRestApiLauncherTestFixture;
import brooklyn.rest.security.provider.TestSecurityProvider;
import brooklyn.test.HttpTestUtils;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

public class ServerResourceIntegrationTest extends BrooklynRestApiLauncherTestFixture {

    @Test(groups = "Integration")
    public void testGetUser() throws Exception {
        Server server = useServerForTest(BrooklynRestApiLauncher.launcher()
                .securityProvider(TestSecurityProvider.class)
                .withoutJsgui()
                .start());
        assertEquals(getServerUser(server), TestSecurityProvider.USER);
    }

    private String getServerUser(Server server) throws Exception {
        HttpClient client = HttpTool.httpClientBuilder()
                .uri(getBaseUri(server))
                .credentials(new UsernamePasswordCredentials(TestSecurityProvider.USER, TestSecurityProvider.PASSWORD))
                .build();
        HttpToolResponse response = HttpTool.httpGet(client, URI.create(getBaseUri() + "/v1/server/user"),
                ImmutableMap.<String, String>of());
        HttpTestUtils.assertHealthyStatusCode(response.getResponseCode());
        return response.getContentAsString();
    }

}
