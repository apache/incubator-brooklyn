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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.EntityManager;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * Test the operation of the {@link NginxController} class, for URL mapped groups (two different pools).
 * 
 * These tests require that /etc/hosts contains some extra entries, such as:
 *     127.0.0.1       localhost localhost1 localhost2 localhost3 localhost4
 */
public class NginxUrlMappingIntegrationTest extends BrooklynAppLiveTestSupport {
    
    // TODO Make JBoss7Server.deploy wait for the web-app to actually be deployed.
    // That may simplify some of the tests, because we can assert some things immediately rather than in a succeedsEventually.
    
    private static final Logger log = LoggerFactory.getLogger(NginxUrlMappingIntegrationTest.class);

    private static final String WAR_URL = "classpath://hello-world.war";
    
    private NginxController nginx;
    private DynamicCluster cluster;
    private Group urlMappingsGroup;
    private EntityManager entityManager;
    private LocalhostMachineProvisioningLocation localLoc;
    
    private URL war;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        war = getClass().getClassLoader().getResource("hello-world.war");
        assertNotNull(war, "Unable to locate hello-world.war resource");
        
        urlMappingsGroup = app.createAndManageChild(EntitySpec.create(BasicGroup.class)
                .configure("childrenAsMembers", true));
        entityManager = app.getManagementContext().getEntityManager();
        
