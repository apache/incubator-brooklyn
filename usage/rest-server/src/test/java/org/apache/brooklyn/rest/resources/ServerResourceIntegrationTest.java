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

import static brooklyn.util.http.HttpTool.httpClientBuilder;
import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.http.HttpStatus;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Server;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.BrooklynRestApiLauncher;
import org.apache.brooklyn.rest.BrooklynRestApiLauncherTestFixture;
import org.apache.brooklyn.rest.security.provider.TestSecurityProvider;
import org.apache.brooklyn.test.HttpTestUtils;

import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.collect.ImmutableMap;

public class ServerResourceIntegrationTest extends BrooklynRestApiLauncherTestFixture {

    /**
     * [sam] Other tests rely on brooklyn.properties not containing security properties so ..
     * I think the best way to test this is to set a security provider, then reload properties
     * and check no authentication is required.
     * 
     * [aled] Changing this test so doesn't rely on brooklyn.properties having no security
     * provider (that can lead to failures locally when running just this test). Asserts 
     */
    @Test(groups = "Integration")
    public void testSecurityProviderUpdatesWhenPropertiesReloaded() {
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put("brooklyn.webconsole.security.users", "admin");
        brooklynProperties.put("brooklyn.webconsole.security.user.admin.password", "mypassword");
        UsernamePasswordCredentials defaultCredential = new UsernamePasswordCredentials("admin", "mypassword");

        ManagementContext mgmt = new LocalManagementContext(brooklynProperties);
        
        try {
            Server server = useServerForTest(BrooklynRestApiLauncher.launcher()
                    .managementContext(mgmt)
                    .withoutJsgui()
                    .securityProvider(TestSecurityProvider.class)
                    .start());
            String baseUri = getBaseUri(server);
    
            HttpToolResponse response;
            final URI uri = URI.create(getBaseUri() + "/v1/server/properties/reload");
            final Map<String, String> args = Collections.emptyMap();
    
            // Unauthorised when no credentials, and when default credentials.
            response = HttpTool.httpPost(httpClientBuilder().uri(baseUri).build(), uri, args, args);
            assertEquals(response.getResponseCode(), HttpStatus.SC_UNAUTHORIZED);
    
            response = HttpTool.httpPost(httpClientBuilder().uri(baseUri).credentials(defaultCredential).build(), 
                    uri, args, args);
            assertEquals(response.getResponseCode(), HttpStatus.SC_UNAUTHORIZED);

            // Accepts TestSecurityProvider credentials, and we reload.
            response = HttpTool.httpPost(httpClientBuilder().uri(baseUri).credentials(TestSecurityProvider.CREDENTIAL).build(),
                    uri, args, args);
            HttpTestUtils.assertHealthyStatusCode(response.getResponseCode());
    
            // Has no gone back to credentials from brooklynProperties; TestSecurityProvider credentials no longer work
            response = HttpTool.httpPost(httpClientBuilder().uri(baseUri).credentials(defaultCredential).build(), 
                    uri, args, args);
            HttpTestUtils.assertHealthyStatusCode(response.getResponseCode());
            
            response = HttpTool.httpPost(httpClientBuilder().uri(baseUri).credentials(TestSecurityProvider.CREDENTIAL).build(), 
                    uri, args, args);
            assertEquals(response.getResponseCode(), HttpStatus.SC_UNAUTHORIZED);
    
        } finally {
            ((ManagementContextInternal)mgmt).terminate();
        }
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
        HttpClient client = httpClientBuilder()
                .uri(getBaseUri(server))
                .credentials(TestSecurityProvider.CREDENTIAL)
                .build();
        
        HttpToolResponse response = HttpTool.httpGet(client, URI.create(getBaseUri(server) + "/v1/server/user"),
                ImmutableMap.<String, String>of());
        HttpTestUtils.assertHealthyStatusCode(response.getResponseCode());
        return response.getContentAsString();
    }

}
