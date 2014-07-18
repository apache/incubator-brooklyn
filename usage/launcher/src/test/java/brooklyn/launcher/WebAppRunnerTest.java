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
package brooklyn.launcher;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.HttpTestUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;

import com.google.common.collect.Lists;


/**
 * These tests require the brooklyn.war to work. (Should be placed by maven build.)
 */
public class WebAppRunnerTest {

    public static final Logger log = LoggerFactory.getLogger(WebAppRunnerTest.class);
            
    List<LocalManagementContext> managementContexts = Lists.newCopyOnWriteArrayList();
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (LocalManagementContext managementContext : managementContexts) {
            Entities.destroyAll(managementContext);
        }
        managementContexts.clear();
    }
    
    LocalManagementContext newManagementContext(BrooklynProperties brooklynProperties) {
        LocalManagementContext result = new LocalManagementContext(brooklynProperties);
        managementContexts.add(result);
        return result;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    BrooklynWebServer createWebServer(Map properties) {
        Map bigProps = MutableMap.copyOf(properties);
        Map attributes = MutableMap.copyOf( (Map) bigProps.get("attributes") );
        bigProps.put("attributes", attributes);

        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.putAll(bigProps);
        brooklynProperties.put("brooklyn.webconsole.security.provider","brooklyn.rest.security.provider.AnyoneSecurityProvider");
        brooklynProperties.put("brooklyn.webconsole.security.https.required","false");
        return new BrooklynWebServer(bigProps, newManagementContext(brooklynProperties));
    }
    
    @Test
    public void testStartWar1() throws Exception {
        if (!Networking.isPortAvailable(8090))
            fail("Another process is using port 8090 which is required for this test.");
        BrooklynWebServer server = createWebServer(MutableMap.of("port", 8090));
        assertNotNull(server);
        
        try {
            server.start();
            assertBrooklynEventuallyAt("http://localhost:8090/");
        } finally {
            server.stop();
        }
    }

    public static void assertBrooklynEventuallyAt(String url) {
        HttpTestUtils.assertContentEventuallyContainsText(url, "Brooklyn Web Console");
    }
    
    @Test
    public void testStartSecondaryWar() throws Exception {
        if (!Networking.isPortAvailable(8090))
            fail("Another process is using port 8090 which is required for this test.");
        BrooklynWebServer server = createWebServer(
            MutableMap.of("port", 8090, "war", "brooklyn.war", "wars", MutableMap.of("hello", "hello-world.war")) );
        assertNotNull(server);
        
        try {
            server.start();

            assertBrooklynEventuallyAt("http://localhost:8090/");
            HttpTestUtils.assertContentEventuallyContainsText("http://localhost:8090/hello",
                "This is the home page for a sample application");

        } finally {
            server.stop();
        }
    }

    @Test
    public void testStartSecondaryWarAfter() throws Exception {
        if (!Networking.isPortAvailable(8090))
            fail("Another process is using port 8090 which is required for this test.");
        BrooklynWebServer server = createWebServer(MutableMap.of("port", 8090, "war", "brooklyn.war"));
        assertNotNull(server);
        
        try {
            server.start();
            server.deploy("/hello", "hello-world.war");

            assertBrooklynEventuallyAt("http://localhost:8090/");
            HttpTestUtils.assertContentEventuallyContainsText("http://localhost:8090/hello",
                "This is the home page for a sample application");

        } finally {
            server.stop();
        }
    }

    @Test
    public void testStartWithLauncher() throws Exception {
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .brooklynProperties("brooklyn.webconsole.security.provider","brooklyn.rest.security.provider.AnyoneSecurityProvider")
                .webapp("/hello", "hello-world.war")
                .start();
        BrooklynServerDetails details = launcher.getServerDetails();
        
        try {
            details.getWebServer().deploy("/hello2", "hello-world.war");

            assertBrooklynEventuallyAt(details.getWebServerUrl());
            HttpTestUtils.assertContentEventuallyContainsText(details.getWebServerUrl()+"hello", "This is the home page for a sample application");
            HttpTestUtils.assertContentEventuallyContainsText(details.getWebServerUrl()+"hello2", "This is the home page for a sample application");
            HttpTestUtils.assertHttpStatusCodeEventuallyEquals(details.getWebServerUrl()+"hello0", 404);

        } finally {
            details.getWebServer().stop();
            ((ManagementContextInternal)details.getManagementContext()).terminate();
        }
    }
    
}
