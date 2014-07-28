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

import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import brooklyn.rest.security.provider.AnyoneSecurityProvider;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.time.Time;

public class BrooklynPropertiesSecurityFilterTest extends BrooklynRestApiLauncherTestFixture {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynPropertiesSecurityFilterTest.class);

    // Would be great for this to be a unit test but it takes almost ten seconds.
    @Test(groups = "Integration")
    public void testInteractionOfSecurityFilterAndFormMapProvider() throws Exception {
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            Server server = useServerForTest(BrooklynRestApiLauncher.launcher()
                    .securityProvider(AnyoneSecurityProvider.class)
                    .forceUseOfDefaultCatalogWithJavaClassPath(true)
                    .withoutJsgui()
                    .start());
            String appId = startAppAtNode(server);
            String entityId = getTestEntityInApp(server, appId);
            HttpClient client = HttpTool.httpClientBuilder()
                    .uri(getBaseUri(server))
                    .build();
            List<? extends NameValuePair> nvps = Lists.newArrayList(
                    new BasicNameValuePair("arg", "bar"));
            String effector = String.format("/v1/applications/%s/entities/%s/effectors/identityEffector", appId, entityId);
            HttpToolResponse response = HttpTool.httpPost(client, URI.create(getBaseUri() + effector),
                    ImmutableMap.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_FORM_URLENCODED.getMimeType()),
                    URLEncodedUtils.format(nvps, Charsets.UTF_8).getBytes());

            LOG.info("Effector response: {}", response.getContentAsString());
            assertTrue(HttpTool.isStatusCodeHealthy(response.getResponseCode()), "response code=" + response.getResponseCode());
        } finally {
            LOG.info("testInteractionOfSecurityFilterAndFormMapProvider complete in " + Time.makeTimeStringRounded(stopwatch));
        }
    }

    private String startAppAtNode(Server server) throws Exception {
        String blueprint = "name: TestApp\n" +
                "location: localhost\n" +
                "services:\n" +
                "- type: brooklyn.test.entity.TestEntity";
        HttpClient client = HttpTool.httpClientBuilder()
                .uri(getBaseUri(server))
                .build();
        HttpToolResponse response = HttpTool.httpPost(client, URI.create(getBaseUri() + "/v1/applications"),
                ImmutableMap.of(HttpHeaders.CONTENT_TYPE, "application/x-yaml"),
                blueprint.getBytes());
        assertTrue(HttpTool.isStatusCodeHealthy(response.getResponseCode()), "error creating app. response code=" + response.getResponseCode());
        Map<String, Object> body = new ObjectMapper().readValue(response.getContent(), HashMap.class);
        return (String) body.get("entityId");
    }

    private String getTestEntityInApp(Server server, String appId) throws Exception {
        HttpClient client = HttpTool.httpClientBuilder()
                .uri(getBaseUri(server))
                .build();
        List entities = new ObjectMapper().readValue(
                HttpTool.httpGet(client, URI.create(getBaseUri() + "/v1/applications/" + appId + "/entities"), MutableMap.<String, String>of()).getContent(), List.class);
        LOG.info((String) ((Map) entities.get(0)).get("id"));
        return (String) ((Map) entities.get(0)).get("id");
    }
}
