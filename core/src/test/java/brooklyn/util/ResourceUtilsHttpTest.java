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
package brooklyn.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.localserver.RequestBasicAuth;
import org.apache.http.localserver.ResponseBasicUnauthorized;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;

public class ResourceUtilsHttpTest {
    private ResourceUtils utils;
    private LocalTestServer server;
    private String baseUrl;

    @BeforeClass(alwaysRun=true)
    public void setUp() throws Exception {
        utils = ResourceUtils.create(this, "mycontext");

        BasicHttpProcessor httpProcessor = new BasicHttpProcessor();
        httpProcessor.addInterceptor(new ResponseServer());
        httpProcessor.addInterceptor(new ResponseContent());
        httpProcessor.addInterceptor(new ResponseConnControl());
        httpProcessor.addInterceptor(new RequestBasicAuth());
        httpProcessor.addInterceptor(new ResponseBasicUnauthorized());

        server = new LocalTestServer(httpProcessor, null);
        server.register("/simple", new SimpleResponseHandler("OK"));
        server.register("/empty", new SimpleResponseHandler(HttpStatus.SC_NO_CONTENT, ""));
        server.register("/missing", new SimpleResponseHandler(HttpStatus.SC_NOT_FOUND, "Missing"));
        server.register("/redirect", new SimpleResponseHandler(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect", new BasicHeader("Location", "/simple")));
        server.register("/cycle", new SimpleResponseHandler(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect", new BasicHeader("Location", "/cycle")));
        server.register("/secure", new SimpleResponseHandler(HttpStatus.SC_MOVED_TEMPORARILY, "Redirect", new BasicHeader("Location", "https://0.0.0.0/")));
        server.register("/auth", new AuthHandler("test", "test", "OK"));
        server.register("/auth_escape", new AuthHandler("test@me:/", "test", "OK"));
        server.register("/auth_escape2", new AuthHandler("test@me:test", "", "OK"));
        server.register("/no_credentials", new CheckNoCredentials());
        server.start();

        InetSocketAddress addr = server.getServiceAddress();
        baseUrl = "http://" + addr.getHostName() + ":" + addr.getPort();
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

    @Test
    public void testFollowsProtoChange() throws Exception {
        try {
            utils.getResourceFromUrl(baseUrl + "/secure");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Connection to https://0.0.0.0 refused"));
        }
    }

    // See https://github.com/brooklyncentral/brooklyn/issues/1338
    @Test(groups={"Integration"})
    public void testResourceFromUrlFollowsRedirect() throws Exception {
        String contents = new ResourceUtils(this).getResourceAsString("http://bit.ly/brooklyn-visitors-creation-script");
        assertFalse(contents.contains("bit.ly"), "contents="+contents);
    }

    private static class SimpleResponseHandler implements HttpRequestHandler {
        private int statusCode = HttpStatus.SC_OK;
        private String responseBody = "";
        private Header[] headers;

        protected SimpleResponseHandler(String response) {
            this.responseBody = response;
        }

        protected SimpleResponseHandler(int status, String response) {
            this.statusCode = status;
            this.responseBody = response;
        }

        protected SimpleResponseHandler(int status, String response, Header... headers) {
            this.statusCode = status;
            this.responseBody = response;
            this.headers = headers;
        }

        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
            response.setStatusCode(statusCode);
            response.setEntity(new StringEntity(responseBody));
            if (headers != null) {
                for (Header header : headers) {
                    response.setHeader(header);
                }
            }
        }
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
