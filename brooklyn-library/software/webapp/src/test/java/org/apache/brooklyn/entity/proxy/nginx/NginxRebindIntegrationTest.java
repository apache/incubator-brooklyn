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
package org.apache.brooklyn.entity.proxy.nginx;

import static org.apache.brooklyn.test.EntityTestUtils.assertAttributeEqualsEventually;
import static org.apache.brooklyn.test.HttpTestUtils.assertHttpStatusCodeEquals;
import static org.apache.brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals;
import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.mgmt.rebind.RebindOptions;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.proxy.nginx.NginxController;
import org.apache.brooklyn.entity.proxy.nginx.UrlMapping;
import org.apache.brooklyn.entity.proxy.nginx.UrlRewriteRule;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.tomcat.Tomcat8Server;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.WebAppMonitor;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxRebindIntegrationTest extends RebindTestFixtureWithApp {

    private static final Logger LOG = LoggerFactory.getLogger(NginxRebindIntegrationTest.class);

    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
    private ExecutorService executor;
    
    @Override
    protected boolean useLiveManagementContext() {
        // For Aled, the test failed without own ~/.brooklyn/brooklyn.properties.
        // Suspect that was caused by local environment, with custom brooklyn.ssh.config.scriptHeader
        // to set things like correct Java on path.
        return true;
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        localhostProvisioningLocation = origManagementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        executor = Executors.newCachedThreadPool();
    }

    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (WebAppMonitor monitor : webAppMonitors) {
            monitor.terminate();
        }
        webAppMonitors.clear();
        if (executor != null) executor.shutdownNow();
        super.tearDown();
    }

    private WebAppMonitor newWebAppMonitor(String url, int expectedResponseCode) {
        WebAppMonitor monitor = new WebAppMonitor(url)
//                .delayMillis(0) FIXME Re-enable to fast polling
                .expectedResponseCode(expectedResponseCode)
                .logFailures(LOG);
        webAppMonitors.add(monitor);
        executor.execute(monitor);
        return monitor;
    }
    
    /**
     * Test can rebind to the simplest possible nginx configuration (i.e. no server pool).
     */
    @Test(groups = "Integration")
    public void testRebindsWithEmptyServerPool() throws Exception {
        
        // Set up nginx with a server pool
        DynamicCluster origServerPool = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(Tomcat8Server.class))
                .configure("initialSize", 0));
        
        NginxController origNginx = origApp.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", origServerPool)
                .configure("domain", "localhost"));
        
        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String rootUrl = origNginx.getAttribute(NginxController.ROOT_URL);
        int nginxPort = origNginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        assertHttpStatusCodeEventuallyEquals(rootUrl, 404);
        WebAppMonitor monitor = newWebAppMonitor(rootUrl, 404);
        final String origConfigFile = origNginx.getConfigFile();
        
        newApp = rebind(RebindOptions.create().terminateOrigManagementContext(true));
        final NginxController newNginx = (NginxController) Iterables.find(newApp.getChildren(), Predicates.instanceOf(NginxController.class));

        assertEquals(newNginx.getConfigFile(), origConfigFile);
        
        EntityTestUtils.assertAttributeEqualsEventually(newNginx, NginxController.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        assertEquals(newNginx.getAttribute(NginxController.PROXY_HTTP_PORT), (Integer)nginxPort);
        assertEquals(newNginx.getAttribute(NginxController.ROOT_URL), rootUrl);
        assertEquals(newNginx.getAttribute(NginxController.PROXY_HTTP_PORT), origNginx.getAttribute(NginxController.PROXY_HTTP_PORT));
        assertEquals(newNginx.getConfig(NginxController.STICKY), origNginx.getConfig(NginxController.STICKY));
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(rootUrl, 404);
        
        assertEquals(monitor.getFailures(), 0);
    }
    
    /*
        Exception java.lang.NoClassDefFoundError
        
        Message: org/apache/brooklyn/test/HttpTestUtils$3
        Stacktrace:
        
        
        at org.apache.brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals(HttpTestUtils.java:208)
        at org.apache.brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals(HttpTestUtils.java:204)
        at org.apache.brooklyn.entity.proxy.nginx.NginxRebindIntegrationTest.testRebindsWithoutLosingServerPool(NginxRebindIntegrationTest.java:178)
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
    /**
     * Test can rebind with an active server pool.
     */
    @Test(groups = {"Integration","Broken"})
    public void testRebindsWithoutLosingServerPool() throws Exception {
        
        // Set up nginx with a server pool
        DynamicCluster origServerPool = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(Tomcat8Server.class).configure("war", getTestWar()))
                .configure("initialSize", 1));
        
        NginxController origNginx = origApp.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", origServerPool)
                .configure("domain", "localhost"));
        
        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String rootUrl = origNginx.getAttribute(NginxController.ROOT_URL);
        Tomcat8Server origServer = (Tomcat8Server) Iterables.getOnlyElement(origServerPool.getMembers());
        assertEquals(origNginx.getAttribute(NginxController.SERVER_POOL_TARGETS).keySet(), ImmutableSet.of(origServer));
        
        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(rootUrl, 200);
        final String origConfigFile = origNginx.getConfigFile();
        
        // Rebind
        newApp = rebind(RebindOptions.create().terminateOrigManagementContext(true));
        ManagementContext newManagementContext = newApp.getManagementContext();
        final NginxController newNginx = (NginxController) Iterables.find(newApp.getChildren(), Predicates.instanceOf(NginxController.class));
        final DynamicCluster newServerPool = (DynamicCluster) newManagementContext.getEntityManager().getEntity(origServerPool.getId());
        final Tomcat8Server newServer = (Tomcat8Server) Iterables.getOnlyElement(newServerPool.getMembers());

        // Expect continually to have same nginx members; should not lose them temporarily!
        Asserts.succeedsContinually(new Runnable() {
            public void run() {
                Map<Entity, String> newNginxMemebers = newNginx.getAttribute(NginxController.SERVER_POOL_TARGETS);
                assertEquals(newNginxMemebers.keySet(), ImmutableSet.of(newServer));
            }});
        
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);

        assertEquals(newNginx.getConfigFile(), origConfigFile);
        
        // Check that an update doesn't break things
        newNginx.update();

        assertHttpStatusCodeEquals(rootUrl, 200);

        // Resize new cluster, and confirm change takes affect.
        //  - Increase size
        //  - wait for nginx to definitely be updates (TODO nicer way to wait for updated?)
        //  - terminate old server
        //  - confirm can still route messages
        newServerPool.resize(2);
        
        Thread.sleep(10*1000);
        
        newServer.stop();

        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);

        // Check that URLs have been constantly reachable
        assertEquals(monitor.getFailures(), 0);
    }
    
    /*
        Exception java.lang.NoClassDefFoundError
        
        Message: org/apache/brooklyn/test/HttpTestUtils$3
        Stacktrace:
        
        
        at org.apache.brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals(HttpTestUtils.java:208)
        at org.apache.brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals(HttpTestUtils.java:204)
        at org.apache.brooklyn.entity.proxy.nginx.NginxRebindIntegrationTest.testRebindsWithoutLosingUrlMappings(NginxRebindIntegrationTest.java:254)
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
    /**
     * Test can rebind to the with server pool and URL remappings.
     * NOTE: This requires a redirection from localhost1 to 127.0.0.1 in your /etc/hosts file
     */
    @Test(groups = {"Integration","Broken"})
    public void testRebindsWithoutLosingUrlMappings() throws Exception {
        
        // Set up nginx with a url-mapping
        Group origUrlMappingsGroup = origApp.createAndManageChild(EntitySpec.create(BasicGroup.class)
                .configure("childrenAsMembers", true));
        
        DynamicCluster origServerPool = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(Tomcat8Server.class).configure("war", getTestWar()))
                .configure("initialSize", 1)); 

        UrlMapping origMapping = origUrlMappingsGroup.addChild(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost1")
                .configure("target", origServerPool)
                .configure("rewrites", ImmutableList.of(new UrlRewriteRule("/foo/(.*)", "/$1"))));
        
        NginxController origNginx = origApp.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("domain", "localhost")
                .configure("urlMappings", origUrlMappingsGroup));

        // Start the app, and ensure reachable; start polling the URL
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        String mappingGroupUrl = "http://localhost1:"+origNginx.getAttribute(NginxController.PROXY_HTTP_PORT)+"/foo/";

        assertHttpStatusCodeEventuallyEquals(mappingGroupUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(mappingGroupUrl, 200);
        final String origConfigFile = origNginx.getConfigFile();
        
        // Create a rebinding
        newApp = rebind(RebindOptions.create().terminateOrigManagementContext(true));
        ManagementContext newManagementContext = newApp.getManagementContext();
        final NginxController newNginx = (NginxController) Iterables.find(newApp.getChildren(), Predicates.instanceOf(NginxController.class));
        DynamicCluster newServerPool = (DynamicCluster) newManagementContext.getEntityManager().getEntity(origServerPool.getId());
        Tomcat8Server newServer = (Tomcat8Server) Iterables.getOnlyElement(newServerPool.getMembers());
        
        assertAttributeEqualsEventually(newNginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(mappingGroupUrl, 200);
        
        assertEquals(newNginx.getConfigFile(), origConfigFile);
        
        // Check that an update doesn't break things
        newNginx.update();

        assertHttpStatusCodeEquals(mappingGroupUrl, 200);

        // Resize new cluster, and confirm change takes affect.
        //  - Increase size
        //  - wait for nginx to definitely be updates (TODO nicer way to wait for updated?)
        //  - terminate old server
        //  - confirm can still route messages
        newServerPool.resize(2);
        
        Thread.sleep(10*1000);
        
        newServer.stop();

        assertHttpStatusCodeEquals(mappingGroupUrl, 200);

        // Check that URLs have been constantly reachable
        assertEquals(monitor.getFailures(), 0);
    }
}
