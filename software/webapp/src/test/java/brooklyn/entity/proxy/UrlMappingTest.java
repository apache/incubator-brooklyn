package brooklyn.entity.proxy;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.HashSet;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.nginx.UrlMapping;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public class UrlMappingTest {
    
    private static final Logger log = LoggerFactory.getLogger(UrlMappingTest.class);
    
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

        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        
        EntityFactory<StubAppServer> serverFactory = new BasicConfigurableEntityFactory<StubAppServer>(StubAppServer.class);
        cluster = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure("initialSize", initialClusterSize)
                .configure("factory", serverFactory));

        urlMapping = app.createAndManageChild(EntitySpecs.spec(UrlMapping.class)
                .configure("domain", "localhost")
                .configure("target", cluster));

        app.start( ImmutableList.of(
                managementContext.getLocationManager().createLocation(
                        LocationSpec.spec(LocalhostMachineProvisioningLocation.class))
                ));
        log.info("app's location managed: "+managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(app.getLocations())));
        log.info("clusters's location managed: "+managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(cluster.getLocations())));
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
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

    // i think no real reason for other methods to be Integration apart from the time they take;
    // having one in the unit tests is very handy however, and this is a good choice because it does quite a lot!
    @Test
    public void testTargetMappingUpdatesAfterRebind() throws Exception {
        log.info("starting testTargetMappingUpdatesAfterRebind");
        Iterable<StubAppServer> members = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        assertExpectedTargetsEventually(members);
        
        Assert.assertTrue(managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(cluster.getLocations())));
        rebind();
        Assert.assertTrue(managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(cluster.getLocations())),
                "location not managed after rebind");
        
        Iterable<StubAppServer> members2 = Iterables.filter(cluster.getChildren(), StubAppServer.class);
        StubAppServer target1 = Iterables.get(members2, 0);
        StubAppServer target2 = Iterables.get(members2, 1);

        // Expect to have existing targets
        assertExpectedTargetsEventually(ImmutableSet.of(target1, target2));

        // Add a new member; expect member to be added
        log.info("resizing "+cluster+" - "+cluster.getChildren());
        Integer result = cluster.resize(3);
        Assert.assertTrue(managementContext.getLocationManager().isManaged(Iterables.getOnlyElement(cluster.getLocations())));
        log.info("resized "+cluster+" ("+result+") - "+cluster.getChildren());
        HashSet<StubAppServer> newEntities = Sets.newHashSet(Iterables.filter(cluster.getChildren(), StubAppServer.class));
        newEntities.remove(target1);
        newEntities.remove(target2);
        StubAppServer target3 = Iterables.getOnlyElement(newEntities);
        log.info("expecting "+ImmutableSet.of(target1, target2, target3));
        assertExpectedTargetsEventually(ImmutableSet.of(target1, target2, target3));
        
        // Stop one member, and expect the URL Mapping to be updated accordingly
        log.info("pretending one node down");
        target1.setAttribute(StubAppServer.SERVICE_UP, false);
        assertExpectedTargetsEventually(ImmutableSet.of(target2, target3));

        // Unmanage a member, and expect the URL Mapping to be updated accordingly
        log.info("unmanaging another node");
        Entities.unmanage(target2);
        assertExpectedTargetsEventually(ImmutableSet.of(target3));
        log.info("success - testTargetMappingUpdatesAfterRebind");
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
        managementContext = (LocalManagementContext) ((EntityInternal)app).getManagementContext();
        cluster = (DynamicCluster) Iterables.find(app.getChildren(), Predicates.instanceOf(DynamicCluster.class));
        urlMapping = (UrlMapping) Iterables.find(app.getChildren(), Predicates.instanceOf(UrlMapping.class));
    }
}
