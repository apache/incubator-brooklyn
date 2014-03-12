package brooklyn.entity.group;

import static brooklyn.entity.group.DynamicMultiGroup.BUCKET_FUNCTION;
import static brooklyn.entity.group.DynamicMultiGroup.ENTITY_PROVIDER;
import static brooklyn.entity.group.DynamicMultiGroupImpl.bucketFromAttribute;
import static brooklyn.entity.group.DynamicMultiGroupImpl.iterableForChildren;
import static com.google.common.collect.Iterables.find;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;


public class DynamicMultiGroupTest {

    private static final AttributeSensor<String> SENSOR = new BasicAttributeSensor<String>(String.class, "multigroup.test");
    private static final ImmutableMap<String, Duration> ASSERT_FLAGS = ImmutableMap.of("timeout", Duration.FIVE_SECONDS);

    private TestApplication app;


    @BeforeMethod
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        app.start(ImmutableList.of(new SimulatedLocation()));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (app != null)
            Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testDistributionBySensor() {
        final Group source = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        final DynamicMultiGroup dmg = app.createAndManageChild(
                EntitySpec.create(DynamicMultiGroup.class)
                .configure(BUCKET_FUNCTION, bucketFromAttribute(SENSOR))
                .configure(ENTITY_PROVIDER, iterableForChildren(source))
        );
        app.subscribeToChildren(source, SENSOR, new SensorEventListener<String>() {
            public void onEvent(SensorEvent<String> event) { dmg.distributeEntities(); }
        });

        final EntitySpec<TestEntity> childSpec = EntitySpec.create(TestEntity.class);
        final TestEntity child1 = source.addChild(childSpec.displayName("child1"));
        final TestEntity child2 = source.addChild(childSpec.displayName("child2"));
        Entities.manage(child1);
        Entities.manage(child2);

        // Start with both children in bucket A; there is no bucket B
        child1.setAttribute(SENSOR, "bucketA");
        child2.setAttribute(SENSOR, "bucketA");
        Asserts.succeedsEventually(ASSERT_FLAGS, new Runnable() {
            public void run() {
                Group bucketA = (Group) find(dmg.getChildren(), withDisplayName("bucketA"), null);
                Group bucketB = (Group) find(dmg.getChildren(), withDisplayName("bucketB"), null);
                assertNotNull(bucketA);
                assertNull(bucketB);
                assertTrue(bucketA.getMembers().contains(child1));
                assertTrue(bucketA.getMembers().contains(child2));
            }
        });

        // Move child 1 into bucket B
        child1.setAttribute(SENSOR, "bucketB");
        Asserts.succeedsEventually(ASSERT_FLAGS, new Runnable() {
            public void run() {
                Group bucketA = (Group) find(dmg.getChildren(), withDisplayName("bucketA"), null);
                Group bucketB = (Group) find(dmg.getChildren(), withDisplayName("bucketB"), null);
                assertNotNull(bucketA);
                assertNotNull(bucketB);
                assertTrue(bucketB.getMembers().contains(child1));
                assertTrue(bucketA.getMembers().contains(child2));
            }
        });

        // Move child 2 into bucket B; there is now no bucket A
        child2.setAttribute(SENSOR, "bucketB");
        Asserts.succeedsEventually(ASSERT_FLAGS, new Runnable() {
            public void run() {
                Group bucketA = (Group) find(dmg.getChildren(), withDisplayName("bucketA"), null);
                Group bucketB = (Group) find(dmg.getChildren(), withDisplayName("bucketB"), null);
                assertNull(bucketA);
                assertNotNull(bucketB);
                assertTrue(bucketB.getMembers().contains(child1));
                assertTrue(bucketB.getMembers().contains(child2));
            }
        });

        // Add new child 3, associated with new bucket C
        final TestEntity child3 = source.addChild(childSpec.displayName("child3"));
        Entities.manage(child3);
        child3.setAttribute(SENSOR, "bucketC");
        Asserts.succeedsEventually(ASSERT_FLAGS, new Runnable() {
            public void run() {
                Group bucketC = (Group) find(dmg.getChildren(), withDisplayName("bucketC"), null);
                assertNotNull(bucketC);
                assertTrue(bucketC.getMembers().contains(child3));
            }
        });

        // Un-set the sensor on child 3 -- gets removed from bucket C, which then
        // disappears as it is empty.
        child3.setAttribute(SENSOR, null);
        Asserts.succeedsEventually(ASSERT_FLAGS, new Runnable() {
            public void run() {
                Group bucketB = (Group) find(dmg.getChildren(), withDisplayName("bucketB"), null);
                Group bucketC = (Group) find(dmg.getChildren(), withDisplayName("bucketC"), null);
                assertNotNull(bucketB);
                assertNull(bucketC);
                assertFalse(bucketB.getMembers().contains(child3));
            }
        });
    }

    private static Predicate<Entity> withDisplayName(final String displayName) {
        return new Predicate<Entity>() {
            public boolean apply(Entity e) { return e.getDisplayName().equals(displayName); }
        };
    }

}
