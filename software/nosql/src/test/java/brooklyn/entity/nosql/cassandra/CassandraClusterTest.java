package brooklyn.entity.nosql.cassandra;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class CassandraClusterTest {

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
        cluster = app.createAndManageChild(EntitySpec.create(CassandraCluster.class)
                .configure(CassandraCluster.INITIAL_SIZE, 2)
                .configure(CassandraCluster.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraCluster.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));

        app.start(ImmutableList.of(loc));
        EmptySoftwareProcess e1 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 0);
        EmptySoftwareProcess e2 = (EmptySoftwareProcess) Iterables.get(cluster.getMembers(), 1);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.CURRENT_SEEDS, ImmutableSet.<Entity>of(e1, e2));
        
        ((EntityInternal)e1).setAttribute(Attributes.SERVICE_UP, false);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.CURRENT_SEEDS, ImmutableSet.<Entity>of(e2));

        cluster.resize(3);
        EmptySoftwareProcess e3 = (EmptySoftwareProcess) Iterables.getOnlyElement(Sets.difference(ImmutableSet.copyOf(cluster.getMembers()), ImmutableSet.of(e1,e2)));
        EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.CURRENT_SEEDS, ImmutableSet.<Entity>of(e2, e3));
    }
}
