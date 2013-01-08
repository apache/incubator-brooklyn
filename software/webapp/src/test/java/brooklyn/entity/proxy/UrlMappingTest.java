package brooklyn.entity.proxy;

import static org.testng.Assert.assertEquals;

import java.io.File;

import javax.annotation.Nullable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.nginx.UrlMapping;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class UrlMappingTest {
    
    private final int initialClusterSize = 2;
    
    private ClassLoader classLoader = getClass().getClassLoader();
    private LocalManagementContext managementContext;
    private File mementoDir;
    
    private TestApplication app;
    private DynamicCluster cluster;
    private UrlMapping urlMapping;
    
    @BeforeMethod(alwaysRun=true)
    public void setup() {
        mementoDir = Files.createTempDir();
        managementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);

        app = new TestApplication();
        
        EntityFactory<StubAppServer> serverFactory = new BasicConfigurableEntityFactory<StubAppServer>(StubAppServer.class);
        cluster = new DynamicCluster(
                MutableMap.of("initialSize", initialClusterSize, "factory", serverFactory), 
                app);

        urlMapping = new UrlMapping(
                MutableMap.builder()
                        .put("domain", "localhost")
                        .put("target", cluster)
                        .build(),
                app);

        Entities.startManagement(app, managementContext);
        
        app.start(ImmutableList.of(new LocalhostMachineProvisioningLocation()));
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroy(app);
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    @Test(groups = "Integration")
    public void testTargetMappingsMatchesClusterMembers() {
        // Check updates its TARGET_ADDRESSES (through async subscription)
        assertExpectedTargetsEventually(cluster.getMembers());
    }
    
    @Test(groups = "Integration")
    public void testTargetMappingsRemovesUnmanagedMember() {
        Iterable<StubAppServer> members = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        StubAppServer target1 = Iterables.get(members, 0);
        StubAppServer target2 = Iterables.get(members, 1);
        
        // First wait for targets to be listed
        assertExpectedTargetsEventually(members);
        
        // Unmanage one member, and expect the URL Mapping to be updated accordingly
        Entities.unmanage(target1);

        assertExpectedTargetsEventually(ImmutableSet.of(target2));
    }
    
    @Test(groups = "Integration")
    public void testTargetMappingsRemovesDownMember() {
        Iterable<StubAppServer> members = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        StubAppServer target1 = Iterables.get(members, 0);
        StubAppServer target2 = Iterables.get(members, 1);
        
        // First wait for targets to be listed
        assertExpectedTargetsEventually(members);
        
        // Stop one member, and expect the URL Mapping to be updated accordingly
        target1.setAttribute(StubAppServer.SERVICE_UP, false);

        assertExpectedTargetsEventually(ImmutableSet.of(target2));
    }
    
    @Test(groups = "Integration")
    public void testTargetMappingUpdatesAfterRebind() throws Exception {
        Iterable<StubAppServer> members = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        assertExpectedTargetsEventually(members);
        
        rebind();
        
        Iterable<StubAppServer> members2 = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        StubAppServer target1 = Iterables.get(members2, 0);
        StubAppServer target2 = Iterables.get(members2, 1);

        // Expect to have existing targets
        assertExpectedTargetsEventually(ImmutableSet.of(target1, target2));

        // Add a new member; expect member to be added
        cluster.resize(3);
        StubAppServer target3 = Iterables.get(Iterables.filter(cluster.getChildren(), StubAppServer.class), 2);
        assertExpectedTargetsEventually(ImmutableSet.of(target1, target2, target3));
        
        // Stop one member, and expect the URL Mapping to be updated accordingly
        target1.setAttribute(StubAppServer.SERVICE_UP, false);
        assertExpectedTargetsEventually(ImmutableSet.of(target2, target3));

        // Unmanage a member, and expect the URL Mapping to be updated accordingly
        Entities.unmanage(target2);
        assertExpectedTargetsEventually(ImmutableSet.of(target3));
    }
    
    private void assertExpectedTargetsEventually(final Iterable<? extends Entity> members) {
        TestUtils.executeUntilSucceeds(new Runnable() {
            public void run() {
                Iterable<String> expectedTargets = Iterables.transform(members, new Function<Entity,String>() {
                        @Override public String apply(@Nullable Entity input) {
                            return input.getAttribute(Attributes.HOSTNAME)+":"+input.getAttribute(Attributes.HTTP_PORT);
                        }});
                
                assertEquals(ImmutableSet.copyOf(urlMapping.getAttribute(UrlMapping.TARGET_ADDRESSES)), ImmutableSet.copyOf(expectedTargets));
                assertEquals(urlMapping.getAttribute(UrlMapping.TARGET_ADDRESSES).size(), Iterables.size(members));
            }});
    }
    
    private void rebind() throws Exception {
        RebindTestUtils.waitForPersisted(app);
        
        // Stop the old management context, so original nginx won't interfere
        managementContext.terminate();
        
        app = (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        cluster = (DynamicCluster) Iterables.find(app.getChildren(), Predicates.instanceOf(DynamicCluster.class));
        urlMapping = (UrlMapping) Iterables.find(app.getChildren(), Predicates.instanceOf(UrlMapping.class));
    }
}
