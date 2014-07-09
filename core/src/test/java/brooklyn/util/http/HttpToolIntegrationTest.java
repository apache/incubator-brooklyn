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
package brooklyn.util.http;

import static org.testng.Assert.assertTrue;

import java.net.URI;

import org.apache.http.client.HttpClient;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.basic.PortRanges;
import brooklyn.test.HttpService;

import com.google.common.collect.ImmutableMap;

public class HttpToolIntegrationTest {

    // TODO Expand test coverage for credentials etc
    
    private HttpService httpService;
    private HttpService httpsService;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        httpService = new HttpService(PortRanges.fromString("9000+"), false).start();
        httpsService = new HttpService(PortRanges.fromString("9000+"), true).start();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (httpService != null) httpService.shutdown();
        if (httpsService != null) httpsService.shutdown();
    }
    
    @Test(groups = {"Integration"})
    public void testHttpGet() throws Exception {
        URI baseUri = new URI(httpService.getUrl());

        HttpClient client = HttpTool.httpClientBuilder().build();
        HttpToolResponse result = HttpTool.httpGet(client, baseUri, ImmutableMap.<String,String>of());
        assertTrue(new String(result.getContent()).contains("Hello, World"), "val="+new String(result.getContent()));
    }
    
    @Test(groups = {"Integration"})
    public void testHttpRedirect() throws Exception {
        URI baseUri = new URI(httpService.getUrl() + "hello/redirectAbsolute");

        HttpClient client = HttpTool.httpClientBuilder().laxRedirect(true).build();
        HttpToolResponse result = HttpTool.httpGet(client, baseUri, ImmutableMap.<String,String>of());
        assertTrue(new String(result.getContent()).contains("Hello, World"), "val="+new String(result.getContent()));
    }
    
    @Test(groups = {"Integration"})
    public void testHttpPost() throws Exception {
        URI baseUri = new URI(httpService.getUrl());

        HttpClient client = HttpTool.httpClientBuilder().build();
        HttpToolResponse result = HttpTool.httpPost(client, baseUri, ImmutableMap.<String,String>of(), new byte[0]);
        assertTrue(new String(result.getContent()).contains("Hello, World"), "val="+new String(result.getContent()));
    }
    
    @Test(groups = {"Integration"})
    public void testHttpsGetWithTrustAll() throws Exception {
        URI baseUri = new URI(httpsService.getUrl());

        HttpClient client = HttpTool.httpClientBuilder().https(true).trustAll().build();
        HttpToolResponse result = HttpTool.httpGet(client, baseUri, ImmutableMap.<String,String>of());
        assertTrue(new String(result.getContent()).contains("Hello, World"), "val="+new String(result.getContent()));
    }
    
    @Test(groups = {"Integration"})
    public void testHttpsPostWithTrustSelfSigned() throws Exception {
        URI baseUri = new URI(httpsService.getUrl());

        HttpClient client = HttpTool.httpClientBuilder().https(true).trustSelfSigned().build();
        HttpToolResponse result = HttpTool.httpPost(client, baseUri, ImmutableMap.<String,String>of(), new byte[0]);
        assertTrue(new String(result.getContent()).contains("Hello, World"), "val="+new String(result.getContent()));
    }
}
