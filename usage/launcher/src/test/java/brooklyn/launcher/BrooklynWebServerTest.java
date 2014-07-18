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

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class BrooklynWebServerTest {

    public static final Logger log = LoggerFactory.getLogger(BrooklynWebServer.class);

    private BrooklynProperties brooklynProperties;
    private BrooklynWebServer webServer;
    private List<LocalManagementContext> managementContexts = Lists.newCopyOnWriteArrayList();
    
    @BeforeMethod(alwaysRun=true)
    public void setUp(){
        brooklynProperties = BrooklynProperties.Factory.newDefault();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (LocalManagementContext managementContext : managementContexts) {
            Entities.destroyAll(managementContext);
        }
        managementContexts.clear();
        if (webServer != null) webServer.stop();
    }
    
    private LocalManagementContext newManagementContext(BrooklynProperties brooklynProperties) {
        LocalManagementContext result = new LocalManagementContextForTests(brooklynProperties);
        managementContexts.add(result);
        return result;
    }
    
    @Test
    public void verifyHttp() throws Exception {
        webServer = new BrooklynWebServer(newManagementContext(brooklynProperties));
        try {
            webServer.start();
    
            HttpToolResponse response = HttpTool.execAndConsume(new DefaultHttpClient(), new HttpGet(webServer.getRootUrl()));
            assertEquals(response.getResponseCode(), 200);
        } finally {
            webServer.stop();
        }
    }

    @Test
    public void verifyHttps() throws Exception {
        Map<String,?> flags = ImmutableMap.<String,Object>builder()
                .put("httpsEnabled", true)
                .put("keystorePath", getFile("server.ks"))
                .put("keystorePassword", "password")
                .build();
        webServer = new BrooklynWebServer(flags, newManagementContext(brooklynProperties));
        webServer.start();
        
        try {
            KeyStore keyStore = load("client.ks", "password");
            KeyStore trustStore = load("client.ts", "password");
            SSLSocketFactory socketFactory = new SSLSocketFactory(SSLSocketFactory.TLS, keyStore, "password", trustStore, (SecureRandom)null, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpToolResponse response = HttpTool.execAndConsume(
                    HttpTool.httpClientBuilder()
                            .port(webServer.getActualPort())
                            .https(true)
                            .socketFactory(socketFactory)
                            .build(),
                    new HttpGet(webServer.getRootUrl()));
            assertEquals(response.getResponseCode(), 200);
        } finally {
            webServer.stop();
        }
    }

    @Test
    public void verifyHttpsFromConfig() throws Exception {
        brooklynProperties.put(BrooklynWebConfig.HTTPS_REQUIRED, true);
        brooklynProperties.put(BrooklynWebConfig.KEYSTORE_URL, getFile("server.ks"));
        brooklynProperties.put(BrooklynWebConfig.KEYSTORE_PASSWORD, "password");
        webServer = new BrooklynWebServer(MutableMap.of(), newManagementContext(brooklynProperties));
        webServer.start();
        
        try {
            KeyStore keyStore = load("client.ks", "password");
            KeyStore trustStore = load("client.ts", "password");
            SSLSocketFactory socketFactory = new SSLSocketFactory(SSLSocketFactory.TLS, keyStore, "password", trustStore, (SecureRandom)null, SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            HttpToolResponse response = HttpTool.execAndConsume(
                    HttpTool.httpClientBuilder()
                            .port(webServer.getActualPort())
                            .https(true)
                            .socketFactory(socketFactory)
                            .build(),
                    new HttpGet(webServer.getRootUrl()));
            assertEquals(response.getResponseCode(), 200);
        } finally {
            webServer.stop();
        }
    }

    private KeyStore load(String name, String password) throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        FileInputStream instream = new FileInputStream(new File(getFile(name)));
        keystore.load(instream, password.toCharArray());
        return keystore;
    }

    private String getFile(String file) {
        // this works because both IDE and Maven run tests with classes/resources on the file system
        return new File(getClass().getResource("/" + file).getFile()).getAbsolutePath();
    }
}
