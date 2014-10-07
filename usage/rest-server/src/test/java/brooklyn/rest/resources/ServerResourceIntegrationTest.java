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
import java.util.Collections;
import java.util.Map;

import org.apache.http.HttpStatus;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.management.ManagementContext;
import brooklyn.rest.BrooklynRestApiLauncher;
import brooklyn.rest.BrooklynRestApiLauncherTestFixture;
import brooklyn.rest.security.provider.AnyoneSecurityProvider;
import brooklyn.rest.security.provider.TestSecurityProvider;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

public class ServerResourceIntegrationTest extends BrooklynRestApiLauncherTestFixture {

    /**
     * [sam] Other tests rely on brooklyn.properties not containing security properties so ..
     * I think the best way to test this is to set a security provider, then reload properties
     * and check no authentication is required.
     */
    @Test(groups = "Integration")
    public void testSecurityProviderUpdatesWhenPropertiesReloaded() {
        Server server = useServerForTest(BrooklynRestApiLauncher.launcher()
                .withoutJsgui()
                .securityProvider(TestSecurityProvider.class)
                .start());
        HttpTool.HttpClientBuilder builder = HttpTool.httpClientBuilder()
                .uri(getBaseUri(server));

        HttpToolResponse response;
        final URI uri = URI.create(getBaseUri() + "/v1/server/properties/reload");
        final Map<String, String> args = Collections.emptyMap();

        // Unauthorised
        response = HttpTool.httpPost(builder.build(), uri, args, args);
        assertEquals(response.getResponseCode(), HttpStatus.SC_UNAUTHORIZED);

        response = HttpTool.httpPost(builder.credentials(TestSecurityProvider.CREDENTIAL).build(),
                uri, args, args);
        HttpTestUtils.assertHealthyStatusCode(response.getResponseCode());

        // Lack of credentials now accepted.
        response = HttpTool.httpPost(builder.build(), uri, args, args);
        HttpTestUtils.assertHealthyStatusCode(response.getResponseCode());
    }

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
                .credentials(TestSecurityProvider.CREDENTIAL)
                .build();
        HttpToolResponse response = HttpTool.httpGet(client, URI.create(getBaseUri() + "/v1/server/user"),
                ImmutableMap.<String, String>of());
        HttpTestUtils.assertHealthyStatusCode(response.getResponseCode());
        return response.getContentAsString();
    }

}
