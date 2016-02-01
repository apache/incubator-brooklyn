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
package org.apache.brooklyn.rest;

import static org.testng.Assert.assertTrue;

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
import org.apache.brooklyn.rest.security.provider.AnyoneSecurityProvider;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.brooklyn.util.time.Time;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class BrooklynPropertiesSecurityFilterTest extends BrooklynRestApiLauncherTestFixture {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynPropertiesSecurityFilterTest.class);

    /*
        Exception java.lang.AssertionError
        
        Message: error creating app. response code=400 expected [true] but found [false]
        Stacktrace:
        
        
        at org.testng.Assert.fail(Assert.java:94)
        at org.testng.Assert.failNotEquals(Assert.java:494)
        at org.testng.Assert.assertTrue(Assert.java:42)
        at org.apache.brooklyn.rest.BrooklynPropertiesSecurityFilterTest.startAppAtNode(BrooklynPropertiesSecurityFilterTest.java:94)
        at org.apache.brooklyn.rest.BrooklynPropertiesSecurityFilterTest.testInteractionOfSecurityFilterAndFormMapProvider(BrooklynPropertiesSecurityFilterTest.java:64)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
        at org.testng.internal.Invoker.invokeMethod(Invoker.java:714)
        at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
        at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
        at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
        at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
        at org.testng.TestRunner.privateRun(TestRunner.java:767)
        at org.testng.TestRunner.run(TestRunner.java:617)
        at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
        at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
        at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
        at org.testng.SuiteRunner.run(SuiteRunner.java:254)
        at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
        at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
        at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
        at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
        at org.testng.TestNG.run(TestNG.java:1057)
        at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
        at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
        at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
        at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
        at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
     */
    // Would be great for this to be a unit test but it takes almost ten seconds.
    @Test(groups = {"Integration","Broken"})
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
            String effector = String.format("/applications/%s/entities/%s/effectors/identityEffector", appId, entityId);
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
                "- type: org.apache.brooklyn.test.entity.TestEntity";
        HttpClient client = HttpTool.httpClientBuilder()
                .uri(getBaseUri(server))
                .build();
        HttpToolResponse response = HttpTool.httpPost(client, URI.create(getBaseUri() + "/applications"),
                ImmutableMap.of(HttpHeaders.CONTENT_TYPE, "application/x-yaml"),
                blueprint.getBytes());
        assertTrue(HttpTool.isStatusCodeHealthy(response.getResponseCode()), "error creating app. response code=" + response.getResponseCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new ObjectMapper().readValue(response.getContent(), HashMap.class);
        return (String) body.get("entityId");
    }

    @SuppressWarnings("rawtypes")
    private String getTestEntityInApp(Server server, String appId) throws Exception {
        HttpClient client = HttpTool.httpClientBuilder()
                .uri(getBaseUri(server))
                .build();
        List entities = new ObjectMapper().readValue(
                HttpTool.httpGet(client, URI.create(getBaseUri() + "/applications/" + appId + "/entities"), MutableMap.<String, String>of()).getContent(), List.class);
        LOG.info((String) ((Map) entities.get(0)).get("id"));
        return (String) ((Map) entities.get(0)).get("id");
    }
}
