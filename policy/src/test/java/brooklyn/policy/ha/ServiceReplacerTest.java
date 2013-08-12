package brooklyn.policy.ha;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.FailingEntity;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.policy.ha.HASensors.FailureDescriptor;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class ServiceReplacerTest {

    private ManagementContext managementContext;
    private TestApplication app;
    private SimulatedLocation loc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = Entities.newManagementContext();
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        loc = managementContext.getLocationManager().createLocation(LocationSpec.spec(SimulatedLocation.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testReplacesFailedMember() throws Exception {
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntity.class))
                .configure(DynamicCluster.INITIAL_SIZE, 3));
        app.start(ImmutableList.<Location>of(loc));

        ServiceReplacer policy = new ServiceReplacer(new ConfigBag().configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        cluster.addPolicy(policy);

        final Set<Entity> initialMembers = ImmutableSet.copyOf(cluster.getMembers());
        final TestEntity e1 = (TestEntity) Iterables.get(initialMembers, 1);
        
        e1.emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));
        
        // Expect e1 to be replaced
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Set<Entity> newMembers = Sets.difference(ImmutableSet.copyOf(cluster.getMembers()), initialMembers);
                Set<Entity> removedMembers = Sets.difference(initialMembers, ImmutableSet.copyOf(cluster.getMembers()));
                assertEquals(removedMembers, ImmutableSet.of(e1));
                assertEquals(newMembers.size(), 1);
                assertEquals(((TestEntity)Iterables.getOnlyElement(newMembers)).getCallHistory(), ImmutableList.of("start"));
                assertFalse(Entities.isManaged(e1));
            }});
    }
    
    // fails the startup of the replacement entity (but not the original). 
    @Test
    public void testSetsOnFireWhenFailToReplaceMember() throws Exception {
        Predicate<FailingEntity> whetherToFail = new Predicate<FailingEntity>() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override public boolean apply(FailingEntity input) {
                return counter.incrementAndGet() >= 2;
            }
        };
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(FailingEntity.class)
                        .configure(FailingEntity.FAIL_ON_START_CONDITION, whetherToFail))
                .configure(DynamicCluster.INITIAL_SIZE, 1)
                .configure(DynamicCluster.QUARANTINE_FAILED_ENTITIES, true));
        app.start(ImmutableList.<Location>of(loc));
        
        ServiceReplacer policy = new ServiceReplacer(new ConfigBag().configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, HASensors.ENTITY_FAILED));
        cluster.addPolicy(policy);
        
        final Set<Entity> initialMembers = ImmutableSet.copyOf(cluster.getMembers());
        final TestEntity e1 = (TestEntity) Iterables.get(initialMembers, 0);
        
        e1.emit(HASensors.ENTITY_FAILED, new FailureDescriptor(e1, "simulate failure"));
        
        // Expect cluster to go on-fire when fails to start replacement
        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
        
        // And expect to have the second failed entity still kicking around as proof (in quarantine)
        Iterable<Entity> members = Iterables.filter(managementContext.getEntityManager().getEntities(), Predicates.instanceOf(FailingEntity.class));
        assertEquals(Iterables.size(members), 2);
    }
}
