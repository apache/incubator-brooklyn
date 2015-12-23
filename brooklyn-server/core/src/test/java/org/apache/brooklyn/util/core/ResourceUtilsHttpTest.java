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
package org.apache.brooklyn.util.core;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.IOException;
import java.io.InputStream;

import org.apache.brooklyn.test.http.TestHttpRequestHandler;
import org.apache.brooklyn.test.http.TestHttpServer;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ResponseServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ResourceUtilsHttpTest {
    private ResourceUtils utils;
    private TestHttpServer server;
    private String baseUrl;

    @BeforeClass(alwaysRun=true)
    public void setUp() throws Exception {
        utils = ResourceUtils.create(this, "mycontext");
        server = new TestHttpServer()
            .interceptor(new ResponseServer())
            .interceptor(new ResponseBasicUnauthorized())
            .interceptor(new RequestBasicAuth())
            .handler("/simple", new TestHttpRequestHandler().response("OK"))
            .handler("/empty", new TestHttpRequestHandler().code(HttpStatus.SC_NO_CONTENT))
            .handler("/missing", new TestHttpRequestHandler().code(HttpStatus.SC_NOT_FOUND).response("Missing"))
            .handler("/redirect", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "/simple"))
            .handler("/cycle", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "/cycle"))
            .handler("/secure", new TestHttpRequestHandler().code(HttpStatus.SC_MOVED_TEMPORARILY).response("Redirect").header("Location", "https://0.0.0.0/"))
            .handler("/auth", new AuthHandler("test", "test", "OK"))
            .handler("/auth_escape", new AuthHandler("test@me:/", "test", "OK"))
            .handler("/auth_escape2", new AuthHandler("test@me:test", "", "OK"))
            .handler("/no_credentials", new CheckNoCredentials())
            .start();
        baseUrl = server.getUrl();
    }

    @AfterClass(alwaysRun=true)
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void testGet() throws Exception {
        InputStream stream = utils.getResourceFromUrl(baseUrl + "/simple");
        assertEquals(Streams.readFullyString(stream), "OK");
    }

    @Test
    public void testGetEmpty() throws Exception {
        InputStream stream = utils.getResourceFromUrl(baseUrl + "/empty");
        assertEquals(Streams.readFullyString(stream), "");
    }

    @Test
    public void testGetProtected() throws Exception {
        String url = baseUrl.replace("http://", "http://test:test@") + "/auth";
        InputStream stream = utils.getResourceFromUrl(url);
        assertEquals(Streams.readFullyString(stream), "OK");
    }

    @Test
    public void testGetProtectedEscape() throws Exception {
        String url = baseUrl.replace("http://", "http://test%40me%3A%2F:test@") + "/auth_escape";
        InputStream stream = utils.getResourceFromUrl(url);
        assertEquals(Streams.readFullyString(stream), "OK");
    }

    @Test
    public void testGetProtectedEscape2() throws Exception {
        String url = baseUrl.replace("http://", "http://test%40me%3Atest@") + "/auth_escape2";
        InputStream stream = utils.getResourceFromUrl(url);
        assertEquals(Streams.readFullyString(stream), "OK");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testProtectedFailsWithoutCredentials() throws Exception {
        utils.getResourceFromUrl(baseUrl + "/auth");
    }

    @Test
    public void testInvalidCredentialsNotPassed() throws Exception {
        String url = baseUrl + "/no_credentials?no:auth@needed";
        InputStream stream = utils.getResourceFromUrl(url);
        assertEquals(Streams.readFullyString(stream), "OK");
    }

    @Test
    public void testRedirect() throws Exception {
        InputStream stream = utils.getResourceFromUrl(baseUrl + "/redirect");
        assertEquals(Streams.readFullyString(stream), "OK");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testCycleRedirect() throws Exception {
        InputStream stream = utils.getResourceFromUrl(baseUrl + "/cycle");
        assertEquals(Streams.readFullyString(stream), "OK");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testGetMissing() throws Exception {
        utils.getResourceFromUrl(baseUrl + "/missing");
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void testFollowsProtoChange() throws Exception {
        utils.getResourceFromUrl(baseUrl + "/secure");
    }

    // See https://github.com/brooklyncentral/brooklyn/issues/1338
    @Test(groups={"Integration"})
    public void testResourceFromUrlFollowsRedirect() throws Exception {
        String contents = new ResourceUtils(this).getResourceAsString("http://bit.ly/brooklyn-visitors-creation-script");
        assertFalse(contents.contains("bit.ly"), "contents="+contents);
    }

    private static class AuthHandler implements HttpRequestHandler {
        private String username;
        private String password;
        private String responseBody;

        public AuthHandler(String username, String password, String response) {
            this.username = username;
            this.password = password;
            this.responseBody = response;
        }

        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
            String creds = (String) context.getAttribute("creds");
            if (creds == null || !creds.equals(getExpectedCredentials())) {
                response.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            } else {
                response.setEntity(new StringEntity(responseBody));
            }
        }

        private String getExpectedCredentials() {
            if (Strings.isEmpty(password)) {
                return username;
            } else {
                return username + ":" + password;
            }
        }

    }

    private static class CheckNoCredentials implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response,
                HttpContext context) throws HttpException, IOException {
            String creds = (String) context.getAttribute("creds");
            if (creds == null) {
                response.setEntity(new StringEntity("OK"));
            } else {
                response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            }
        }

    }
}
