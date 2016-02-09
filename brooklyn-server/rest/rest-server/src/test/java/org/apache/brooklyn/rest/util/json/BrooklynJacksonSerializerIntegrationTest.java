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
package org.apache.brooklyn.rest.util.json;

import java.io.NotSerializableException;
import java.net.URI;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.rest.BrooklynRestApiLauncher;
import org.apache.brooklyn.rest.util.json.BrooklynJacksonSerializerTest.SelfRefNonSerializableClass;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URIBuilder;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

public class BrooklynJacksonSerializerIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynJacksonSerializerIntegrationTest.class);
    
    // Ensure TEXT_PLAIN just returns toString for ManagementContext instance.
    // Strangely, testWithLauncherSerializingListsContainingEntitiesAndOtherComplexStuff ended up in the 
    // EntityConfigResource.getPlain code, throwing a ClassCastException.
    // 
    // TODO This tests the fix for that ClassCastException, but does not explain why 
    // testWithLauncherSerializingListsContainingEntitiesAndOtherComplexStuff was calling it.
    @Test(groups="Integration") //because of time
    public void testWithAcceptsPlainText() throws Exception {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        Server server = null;
        try {
            server = BrooklynRestApiLauncher.launcher().managementContext(mgmt).start();
            HttpClient client = HttpTool.httpClientBuilder().build();

            TestApplication app = TestApplication.Factory.newManagedInstanceForTests(mgmt);

            String serverAddress = "http://localhost:"+((NetworkConnector)server.getConnectors()[0]).getLocalPort();
            String appUrl = serverAddress + "/v1/applications/" + app.getId();
            String entityUrl = appUrl + "/entities/" + app.getId();
            URI configUri = new URIBuilder(entityUrl + "/config/" + TestEntity.CONF_OBJECT.getName())
                    .addParameter("raw", "true")
                    .build();

            // assert config here is just mgmt.toString()
            app.config().set(TestEntity.CONF_OBJECT, mgmt);
            String content = get(client, configUri, ImmutableMap.of("Accept", MediaType.TEXT_PLAIN));
            log.info("CONFIG MGMT is:\n"+content);
            Assert.assertEquals(content, mgmt.toString(), "content="+content);
            
        } finally {
            try {
                if (server != null) server.stop();
            } catch (Exception e) {
                log.warn("failed to stop server: "+e);
            }
            Entities.destroyAll(mgmt);
        }
    }
        
    @Test(groups="Integration") //because of time
    public void testWithLauncherSerializingListsContainingEntitiesAndOtherComplexStuff() throws Exception {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        Server server = null;
        try {
            server = BrooklynRestApiLauncher.launcher().managementContext(mgmt).start();
            HttpClient client = HttpTool.httpClientBuilder().build();

            TestApplication app = TestApplication.Factory.newManagedInstanceForTests(mgmt);

            String serverAddress = "http://localhost:"+((NetworkConnector)server.getConnectors()[0]).getLocalPort();
            String appUrl = serverAddress + "/v1/applications/" + app.getId();
            String entityUrl = appUrl + "/entities/" + app.getId();
            URI configUri = new URIBuilder(entityUrl + "/config/" + TestEntity.CONF_OBJECT.getName())
                    .addParameter("raw", "true")
                    .build();

            // assert config here is just mgmt
            app.config().set(TestEntity.CONF_OBJECT, mgmt);
            String content = get(client, configUri, ImmutableMap.of("Accept", MediaType.APPLICATION_JSON));
            log.info("CONFIG MGMT is:\n"+content);
            @SuppressWarnings("rawtypes")
            Map values = new Gson().fromJson(content, Map.class);
            Assert.assertEquals(values, ImmutableMap.of("type", LocalManagementContextForTests.class.getCanonicalName()), "values="+values);

            // assert normal API returns the same, containing links
            content = get(client, entityUrl, ImmutableMap.of("Accept", MediaType.APPLICATION_JSON));
            log.info("ENTITY is: \n"+content);
            values = new Gson().fromJson(content, Map.class);
            Assert.assertTrue(values.size()>=3, "Map is too small: "+values);
            Assert.assertTrue(values.size()<=6, "Map is too big: "+values);
            Assert.assertEquals(values.get("type"), TestApplication.class.getCanonicalName(), "values="+values);
            Assert.assertNotNull(values.get("links"), "Map should have contained links: values="+values);

            // but config etc returns our nicely json serialized
            app.config().set(TestEntity.CONF_OBJECT, app);
            content = get(client, configUri, ImmutableMap.of("Accept", MediaType.APPLICATION_JSON));
            log.info("CONFIG ENTITY is:\n"+content);
            values = new Gson().fromJson(content, Map.class);
            Assert.assertEquals(values, ImmutableMap.of("type", Entity.class.getCanonicalName(), "id", app.getId()), "values="+values);

            // and self-ref gives error + toString
            SelfRefNonSerializableClass angry = new SelfRefNonSerializableClass();
            app.config().set(TestEntity.CONF_OBJECT, angry);
            content = get(client, configUri, ImmutableMap.of("Accept", MediaType.APPLICATION_JSON));
            log.info("CONFIG ANGRY is:\n"+content);
            assertErrorObjectMatchingToString(content, angry);
            
            // as does Server
            app.config().set(TestEntity.CONF_OBJECT, server);
            content = get(client, configUri, ImmutableMap.of("Accept", MediaType.APPLICATION_JSON));
            // NOTE, if using the default visibility / object mapper, the getters of the object are invoked
            // resulting in an object which is huge, 7+MB -- and it wreaks havoc w eclipse console regex parsing!
            // (but with our custom VisibilityChecker server just gives us the nicer error!)
            log.info("CONFIG SERVER is:\n"+content);
            assertErrorObjectMatchingToString(content, server);
            Assert.assertTrue(content.contains(NotSerializableException.class.getCanonicalName()), "server should have contained things which are not serializable");
            Assert.assertTrue(content.length() < 1024, "content should not have been very long; instead was: "+content.length());
            
        } finally {
            try {
                if (server != null) server.stop();
            } catch (Exception e) {
                log.warn("failed to stop server: "+e);
            }
            Entities.destroyAll(mgmt);
        }
    }

    private void assertErrorObjectMatchingToString(String content, Object expected) {
        Object value = new Gson().fromJson(content, Object.class);
        Assert.assertTrue(value instanceof Map, "Expected map, got: "+value);
        Assert.assertEquals(((Map<?,?>)value).get("toString"), expected.toString());
    }

    private String get(HttpClient client, String uri, Map<String, String> headers) {
        return get(client, URI.create(uri), headers);
    }

    private String get(HttpClient client, URI uri, Map<String, String> headers) {
        return HttpTool.httpGet(client, uri, headers).getContentAsString();
    }
}
