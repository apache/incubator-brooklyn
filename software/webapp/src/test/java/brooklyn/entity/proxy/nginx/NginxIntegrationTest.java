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
package brooklyn.entity.proxy.nginx;

import static brooklyn.test.EntityTestUtils.assertAttributeEqualsEventually;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEquals;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.WebAppMonitor;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxIntegrationTest extends BrooklynAppLiveTestSupport {
    private static final Logger log = LoggerFactory.getLogger(NginxIntegrationTest.class);

    static final String HELLO_WAR_URL = "classpath://hello-world.war";

    private NginxController nginx;
    private DynamicCluster serverPool;
    private Location localLoc;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        localLoc = mgmt.getLocationRegistry().resolve("localhost");
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testWhenNoServersReturns404() {
        serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure(DynamicCluster.FACTORY, new EntityFactory<Entity>() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        throw new UnsupportedOperationException();
                    }}));
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost"));
        
        app.start(ImmutableList.of(localLoc));
        
        assertAttributeEqualsEventually(nginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(nginx.getAttribute(NginxController.ROOT_URL), 404);
    }

    @Test(groups = "Integration")
    public void testRestart() {
        serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure(DynamicCluster.FACTORY, new EntityFactory<Entity>() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        throw new UnsupportedOperationException();
                    }}));
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost"));
        
        app.start(ImmutableList.of(localLoc));

        nginx.restart();
        
        assertAttributeEqualsEventually(nginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(nginx.getAttribute(NginxController.ROOT_URL), 404);
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testCanStartupAndShutdown() {
        serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("portNumberSensor", WebAppService.HTTP_PORT));
        
        app.start(ImmutableList.of(localLoc));
        
        // App-servers and nginx has started
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                for (Entity member : serverPool.getMembers()) {
                    assertTrue(member.getAttribute(SoftwareProcess.SERVICE_UP));
                }
                assertTrue(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
            }});

        // URLs reachable
        assertHttpStatusCodeEventuallyEquals(nginx.getAttribute(NginxController.ROOT_URL), 200);
        for (Entity member : serverPool.getMembers()) {
            assertHttpStatusCodeEventuallyEquals(member.getAttribute(WebAppService.ROOT_URL), 200);
        }

        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcess.SERVICE_UP));
        for (Entity member : serverPool.getMembers()) {
            assertFalse(member.getAttribute(SoftwareProcess.SERVICE_UP));
        }
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly using the config file template.
     */
    @Test(groups = "Integration")
    public void testCanStartupAndShutdownUsingTemplate() {
        serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));

        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("portNumberSensor", WebAppService.HTTP_PORT)
                .configure("configTemplate", "classpath://brooklyn/entity/proxy/nginx/server.conf"));

        app.start(ImmutableList.of(localLoc));

        // App-servers and nginx has started
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                for (Entity member : serverPool.getMembers()) {
                    assertTrue(member.getAttribute(SoftwareProcess.SERVICE_UP));
                }
                assertTrue(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
            }});

        // URLs reachable
        assertHttpStatusCodeEventuallyEquals(nginx.getAttribute(NginxController.ROOT_URL), 200);
        for (Entity member : serverPool.getMembers()) {
            assertHttpStatusCodeEventuallyEquals(member.getAttribute(WebAppService.ROOT_URL), 200);
        }

        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcess.SERVICE_UP));
        for (Entity member : serverPool.getMembers()) {
            assertFalse(member.getAttribute(SoftwareProcess.SERVICE_UP));
        }
    }

    /**
     * Test that the Nginx proxy works, serving all domains, if no domain is set
     */
    @Test(groups = "Integration")
    public void testDomainless() {
        serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("portNumberSensor", WebAppService.HTTP_PORT));
        
        app.start(ImmutableList.of(localLoc));
        
        // App-servers and nginx has started
        assertAttributeEqualsEventually(serverPool, SoftwareProcess.SERVICE_UP, true);
        for (Entity member : serverPool.getMembers()) {
            assertAttributeEqualsEventually(member, SoftwareProcess.SERVICE_UP, true);
        }
        assertAttributeEqualsEventually(nginx, SoftwareProcess.SERVICE_UP, true);

        // URLs reachable
        assertHttpStatusCodeEventuallyEquals(nginx.getAttribute(NginxController.ROOT_URL), 200);
        for (Entity member : serverPool.getMembers()) {
            assertHttpStatusCodeEventuallyEquals(member.getAttribute(WebAppService.ROOT_URL), 200);
        }

        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcess.SERVICE_UP));
        for (Entity member : serverPool.getMembers()) {
            assertFalse(member.getAttribute(SoftwareProcess.SERVICE_UP));
        }
    }
    
    @Test(groups = "Integration")
    public void testTwoNginxesGetDifferentPorts() {
        serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure(DynamicCluster.FACTORY, new EntityFactory<Entity>() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        throw new UnsupportedOperationException();
                    }}));
        
        NginxController nginx1 = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("port", "14000+"));
        
        NginxController nginx2 = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("port", "14000+"));
        
        app.start(ImmutableList.of(localLoc));

        String url1 = nginx1.getAttribute(NginxController.ROOT_URL);
        String url2 = nginx2.getAttribute(NginxController.ROOT_URL);

        assertTrue(url1.contains(":1400"), url1);
        assertTrue(url2.contains(":1400"), url2);
        assertNotEquals(url1, url2, "Two nginxs should listen on different ports, not both on "+url1);
        
        // Nginx has started
        assertAttributeEqualsEventually(nginx1, SoftwareProcess.SERVICE_UP, true);
        assertAttributeEqualsEventually(nginx2, SoftwareProcess.SERVICE_UP, true);

        // Nginx reachable (returning default 404)
        assertHttpStatusCodeEventuallyEquals(url1, 404);
        assertHttpStatusCodeEventuallyEquals(url2, 404);
    }
    
    /** Test that site access does not fail even while nginx is reloaded */
    // FIXME test disabled -- reload isn't a problem, but #365 is
    @Test(enabled = false, groups = "Integration")
    public void testServiceContinuity() throws Exception {
        serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool));
        
        app.start(ImmutableList.of(localLoc));

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                for (Entity member : serverPool.getMembers()) {
                    assertHttpStatusCodeEquals(member.getAttribute(WebAppService.ROOT_URL), 200);
                }
                assertHttpStatusCodeEquals(nginx.getAttribute(WebAppService.ROOT_URL), 200);
            }});

        WebAppMonitor monitor = new WebAppMonitor(nginx.getAttribute(WebAppService.ROOT_URL))
            .logFailures(log)
            .delayMillis(0);
        Thread t = new Thread(monitor);
        t.start();

        try {
            Thread.sleep(1*1000);
            log.info("service continuity test, startup, "+monitor.getAttempts()+" requests made");
            monitor.assertAttemptsMade(10, "startup").assertNoFailures("startup").resetCounts();
            
            for (int i=0; i<20; i++) {
                nginx.reload();
                Thread.sleep(500);
                log.info("service continuity test, iteration "+i+", "+monitor.getAttempts()+" requests made");
                monitor.assertAttemptsMade(10, "reloaded").assertNoFailures("reloaded").resetCounts();
            }
            
        } finally {
            t.interrupt();
        }
        
        app.stop();

        // Services have stopped
        assertFalse(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
        assertFalse(serverPool.getAttribute(SoftwareProcess.SERVICE_UP));
        for (Entity member : serverPool.getMembers()) {
            assertFalse(member.getAttribute(SoftwareProcess.SERVICE_UP));
        }
    }

    // FIXME test disabled -- issue #365
    /*
     * This currently makes no assertions, but writes out the number of sequential reqs per sec
     * supported with nginx and jboss.
     * <p>
     * jboss is (now) steady, at 6k+, since we close the connections in HttpTestUtils.getHttpStatusCode.
     * but nginx still hits problems, after about 15k reqs, something is getting starved in nginx.
     */
    @Test(enabled=false, groups = "Integration")
    public void testContinuityNginxAndJboss() throws Exception {
        serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, HELLO_WAR_URL));
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool));
        
        app.start(ImmutableList.of(localLoc));

        final String nginxUrl = nginx.getAttribute(WebAppService.ROOT_URL);

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                for (Entity member : serverPool.getMembers()) {
                    String jbossUrl = member.getAttribute(WebAppService.ROOT_URL);
                    assertHttpStatusCodeEquals(jbossUrl, 200);
                }
                assertHttpStatusCodeEquals(nginxUrl, 200);
            }});

        final String jbossUrl = Iterables.get(serverPool.getMembers(), 0).getAttribute(WebAppService.ROOT_URL);
        
        Thread t = new Thread(new Runnable() {
            public void run() {
                long lastReportTime = System.currentTimeMillis();
                int num = 0;
                while (true) {
                    try {
                        num++;
                        int code = HttpTestUtils.getHttpStatusCode(nginxUrl);
                        if (code!=200) log.info("NGINX GOT: "+code);
                        else log.debug("NGINX GOT: "+code);
                        if (System.currentTimeMillis()>=lastReportTime+1000) {
                            log.info("NGINX DID "+num+" requests in last "+(System.currentTimeMillis()-lastReportTime)+"ms");
                            num=0;
                            lastReportTime = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        log.info("NGINX GOT: "+e);
                    }
                }
            }});
        t.start();
        
        Thread t2 = new Thread(new Runnable() {
            public void run() {
                long lastReportTime = System.currentTimeMillis();
                int num = 0;
                while (true) {
                    try {
                        num++;
                        int code = HttpTestUtils.getHttpStatusCode(jbossUrl);
                        if (code!=200) log.info("JBOSS GOT: "+code);
                        else log.debug("JBOSS GOT: "+code);
                        if (System.currentTimeMillis()>=1000+lastReportTime) {
                            log.info("JBOSS DID "+num+" requests in last "+(System.currentTimeMillis()-lastReportTime)+"ms");
                            num=0;
                            lastReportTime = System.currentTimeMillis();
                        }
                    } catch (Exception e) {
                        log.info("JBOSS GOT: "+e);
                    }
                }
            }});
        t2.start();
        
        t2.join();
    }

    /**
     * Test that the Nginx proxy starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void testCanRestart() {
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", serverPool)
                .configure("domain", "localhost")
                .configure("portNumberSensor", WebAppService.HTTP_PORT));
        
        app.start(ImmutableList.of(localLoc));
        
        // App-servers and nginx has started
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
            }});

        log.info("started, will restart soon");
        Time.sleep(Duration.ONE_SECOND);
        
        nginx.restart();

        Time.sleep(Duration.ONE_SECOND);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(nginx.getAttribute(SoftwareProcess.SERVICE_UP));
            }});
        log.info("restarted and got service up");
    }
    
//    public static void main(String[] args) {
//        NginxIntegrationTest t = new NginxIntegrationTest();
//        t.setup();
//        t.testCanRestart();
//        t.shutdown();        
//    }

}
