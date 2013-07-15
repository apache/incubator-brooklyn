package brooklyn.entity.proxy.nginx;

import static brooklyn.test.TestUtils.urlRespondsStatusCode;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Group;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.LoadBalancerCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.EntityManager;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxClusterIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(NginxClusterIntegrationTest.class);

    private static final long TIMEOUT_MS = 60*1000;
    
    private URL war;
    private LocalhostMachineProvisioningLocation localhostProvisioningLoc;
    private TestApplication app;
    private EntityManager entityManager;
    private LoadBalancerCluster loadBalancerCluster;
    private EntitySpec<NginxController> nginxSpec;
    private Group urlMappings;


    
    @BeforeMethod(groups = "Integration")
    public void setup() throws Exception {
        war = checkNotNull(getClass().getClassLoader().getResource("hello-world.war"), "hello-world.war not on classpath");
        localhostProvisioningLoc = new LocalhostMachineProvisioningLocation(MutableMap.of("address", "localhost"));
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        urlMappings = app.createAndManageChild(EntitySpecs.spec(BasicGroup.class)
                .configure("childrenAsMembers", true));
        entityManager = app.getManagementContext().getEntityManager();
        
        nginxSpec = EntitySpecs.spec(NginxController.class);
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Integration")
    public void testCreatesNginxInstancesAndResizes() {
        loadBalancerCluster = app.createAndManageChild(EntitySpecs.spec(LoadBalancerCluster.class)
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
        DynamicCluster serverPool = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpecs.spec(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.ROOT_WAR, war.getPath()));
        
        loadBalancerCluster = app.createAndManageChild(EntitySpecs.spec(LoadBalancerCluster.class)
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
        DynamicCluster c1 = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpecs.spec(JBoss7Server.class))
                .configure("initialSize", 1)
                .configure(JavaWebAppService.NAMED_WARS, ImmutableList.of(war.getPath())));

        UrlMapping urlMapping = entityManager.createEntity(EntitySpecs.spec(UrlMapping.class)
                .configure("domain", "localhost")
                .configure("path", "/hello-world($|/.*)")
                .configure("target", c1)
                .parent(urlMappings));
        Entities.manage(urlMapping);
        
        loadBalancerCluster = app.createAndManageChild(EntitySpecs.spec(LoadBalancerCluster.class)
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
        loadBalancerCluster = app.createAndManageChild(EntitySpecs.spec(LoadBalancerCluster.class)
                .configure(LoadBalancerCluster.MEMBER_SPEC, nginxSpec)
                .configure("initialSize", 0)
                .configure(NginxController.DOMAIN_NAME, "localhost"));
        
        app.start(ImmutableList.of(localhostProvisioningLoc));
        TestUtils.assertAttributeContinually(loadBalancerCluster, Startable.SERVICE_UP, false);
        
        loadBalancerCluster.resize(1);
        TestUtils.assertAttributeEventually(loadBalancerCluster, Startable.SERVICE_UP, true);

        loadBalancerCluster.resize(0);
        TestUtils.assertAttributeEventually(loadBalancerCluster, Startable.SERVICE_UP, false);
    }
    
    // Warning: test is a little brittle for if a previous run leaves something on these required ports
    @Test(groups = "Integration")
    public void testConfiguresNginxInstancesWithInheritedPortConfig() {
        loadBalancerCluster = app.createAndManageChild(EntitySpecs.spec(LoadBalancerCluster.class)
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
        ImmutableList result = ImmutableList.copyOf(Iterables.filter(app.getManagementContext().getEntities(), Predicates.instanceOf(NginxController.class)));
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

    private void assertNginxsResponsiveEvenutally(final Iterable<NginxController> nginxs, final List<String> pathsFor200) {
        assertNginxsResponsiveEvenutally(nginxs, null, pathsFor200);
    }
    
    private void assertNginxsResponsiveEvenutally(final Iterable<NginxController> nginxs, final String hostname, final List<String> pathsFor200) {
        TestUtils.executeUntilSucceeds(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                for (NginxController nginx : nginxs) {
                    assertTrue(nginx.getAttribute(NginxController.SERVICE_UP));
                    
                    String normalRootUrl = nginx.getAttribute(NginxController.ROOT_URL);
                    int port = nginx.getAttribute(NginxController.PROXY_HTTP_PORT);
                    String rootUrl = (hostname != null) ? ("http://"+hostname+":"+port+"/") : normalRootUrl;
                    
                    String wrongUrl = rootUrl+"doesnotexist";
                    assertEquals(urlRespondsStatusCode(wrongUrl), 404, "url="+wrongUrl);
                    
                    for (String pathFor200 : pathsFor200) {
                        String url = rootUrl+pathFor200;
                        assertEquals(urlRespondsStatusCode(url), 200, "url="+url);
                    }
                }
            }});
    }

    private void assertNoDuplicates(Iterable<String> c) {
        assertEquals(Iterables.size(c), ImmutableSet.copyOf(c).size(), "c="+c);
    }
}
