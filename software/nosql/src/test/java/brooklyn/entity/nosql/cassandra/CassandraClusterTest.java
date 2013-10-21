package brooklyn.entity.nosql.cassandra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.EmptySoftwareProcessSshDriver;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class CassandraClusterTest {

    private static final Logger log = LoggerFactory.getLogger(CassandraClusterTest.class);
    
    private ManagementContext managementContext;
    private TestApplication app;
    private LocalhostMachineProvisioningLocation loc;
    private CassandraCluster cluster;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        managementContext = app.getManagementContext();
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testPopulatesInitialSeeds() throws Exception {
        cluster = app.createAndManageChild(EntitySpec.create(CassandraCluster.class)
                .configure(CassandraCluster.INITIAL_SIZE, 2)
                .configure(CassandraCluster.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraCluster.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));

        app.start(ImmutableList.of(loc));
        EmptySoftwareProcess e1 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 0);
        EmptySoftwareProcess e2 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 1);
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.CURRENT_SEEDS, ImmutableSet.<Entity>of(e1, e2));
    }
    
    @Test
    public void testUpdatesSeedsOnFailuresAndAdditions() throws Exception {
        doTestUpdatesSeedsOnFailuresAndAdditions(true, false);
    }
    
    protected void doTestUpdatesSeedsOnFailuresAndAdditions(boolean fast, boolean checkSeedsConstantOnRejoining) throws Exception {
        cluster = app.createAndManageChild(EntitySpec.create(CassandraCluster.class)
                .configure(CassandraCluster.INITIAL_SIZE, 2)
                .configure(CassandraCluster.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraCluster.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));

        app.start(ImmutableList.of(loc));
        EmptySoftwareProcess e1 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 0);
        EmptySoftwareProcess e2 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 1);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.CURRENT_SEEDS, ImmutableSet.<Entity>of(e1, e2));
        log.debug("Test "+JavaClassNames.niceClassAndMethod()+", cluster "+cluster+" has "+cluster.getMembers()+"; e1="+e1+" e2="+e2);
        
        // calling the driver stop for this entity will cause SERVICE_UP to become false, and stay false
        // (and that's all it does, incidentally); if we just set the attribute it will become true on serviceUp sensor feed
        ((EmptySoftwareProcess)e1).getDriver().stop();
        // not necessary, but speeds things up:
        if (fast)
            ((EntityInternal)e1).setAttribute(Attributes.SERVICE_UP, false);
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.CURRENT_SEEDS, ImmutableSet.<Entity>of(e2));

        cluster.resize(3);
        EmptySoftwareProcess e3 = (EmptySoftwareProcess) Iterables.getOnlyElement(Sets.difference(ImmutableSet.copyOf(cluster.getMembers()), ImmutableSet.of(e1,e2)));
        log.debug("Test "+JavaClassNames.niceClassAndMethod()+", cluster "+cluster+" has "+cluster.getMembers()+"; e3="+e3);
        try {
            EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.CURRENT_SEEDS, ImmutableSet.<Entity>of(e2, e3));
        } finally {
            log.debug("Test "+JavaClassNames.niceClassAndMethod()+", cluster "+cluster+" has "+cluster.getMembers()+"; seeds "+cluster.getAttribute(CassandraCluster.CURRENT_SEEDS));
        }
        
        if (!checkSeedsConstantOnRejoining) {
            // cluster should not revert to e1+e2, simply because e1 has come back; but e1 should rejoin the group
            // (not that important, and waits for 1s, so only done as part of integration)
            ((EmptySoftwareProcessSshDriver)(((EmptySoftwareProcess)e1).getDriver())).launch();
            if (fast)
                ((EntityInternal)e1).setAttribute(Attributes.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsEventually(e1, CassandraNode.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsContinually(cluster, CassandraCluster.CURRENT_SEEDS, ImmutableSet.<Entity>of(e2, e3));
        }
    }
    
    @Test(groups="Integration")
    public void testUpdatesSeedsFastishManyTimes() throws Exception {
        final int COUNT = 20;
        for (int i=0; i<COUNT; i++) {
            log.info("Test "+JavaClassNames.niceClassAndMethod()+", iteration "+(i+1)+" of "+COUNT);
            doTestUpdatesSeedsOnFailuresAndAdditions(true, true);
            tearDown();
            setUp();
        }
    }
    
    @Test(groups="Integration")
    public void testUpdateSeedsSlowAndRejoining() throws Exception {
        final int COUNT = 1;
        for (int i=0; i<COUNT; i++) {
            log.info("Test "+JavaClassNames.niceClassAndMethod()+", iteration "+(i+1)+" of "+COUNT);
            doTestUpdatesSeedsOnFailuresAndAdditions(false, true);
            tearDown();
            setUp();
        }
    }

}