        localLoc = new LocalhostMachineProvisioningLocation();
    }

    protected void checkExtraLocalhosts() throws Exception {
        Set<String> failedHosts = Sets.newLinkedHashSet();
        List<String> allHosts = ImmutableList.of("localhost", "localhost1", "localhost2", "localhost3", "localhost4");
        for (String host : allHosts) {
            try {
                InetAddress i = InetAddress.getByName(host);
                byte[] b = ((Inet4Address)i).getAddress();
                if (b[0]!=127 || b[1]!=0 || b[2]!=0 || b[3]!=1) {
                    log.warn("Failed to resolve "+host+" (test will subsequently fail, but looking for more errors first; see subsequent failure for more info): wrong IP "+Arrays.asList(b));
                    failedHosts.add(host);
                }
            } catch (Exception e) {
                log.warn("Failed to resolve "+host+" (test will subsequently fail, but looking for more errors first; see subsequent failure for more info): "+e, e);
                failedHosts.add(host);
            }
        }
        if (!failedHosts.isEmpty()) {
            fail("These tests (in "+this+") require special hostnames to map to 127.0.0.1, in /etc/hosts: "+failedHosts);
        }
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingServerNameAndPath() throws Exception {
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("urlMappings", urlMappingsGroup));
        
        //cluster 0 mounted at localhost1 /
        DynamicCluster c0 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+"))
                .configure(JavaWebAppService.ROOT_WAR, WAR_URL));
        UrlMapping u0 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost1")
                .configure("target", c0)
                .parent(urlMappingsGroup));
        Entities.manage(u0);
        
        //cluster 1 at localhost2 /hello-world/
        DynamicCluster c1 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+"))
                .configure(JavaWebAppService.NAMED_WARS, ImmutableList.of(WAR_URL)));
        UrlMapping u1 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost2")
                .configure("path", "/hello-world($|/.*)")
                .configure("target", c1)
                .parent(urlMappingsGroup));
        Entities.manage(u1);

        // cluster 2 at localhost3 /c2/  and mapping /hello/xxx to /hello/new xxx
        DynamicCluster c2 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+")));
        UrlMapping u2 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost3")
                .configure("path", "/c2($|/.*)")
                .configure("target", c2)
                .configure("rewrites", ImmutableList.of(new UrlRewriteRule("(.*/|)(hello/)(.*)", "$1$2new $3").setBreak()))
                .parent(urlMappingsGroup));
        Entities.manage(u2);
        // FIXME rewrite not a config
        
        app.start(ImmutableList.of(localLoc));
        final int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        for (Entity member : c2.getMembers()) {
            ((JBoss7Server)member).deploy(war.toString(), "c2.war");
        }
    
        Entities.dumpInfo(app);
        
        // Confirm routes requests to the correct cluster
        // Do more than one request for each in-case just lucky with round-robin...
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                //cluster 0
                for (int i = 0; i < 2; i++) {
                    HttpTestUtils.assertContentContainsText("http://localhost1:"+port, "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/", "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/hello/frank", "http://localhost1:"+port+"/hello/frank");
                }
                //cluster 1
                for (int i = 0; i < 2; i++) {
                    HttpTestUtils.assertContentContainsText("http://localhost2:"+port+"/hello-world", "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost2:"+port+"/hello-world/", "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost2:"+port+"/hello-world/hello/bob", "http://localhost2:"+port+"/hello-world/hello/bob");
                }
                //cluster 2
                for (int i = 0; i < 2; i++) {
                    HttpTestUtils.assertContentContainsText("http://localhost3:"+port+"/c2", "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost3:"+port+"/c2/", "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost3:"+port+"/c2/hello/joe", "http://localhost3:"+port+"/c2/hello/new%20joe");
                }
            }});
        
        //these should *not* be available
        HttpTestUtils.assertHttpStatusCodeEquals("http://localhost:"+port+"/", 404);
        HttpTestUtils.assertHttpStatusCodeEquals("http://localhost1:"+port+"/hello-world", 404);
        HttpTestUtils.assertHttpStatusCodeEquals("http://localhost2:"+port+"/", 404);
        HttpTestUtils.assertHttpStatusCodeEquals("http://localhost2:"+port+"/hello-world/notexists", 404);
        HttpTestUtils.assertHttpStatusCodeEquals("http://localhost3:"+port+"/", 404);
        
        // TODO previously said "make sure nginx default welcome page isn't displayed",
        // but the assertion only worked because threw exception on 404 trying to read
        // stdin of http connection. If reading stderr of http connection, we do see
        // "ginx" in the output. Why were we asserting this? Can we just delete it?
        // Previous code was:
        //     Asserts.assertFails { HttpTestUtils.assertContentContainsText([timeout:1], "http://localhost:${port}/", "ginx"); }
    }

    @Test(groups = "Integration")
    public void testUrlMappingRoutesRequestByPathToCorrectGroup() throws Exception {
        DynamicCluster c0 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+")));
        UrlMapping u0 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost")
                .configure("path", "/atC0($|/.*)")
                .configure("target", c0)
                .parent(urlMappingsGroup));
        Entities.manage(u0);

        DynamicCluster c1 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+")));
        UrlMapping u1 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost")
                .configure("path", "/atC1($|/.*)")
                .configure("target", c1)
                .parent(urlMappingsGroup));
        Entities.manage(u1);

        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("domain", "localhost")
                .configure("port", "8000+")
                .configure("portNumberSensor", WebAppService.HTTP_PORT)
                .configure("urlMappings", urlMappingsGroup));
        
        app.start(ImmutableList.of(localLoc));
        final int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        for (Entity child : c0.getMembers()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC0.war");
        }
        for (Entity child : c1.getMembers()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC1.war");
        }

        // Confirm routes requests to the correct cluster
        // Do more than one request for each in-case just lucky with round-robin...
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                for (int i = 0; i < 2; i++) {
                    HttpTestUtils.assertContentContainsText("http://localhost:"+port+"/atC0", "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost:"+port+"/atC0/", "Hello");
                }
                for (int i = 0; i < 2; i++) {
                    HttpTestUtils.assertContentContainsText("http://localhost:"+port+"/atC1", "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost:"+port+"/atC1/", "Hello");
                }
            }});
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingRemovedWhenMappingEntityRemoved() throws Exception {
        DynamicCluster c0 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+"))
                .configure(JavaWebAppService.ROOT_WAR, war.toString()));
        UrlMapping u0 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost2")
                .configure("target", c0)
                .parent(urlMappingsGroup));
        Entities.manage(u0);
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("domain", "localhost")
                .configure("port", "8000+")
                .configure("portNumberSensor", WebAppService.HTTP_PORT)
                .configure("urlMappings", urlMappingsGroup));
        
        app.start(ImmutableList.of(localLoc));
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        // Wait for deployment to be successful
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals("http://localhost2:"+port+"/", 200);
        
        // Now remove mapping; will no longer route requests
        Entities.unmanage(u0);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals("http://localhost2:"+port+"/", 404);
    }
    
    @Test(groups = "Integration")
    public void testWithCoreClusterAndUrlMappedGroup() throws Exception {
        // TODO Should use different wars, so can confirm content from each cluster
        // TODO Could also assert on: nginx.getConfigFile()
        
        checkExtraLocalhosts();
        
        DynamicCluster coreCluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+"))
                .configure(JavaWebAppService.ROOT_WAR, war.toString()));
        
        DynamicCluster c1 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+"))
                .configure(JavaWebAppService.NAMED_WARS, ImmutableList.of(war.toString())));
        UrlMapping u1 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost1")
                .configure("target", c1)
                .parent(urlMappingsGroup));
        Entities.manage(u1);
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", coreCluster)
                .configure("domain", "localhost")
                .configure("port", "8000+")
                .configure("portNumberSensor", WebAppService.HTTP_PORT)
                .configure("urlMappings", urlMappingsGroup));
                
        app.start(ImmutableList.of(localLoc));
        final int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        // check nginx forwards localhost1 to c1, and localhost to core group 
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/hello-world", "Hello");
                HttpTestUtils.assertHttpStatusCodeEquals("http://localhost1:"+port+"", 404);
                
                HttpTestUtils.assertContentContainsText("http://localhost:"+port+"", "Hello");
                HttpTestUtils.assertHttpStatusCodeEquals("http://localhost:"+port+"/hello-world", 404);
            }});
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingMultipleRewrites() throws Exception {
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("urlMappings", urlMappingsGroup));
    
        //cluster 0 mounted at localhost1 /
        DynamicCluster c0 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+"))
                .configure(JavaWebAppService.ROOT_WAR, WAR_URL));
        UrlMapping u0 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost1")
                .configure("target", c0)
                .parent(urlMappingsGroup));
        u0.addRewrite("/goodbye/al(.*)", "/hello/al$1");
        u0.addRewrite(new UrlRewriteRule("/goodbye(|/.*)$", "/hello$1").setBreak());
        u0.addRewrite("(.*)/hello/al(.*)", "$1/hello/Big Al$2");
        u0.addRewrite("/hello/an(.*)", "/hello/Sir An$1");
        Entities.manage(u0);

        app.start(ImmutableList.of(localLoc));
        final int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        // Confirm routes requests to the correct cluster
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                // health check
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"", "Hello");
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/hello/frank", "http://localhost1:"+port+"/hello/frank");
                
                // goodbye rewritten to hello
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/goodbye/frank", "http://localhost1:"+port+"/hello/frank");
                // hello al rewritten to hello Big Al
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/hello/aled", "http://localhost1:"+port+"/hello/Big%20Aled");
                // hello andrew rewritten to hello Sir Andrew
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/hello/andrew", "http://localhost1:"+port+"/hello/Sir%20Andrew");
                
                // goodbye alex rewritten to hello Big Alex (two rewrites)
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/goodbye/alex", "http://localhost1:"+port+"/hello/Big%20Alex");
                // but goodbye andrew rewritten only to hello Andrew -- test the "break" logic above (won't continue rewriting)
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/goodbye/andrew", "http://localhost1:"+port+"/hello/andrew");
                
                // al rewrite can be anywhere
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/hello/hello/alex", "http://localhost1:"+port+"/hello/hello/Big%20Alex");
                // but an rewrite must be at beginning
                HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"/hello/hello/andrew", "http://localhost1:"+port+"/hello/hello/andrew");
            }});
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingGroupRespondsToScaleOut() throws Exception {
        checkExtraLocalhosts();
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("domain", "localhost")
                .configure("port", "8000+")
                .configure("portNumberSensor", WebAppService.HTTP_PORT)
                .configure("urlMappings", urlMappingsGroup));
        
        final DynamicCluster c1 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+"))
                .configure(JavaWebAppService.ROOT_WAR, war.toString()));
        final UrlMapping u1 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost1")
                .configure("target", c1)
                .parent(urlMappingsGroup));
        Entities.manage(u1);
        
        app.start(ImmutableList.of(localLoc));
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        Entity c1jboss = Iterables.getOnlyElement(c1.getMembers());
        
        // Wait for app-server to be responsive, and url-mapping to update its TARGET_ADDRESSES (through async subscription)
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                // Entities.dumpInfo(app);
                assertEquals(u1.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), 1);
            }});

        // check nginx forwards localhost1 to c1
        HttpTestUtils.assertContentEventuallyContainsText("http://localhost1:"+port+"", "Hello");
        
        // Resize target cluster of url-mapping
        c1.resize(2);
        List c1jbosses = new ArrayList(c1.getMembers());
        c1jbosses.remove(c1jboss);
        Entity c1jboss2 = Iterables.getOnlyElement(c1jbosses);

        // TODO Have to wait for new app-server; should fix app-servers to block
        // Also wait for TARGET_ADDRESSES to update
        assertAppServerRespondsEventually(c1jboss2);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(u1.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), 2);
            }});

        // check jboss2 is included in nginx rules
        // TODO Should getConfigFile return the current config file, rather than recalculate?
        //      This assertion isn't good enough to tell if it's been deployed.
        final String c1jboss2addr = c1jboss2.getAttribute(Attributes.HOSTNAME)+":"+c1jboss2.getAttribute(Attributes.HTTP_PORT);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String conf = nginx.getConfigFile();
                assertTrue(conf.contains(c1jboss2addr), "could not find "+c1jboss2addr+" in:\n"+conf);
            }});
        
        // and check forwarding to c1 by nginx still works
        for (int i = 0; i < 2; i++) {
            HttpTestUtils.assertContentContainsText("http://localhost1:"+port+"", "Hello");
        }
    }
    
    @Test(groups = "Integration")
    public void testUrlMappingWithEmptyCoreCluster() throws Exception {
        DynamicCluster nullCluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
            .configure("initialSize", 0)
            .configure("factory", new EntityFactory<Entity>() {
                public Entity newEntity(Map flags, Entity parent) {
                    throw new UnsupportedOperationException();
                }}));

        DynamicCluster c0 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+")));
        UrlMapping u0 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost")
                .configure("path", "/atC0($|/.*)")
                .configure("target", c0)
                .parent(urlMappingsGroup));
        Entities.manage(u0);

        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("cluster", nullCluster)
                .configure("domain", "localhost")
                .configure("port", "8000+")
                .configure("portNumberSensor", WebAppService.HTTP_PORT)
                .configure("urlMappings", urlMappingsGroup));
        
        app.start(ImmutableList.of(localLoc));
        final int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        for (Entity child : c0.getMembers()) {
            ((JBoss7Server)child).deploy(war.toString(), "atC0.war");
        }

        // Confirm routes requests to the correct cluster
        // Do more than one request for each in-case just lucky with round-robin...
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                for (int i = 0; i < 2; i++) {
                    HttpTestUtils.assertContentContainsText("http://localhost:"+port+"/atC0/", "Hello");
                    HttpTestUtils.assertContentContainsText("http://localhost:"+port+"/atC0", "Hello");
                }
            }});

        // And empty-core should return 404
        HttpTestUtils.assertHttpStatusCodeEquals("http://localhost:"+port+"", 404);
    }
    
    @Test(groups = "Integration")
    public void testDiscardUrlMapping() throws Exception {
        //cluster 0 mounted at localhost1 /
        DynamicCluster c0 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).configure("httpPort", "8100+"))
                .configure(JavaWebAppService.ROOT_WAR, WAR_URL));
        UrlMapping u0 = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost1")
                .configure("target", c0)
                .parent(urlMappingsGroup));
        Entities.manage(u0);

        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("urlMappings", urlMappingsGroup));
        
        app.start(ImmutableList.of(localLoc));
        int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals("http://localhost1:"+port+"", 200);

        // Discard, and confirm that subsequently get a 404 instead
        u0.discard();
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals("http://localhost1:"+port+"", 404);
    }

    private void assertAppServerRespondsEventually(Entity server) {
        String hostname = server.getAttribute(Attributes.HOSTNAME);
        int port = server.getAttribute(Attributes.HTTP_PORT);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals("http://"+hostname+":"+port, 200);
    }
}
