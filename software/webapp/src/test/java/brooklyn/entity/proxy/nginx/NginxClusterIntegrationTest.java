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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.LoadBalancerCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.EntityManager;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxClusterIntegrationTest extends BrooklynAppLiveTestSupport {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(NginxClusterIntegrationTest.class);

    private static final long TIMEOUT_MS = 60*1000;
    
    private URL war;
    private Location localhostProvisioningLoc;
    private EntityManager entityManager;
    private LoadBalancerCluster loadBalancerCluster;
    private EntitySpec<NginxController> nginxSpec;
    private Group urlMappings;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        war = checkNotNull(getClass().getClassLoader().getResource("hello-world.war"), "hello-world.war not on classpath");
        localhostProvisioningLoc = mgmt.getLocationRegistry().resolve("localhost");
        
        urlMappings = app.createAndManageChild(EntitySpec.create(BasicGroup.class)
                .configure("childrenAsMembers", true));
        entityManager = app.getManagementContext().getEntityManager();
        
        nginxSpec = EntitySpec.create(NginxController.class);
    }

    @Test(groups = "Integration")
    public void testCreatesNginxInstancesAndResizes() {
        loadBalancerCluster = app.createAndManageChild(EntitySpec.create(LoadBalancerCluster.class)
                .configure(LoadBalancerCluster.MEMBER_SPEC, nginxSpec)
                .configure("initialSize", 1)
                .configure(NginxController.DOMAIN_NAME, "localhost"));
        
        app.start(ImmutableList.of(localhostProvisioningLoc));
        
        assertEquals(findNginxs().size(), 1);
        assertNginxsResponsiveEvenutally(findNginxs());
        
        // Resize load-balancer cluster
        loadBalancerCluster.resize(2);
        assertEquals(findNginxs().size(), 2);
        assertNoDuplicates(findNginxRootUrls());
        assertNginxsResponsiveEvenutally(findNginxs());
    }
    
    @Test(groups = "Integration")
    public void testNginxInstancesConfiguredWithServerPool() {
        DynamicCluster serverPool = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, war.getPath()));
        
        loadBalancerCluster = app.createAndManageChild(EntitySpec.create(LoadBalancerCluster.class)
                .configure("serverPool", serverPool)
                .configure(LoadBalancerCluster.MEMBER_SPEC, nginxSpec)
                .configure("initialSize", 1)
                .configure(NginxController.DOMAIN_NAME, "localhost"));
        
        app.start(ImmutableList.of(localhostProvisioningLoc));
        
        assertEquals(findNginxs().size(), 1);
        
        String hostname = "localhost";
        List<String> pathsFor200 = ImmutableList.of(""); // i.e. app deployed at root
        assertNginxsResponsiveEvenutally(findNginxs(), hostname, pathsFor200);
    }

    @Test(groups = "Integration")
    public void testNginxInstancesConfiguredWithUrlMappings() {
        DynamicCluster c1 = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.NAMED_WARS, ImmutableList.of(war.getPath())));

        UrlMapping urlMapping = entityManager.createEntity(EntitySpec.create(UrlMapping.class)
                .configure("domain", "localhost")
                .configure("path", "/hello-world($|/.*)")
                .configure("target", c1)
                .parent(urlMappings));
        Entities.manage(urlMapping);
        
        loadBalancerCluster = app.createAndManageChild(EntitySpec.create(LoadBalancerCluster.class)
                .configure("urlMappings", urlMappings)
                .configure(LoadBalancerCluster.MEMBER_SPEC, nginxSpec)
                .configure("initialSize", 1));

        app.start(ImmutableList.of(localhostProvisioningLoc));
        
        assertEquals(findNginxs().size(), 1);
        
        String hostname = "localhost";
        List<String> pathsFor200 = ImmutableList.of("hello-world", "hello-world/");
        assertNginxsResponsiveEvenutally(findNginxs(), hostname, pathsFor200);
    }

    @Test(groups = "Integration")
    public void testClusterIsUpIffHasChildLoadBalancer() {
        loadBalancerCluster = app.createAndManageChild(EntitySpec.create(LoadBalancerCluster.class)
                .configure(LoadBalancerCluster.MEMBER_SPEC, nginxSpec)
                .configure("initialSize", 0)
                .configure(NginxController.DOMAIN_NAME, "localhost"));
        
        app.start(ImmutableList.of(localhostProvisioningLoc));
        EntityTestUtils.assertAttributeEqualsContinually(loadBalancerCluster, Startable.SERVICE_UP, false);
        
        loadBalancerCluster.resize(1);
        EntityTestUtils.assertAttributeEqualsEventually(loadBalancerCluster, Startable.SERVICE_UP, true);

        loadBalancerCluster.resize(0);
        EntityTestUtils.assertAttributeEqualsEventually(loadBalancerCluster, Startable.SERVICE_UP, false);
    }
    
    // Warning: test is a little brittle for if a previous run leaves something on these required ports
    @Test(groups = "Integration")
    public void testConfiguresNginxInstancesWithInheritedPortConfig() {
        loadBalancerCluster = app.createAndManageChild(EntitySpec.create(LoadBalancerCluster.class)
                .configure(LoadBalancerCluster.MEMBER_SPEC, nginxSpec)
                .configure("initialSize", 1)
                .configure(NginxController.DOMAIN_NAME, "localhost")
                .configure(NginxController.PROXY_HTTP_PORT, PortRanges.fromString("8765+")));
        
        app.start(ImmutableList.of(localhostProvisioningLoc));
        
        NginxController nginx1 = Iterables.getOnlyElement(findNginxs());

        loadBalancerCluster.resize(2);
        NginxController nginx2 = Iterables.getOnlyElement(Iterables.filter(findNginxs(), 
                Predicates.not(Predicates.in(ImmutableList.of(nginx1)))));

        assertEquals((int) nginx1.getAttribute(NginxController.PROXY_HTTP_PORT), 8765);
        assertEquals((int) nginx2.getAttribute(NginxController.PROXY_HTTP_PORT), 8766);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<NginxController> findNginxs() {
        ImmutableList result = ImmutableList.copyOf(Iterables.filter(app.getManagementContext().getEntityManager().getEntities(), Predicates.instanceOf(NginxController.class)));
        return (List<NginxController>) result;
    }

    private List<String> findNginxRootUrls() {
        List<String> result = Lists.newArrayList();
        for (NginxController nginx : findNginxs()) {
            result.add(nginx.getAttribute(NginxController.ROOT_URL));
        }
        return result;
    }

    private void assertNginxsResponsiveEvenutally(final Iterable<NginxController> nginxs) {
        assertNginxsResponsiveEvenutally(nginxs, null, Collections.<String>emptyList());
    }

    private void assertNginxsResponsiveEvenutally(final Iterable<NginxController> nginxs, final String hostname, final List<String> pathsFor200) {
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                for (NginxController nginx : nginxs) {
                    assertTrue(nginx.getAttribute(NginxController.SERVICE_UP));
                    
                    String normalRootUrl = nginx.getAttribute(NginxController.ROOT_URL);
                    int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
                    String rootUrl = (hostname != null) ? ("http://"+hostname+":"+port+"/") : normalRootUrl;
                    
                    String wrongUrl = rootUrl+"doesnotexist";
                    HttpTestUtils.assertHttpStatusCodeEquals(wrongUrl, 404);
                    
                    for (String pathFor200 : pathsFor200) {
                        String url = rootUrl+pathFor200;
                        HttpTestUtils.assertHttpStatusCodeEquals(url, 200);
                    }
                }
            }});
    }

    private void assertNoDuplicates(Iterable<String> c) {
        assertEquals(Iterables.size(c), ImmutableSet.copyOf(c).size(), "c="+c);
    }
}
