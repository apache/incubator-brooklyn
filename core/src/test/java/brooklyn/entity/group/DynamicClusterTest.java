package brooklyn.entity.group;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.FailingEntity;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.Task;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Atomics;


public class DynamicClusterTest {

    private static final int TIMEOUT_MS = 2000;

    TestApplication app;
    SimulatedLocation loc;
    SimulatedLocation loc2;
    Random random = new Random();

    @BeforeMethod
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        loc = new SimulatedLocation();
        loc2 = new SimulatedLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void creationOkayWithoutNewEntityFactoryArgument() throws Exception {
        app.createAndManageChild(EntitySpec.create(DynamicCluster.class));
    }

    @Test
    public void constructionRequiresThatNewEntityArgumentIsAnEntityFactory() throws Exception {
        try {
            app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                    .configure("factory", "error"));
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, IllegalArgumentException.class) == null) throw e;
        }
    }

    @Test
    public void startRequiresThatNewEntityArgumentIsGiven() throws Exception {
        DynamicCluster c = app.createAndManageChild(EntitySpec.create(DynamicCluster.class));
        try {
            c.start(ImmutableList.of(loc));
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) throw e;
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterHasMoreThanOneElement() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));
        try {
            cluster.start(ImmutableList.of(loc, loc2));
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, IllegalArgumentException.class) == null) throw e;
        }
    }

    @Test
    public void testClusterHasOneLocationAfterStarting() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));
        cluster.start(ImmutableList.of(loc));
        assertEquals(cluster.getLocations().size(), 1);
        assertEquals(ImmutableList.copyOf(cluster.getLocations()), ImmutableList.of(loc));
    }

    @Test
    public void testServiceUpAfterStartingWithNoMembers() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntity.class))
                .configure(DynamicCluster.INITIAL_SIZE, 0));
        cluster.start(ImmutableList.of(loc));
        assertEquals(cluster.getAttribute(Attributes.SERVICE_STATE), Lifecycle.RUNNING);
        assertTrue(cluster.getAttribute(Attributes.SERVICE_UP));
    }

    @Test
    public void usingEntitySpecResizeFromZeroToOneStartsANewEntityAndSetsItsParent() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("memberSpec", EntitySpec.create(TestEntity.class)));
        
        cluster.start(ImmutableList.of(loc));

        cluster.resize(1);
        TestEntity entity = (TestEntity) Iterables.getOnlyElement(cluster.getMembers());
        assertEquals(entity.getCount(), 1);
        assertEquals(entity.getParent(), cluster);
        assertEquals(entity.getApplication(), app);
    }

    @Test
    public void resizeFromZeroToOneStartsANewEntityAndSetsItsParent() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }}));

        cluster.start(ImmutableList.of(loc));

        cluster.resize(1);
        TestEntity entity = (TestEntity) Iterables.get(cluster.getMembers(), 0);
        assertEquals(entity.getCounter().get(), 1);
        assertEquals(entity.getParent(), cluster);
        assertEquals(entity.getApplication(), app);
    }

    @Test
    public void currentSizePropertyReflectsActualClusterSize() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }}));


        assertEquals(cluster.getCurrentSize(), (Integer)0);

        cluster.start(ImmutableList.of(loc));
        assertEquals(cluster.getCurrentSize(), (Integer)1);
        assertEquals(cluster.getAttribute(Changeable.GROUP_SIZE), (Integer)1);

        int newSize = cluster.resize(0);
        assertEquals(newSize, 0);
        assertEquals((Integer)newSize, cluster.getCurrentSize());
        assertEquals(newSize, cluster.getMembers().size());
        assertEquals((Integer)newSize, cluster.getAttribute(Changeable.GROUP_SIZE));

        newSize = cluster.resize(4);
        assertEquals(newSize, 4);
        assertEquals((Integer)newSize, cluster.getCurrentSize());
        assertEquals(newSize, cluster.getMembers().size());
        assertEquals((Integer)newSize, cluster.getAttribute(Changeable.GROUP_SIZE));

        newSize = cluster.resize(0);
        assertEquals(newSize, 0);
        assertEquals((Integer)newSize, cluster.getCurrentSize());
        assertEquals(newSize, cluster.getMembers().size());
        assertEquals((Integer)newSize, cluster.getAttribute(Changeable.GROUP_SIZE));
    }

    @Test
    public void clusterSizeAfterStartIsInitialSize() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }})
                .configure("initialSize", 2));

        cluster.start(ImmutableList.of(loc));
        assertEquals(cluster.getCurrentSize(), (Integer)2);
        assertEquals(cluster.getMembers().size(), 2);
        assertEquals(cluster.getAttribute(Changeable.GROUP_SIZE), (Integer)2);
    }

    @Test
    public void clusterLocationIsPassedOnToEntityStart() throws Exception {
        List<SimulatedLocation> locations = ImmutableList.of(loc);
        final AtomicReference<Collection<? extends Location>> stashedLocations = Atomics.newReference();
        
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(parent) {
                            @Override
                            public void start(Collection<? extends Location> loc) {
                                super.start(loc);
                                stashedLocations.set(loc);
                            }
                        };
                    }})
                .configure("initialSize", 1));

        cluster.start(locations);
        TestEntity entity = (TestEntity) Iterables.get(cluster.getMembers(), 0);
        
        assertNotNull(stashedLocations.get());
        assertEquals(stashedLocations.get().size(), 1);
        assertEquals(ImmutableList.copyOf(stashedLocations.get()), locations);
    }

    @Test
    public void resizeFromOneToZeroChangesClusterSize() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }})
                .configure("initialSize", 1));

        cluster.start(ImmutableList.of(loc));
        TestEntity entity = (TestEntity) Iterables.get(cluster.getMembers(), 0);
        assertEquals(cluster.getCurrentSize(), (Integer)1);
        assertEquals(entity.getCounter().get(), 1);
        
        cluster.resize(0);
        assertEquals(cluster.getCurrentSize(), (Integer)0);
        assertEquals(entity.getCounter().get(), 0);
    }

    @Test
    public void concurrentResizesToSameNumberCreatesCorrectNumberOfNodes() throws Exception {
        final int OVERHEAD_MS = 500;
        final int STARTUP_TIME_MS = 50;
        final AtomicInteger numStarted = new AtomicInteger(0);
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        Time.sleep(STARTUP_TIME_MS);
                        numStarted.incrementAndGet();
                        return new TestEntityImpl(flags, parent);
                    }}));

        assertEquals(cluster.getCurrentSize(), (Integer)0);
        cluster.start(ImmutableList.of(loc));

        ExecutorService executor = Executors.newCachedThreadPool();
        final List<Throwable> throwables = new CopyOnWriteArrayList<Throwable>();

        try {
            for (int i = 0; i < 10; i++) {
                executor.submit(new Runnable() {
                    public void run() {
                        try {
                            cluster.resize(2);
                        } catch (Throwable e) {
                            throwables.add(e);
                        }
                    }});
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(10*STARTUP_TIME_MS+OVERHEAD_MS, TimeUnit.MILLISECONDS));
            if (throwables.size() > 0) throw Exceptions.propagate(throwables.get(0));
            assertEquals(cluster.getCurrentSize(), (Integer)2);
            assertEquals(cluster.getAttribute(Changeable.GROUP_SIZE), (Integer)2);
            assertEquals(numStarted.get(), 2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(enabled = false)
    public void stoppingTheClusterStopsTheEntity() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }})
                .configure("initialSize", 1));

        cluster.start(ImmutableList.of(loc));
        TestEntity entity = (TestEntity) Iterables.get(cluster.getMembers(), 0);
        
        assertEquals(entity.getCounter().get(), 1);
        cluster.stop();
        assertEquals(entity.getCounter().get(), 0);
    }

    /**
     * This tests the fix for ENGR-1826.
     */
    @Test
    public void failingEntitiesDontBreakClusterActions() throws Exception {
        final int failNum = 2;
        final AtomicInteger counter = new AtomicInteger(0);
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        int num = counter.incrementAndGet();
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(FailingEntity.class)
                                .configure(flags)
                                .configure(FailingEntity.FAIL_ON_START, (num==failNum))
                                .parent(parent));
                    }}));

        cluster.start(ImmutableList.of(loc));
        cluster.resize(3);
        assertEquals(cluster.getCurrentSize(), (Integer)2);
        assertEquals(cluster.getMembers().size(), 2);
        for (Entity member : cluster.getMembers()) {
            assertFalse(((FailingEntity)member).getConfig(FailingEntity.FAIL_ON_START));
        }
    }

    @Test
    public void testInitialQuorumSizeSufficientForStartup() throws Exception {
        final int failNum = 1;
        final AtomicInteger counter = new AtomicInteger(0);
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 2)
                .configure(DynamicCluster.INITIAL_QUORUM_SIZE, 1)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        int num = counter.incrementAndGet();
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(FailingEntity.class)
                                .configure(flags)
                                .configure(FailingEntity.FAIL_ON_START, (num==failNum))
                                .parent(parent));
                    }}));

        cluster.start(ImmutableList.of(loc));
        
        // note that children include quarantine group; and quarantined nodes
        assertEquals(cluster.getCurrentSize(), (Integer)1);
        assertEquals(cluster.getMembers().size(), 1);
        for (Entity member : cluster.getMembers()) {
            assertFalse(((FailingEntity)member).getConfig(FailingEntity.FAIL_ON_START));
        }
    }

    @Test
    public void testInitialQuorumSizeDefaultsToInitialSize() throws Exception {
        final int failNum = 1;
        final AtomicInteger counter = new AtomicInteger(0);
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 2)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        int num = counter.incrementAndGet();
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(FailingEntity.class)
                                .configure(flags)
                                .configure(FailingEntity.FAIL_ON_START, (num==failNum))
                                .parent(parent));
                    }}));

        try {
            cluster.start(ImmutableList.of(loc));
        } catch (Exception e) {
            IllegalStateException unwrapped = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (unwrapped != null && unwrapped.getMessage().contains("failed to get to initial size")) {
                // success
            } else {
                throw e; // fail
            }
        }
        
        // note that children include quarantine group; and quarantined nodes
        assertEquals(cluster.getCurrentSize(), (Integer)1);
        assertEquals(cluster.getMembers().size(), 1);
        for (Entity member : cluster.getMembers()) {
            assertFalse(((FailingEntity)member).getConfig(FailingEntity.FAIL_ON_START));
        }
    }

    @Test
    public void testQuarantineGroupOfCorrectType() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("quarantineFailedEntities", true)
                .configure("initialSize", 0)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TestEntity.class)));

        cluster.start(ImmutableList.of(loc));
        
        QuarantineGroup quarantineGroup = cluster.getAttribute(DynamicCluster.QUARANTINE_GROUP);
        quarantineGroup.expungeMembers(true); // sanity check by calling something on it
    }

    @Test
    public void testCanQuarantineFailedEntities() throws Exception {
        final int failNum = 2;
        final AtomicInteger counter = new AtomicInteger(0);
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("quarantineFailedEntities", true)
                .configure("initialSize", 0)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        int num = counter.incrementAndGet();
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(FailingEntity.class)
                                .configure(flags)
                                .configure(FailingEntity.FAIL_ON_START, (num==failNum))
                                .parent(parent));
                    }}));

        cluster.start(ImmutableList.of(loc));
        cluster.resize(3);
        assertEquals(cluster.getCurrentSize(), (Integer)2);
        assertEquals(cluster.getMembers().size(), 2);
        assertEquals(Iterables.size(Iterables.filter(cluster.getChildren(), Predicates.instanceOf(FailingEntity.class))), 3);
        for (Entity member : cluster.getMembers()) {
            assertFalse(((FailingEntity)member).getConfig(FailingEntity.FAIL_ON_START));
        }

        assertEquals(cluster.getAttribute(DynamicCluster.QUARANTINE_GROUP).getMembers().size(), 1);
        for (Entity member : cluster.getAttribute(DynamicCluster.QUARANTINE_GROUP).getMembers()) {
            assertTrue(((FailingEntity)member).getConfig(FailingEntity.FAIL_ON_START));
        }
    }

    @Test
    public void testDoNotQuarantineFailedEntities() throws Exception {
        final int failNum = 2;
        final AtomicInteger counter = new AtomicInteger(0);
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                // default is quarantineFailedEntities==true
                .configure("quarantineFailedEntities", false)
                .configure("initialSize", 0)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        int num = counter.incrementAndGet();
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(FailingEntity.class)
                                .configure(flags)
                                .configure(FailingEntity.FAIL_ON_START, (num==failNum))
                                .parent(parent));
                    }}));

        cluster.start(ImmutableList.of(loc));
        
        // no quarantine group, as a child
        assertEquals(cluster.getChildren().size(), 0, "children="+cluster.getChildren());
        
        // Failed node will not be a member or child
        cluster.resize(3);
        assertEquals(cluster.getCurrentSize(), (Integer)2);
        assertEquals(cluster.getMembers().size(), 2);
        assertEquals(cluster.getChildren().size(), 2, "children="+cluster.getChildren());
        
        // Failed node will not be managed either
        assertEquals(Iterables.size(Iterables.filter(cluster.getChildren(), Predicates.instanceOf(FailingEntity.class))), 2);
        for (Entity member : cluster.getMembers()) {
            assertFalse(((FailingEntity)member).getConfig(FailingEntity.FAIL_ON_START));
        }
    }

    @Test
    public void defaultRemovalStrategyShutsDownNewestFirstWhenResizing() throws Exception {
        final List<Entity> creationOrder = Lists.newArrayList();
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        Entity result = new TestEntityImpl(flags);
                        creationOrder.add(result);
                        return result;
                    }}));

        cluster.start(ImmutableList.of(loc));
        cluster.resize(1);
        cluster.resize(2);
        assertEquals(cluster.getCurrentSize(), (Integer)2);
        assertEquals(ImmutableSet.copyOf(cluster.getMembers()), ImmutableSet.copyOf(creationOrder), "actual="+cluster.getMembers());

        // Now stop one
        cluster.resize(1);
        assertEquals(cluster.getCurrentSize(), (Integer)1);
        assertEquals(ImmutableList.copyOf(cluster.getMembers()), creationOrder.subList(0, 1));
    }

    @Test
    public void resizeLoggedAsEffectorCall() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }}));

        app.start(ImmutableList.of(loc));
        cluster.resize(1);

        Set<Task<?>> tasks = app.getManagementContext().getExecutionManager().getTasksWithAllTags(ImmutableList.of(
            BrooklynTaskTags.tagForContextEntity(cluster),"EFFECTOR"));
        assertEquals(tasks.size(), 2);
        assertTrue(Iterables.get(tasks, 0).getDescription().contains("start"));
        assertTrue(Iterables.get(tasks, 1).getDescription().contains("resize"));
    }

    @Test
    public void testStoppedChildIsRemoveFromGroup() throws Exception {
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }}));

        cluster.start(ImmutableList.of(loc));

        final TestEntity child = (TestEntity) Iterables.get(cluster.getMembers(), 0);
        child.stop();
        Entities.unmanage(child);

        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                assertFalse(cluster.getChildren().contains(child), "children="+cluster.getChildren());
                assertEquals(cluster.getCurrentSize(), (Integer)0);
                assertEquals(cluster.getMembers().size(), 0);
            }});
    }

    @Test
    public void testPluggableRemovalStrategyIsUsed() throws Exception {
        final List<Entity> removedEntities = Lists.newArrayList();

        Function<Collection<Entity>, Entity> removalStrategy = new Function<Collection<Entity>, Entity>() {
            @Override public Entity apply(Collection<Entity> contenders) {
                Entity choice = Iterables.get(contenders, random.nextInt(contenders.size()));
                removedEntities.add(choice);
                return choice;
            }
        };

        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }})
                .configure("initialSize", 10)
                .configure("removalStrategy", removalStrategy));

        cluster.start(ImmutableList.of(loc));
        Set origMembers = ImmutableSet.copyOf(cluster.getMembers());

        for (int i = 10; i >= 0; i--) {
            cluster.resize(i);
            assertEquals(cluster.getAttribute(Changeable.GROUP_SIZE), (Integer)i);
            assertEquals(removedEntities.size(), 10-i);
            assertEquals(ImmutableSet.copyOf(Iterables.concat(cluster.getMembers(), removedEntities)), origMembers);
        }
    }

    @Test
    public void testPluggableRemovalStrategyCanBeSetAfterConstruction() throws Exception {
        final List<Entity> removedEntities = Lists.newArrayList();

        Function<Collection<Entity>, Entity> removalStrategy = new Function<Collection<Entity>, Entity>() {
            @Override public Entity apply(Collection<Entity> contenders) {
                Entity choice = Iterables.get(contenders, random.nextInt(contenders.size()));
                removedEntities.add(choice);
                return choice;
            }
        };
        
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }})
                .configure("initialSize", 10));

        cluster.start(ImmutableList.of(loc));
        Set origMembers = ImmutableSet.copyOf(cluster.getMembers());

        cluster.setRemovalStrategy(removalStrategy);

        for (int i = 10; i >= 0; i--) {
            cluster.resize(i);
            assertEquals(cluster.getAttribute(Changeable.GROUP_SIZE), (Integer)i);
            assertEquals(removedEntities.size(), 10-i);
            assertEquals(ImmutableSet.copyOf(Iterables.concat(cluster.getMembers(), removedEntities)), origMembers);
        }
    }

    @Test
    public void testResizeDoesNotBlockCallsToQueryGroupMembership() throws Exception {
        final CountDownLatch executingLatch = new CountDownLatch(1);
        final CountDownLatch continuationLatch = new CountDownLatch(1);
        
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        try {
                            executingLatch.countDown();
                            continuationLatch.await();
                            return new TestEntityImpl(flags);
                        } catch (InterruptedException e) {
                            throw Exceptions.propagate(e);
                        }
                    }}));

        cluster.start(ImmutableList.of(loc));

        Thread thread = new Thread(new Runnable() {
                @Override public void run() {
                    cluster.resize(1);
                }});
        
        try {
            // wait for resize to be executing
            thread.start();
            executingLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);

            // ensure can still call methods on group, to query/update membership
            assertEquals(ImmutableList.copyOf(cluster.getMembers()), ImmutableList.of());
            assertEquals(cluster.getCurrentSize(), (Integer)0);
            assertFalse(cluster.hasMember(cluster));
            cluster.addMember(cluster);
            assertTrue(cluster.removeMember(cluster));

            // allow the resize to complete
            continuationLatch.countDown();
            thread.join(TIMEOUT_MS);
            assertFalse(thread.isAlive());
        } finally {
            thread.interrupt();
        }
    }

    @Test
    public void testReplacesMember() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }}));

        cluster.start(ImmutableList.of(loc));
        Entity member = Iterables.get(cluster.getMembers(), 0);

        String replacementId = cluster.replaceMember(member.getId());
        Entity replacement = app.getManagementContext().getEntityManager().getEntity(replacementId);

        assertEquals(cluster.getMembers().size(), 1);
        assertFalse(cluster.getMembers().contains(member));
        assertFalse(cluster.getChildren().contains(member));
        assertNotNull(replacement, "replacementId="+replacementId);
        assertTrue(cluster.getMembers().contains(replacement), "replacement="+replacement+"; members="+cluster.getMembers());
        assertTrue(cluster.getChildren().contains(replacement), "replacement="+replacement+"; children="+cluster.getChildren());
    }

    @Test
    public void testReplaceMemberThrowsIfMemberIdDoesNotResolve() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }}));

        cluster.start(ImmutableList.of(loc));
        Entity member = Iterables.get(cluster.getMembers(), 0);

        try {
            cluster.replaceMember("wrong.id");
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class) == null) throw e;
            if (!Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class).getMessage().contains("entity wrong.id cannot be resolved")) throw e;
        }

        assertEquals(ImmutableSet.copyOf(cluster.getMembers()), ImmutableSet.of(member));
    }

    @Test
    public void testReplaceMemberThrowsIfNotMember() throws Exception {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        return new TestEntityImpl(flags);
                    }}));

        cluster.start(ImmutableList.of(loc));
        Entity member = Iterables.get(cluster.getMembers(), 0);

        try {
            cluster.replaceMember(app.getId());
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class) == null) throw e;
            if (!Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class).getMessage().contains("is not a member")) throw e;
        }

        assertEquals(ImmutableSet.copyOf(cluster.getMembers()), ImmutableSet.of(member));
    }

    @Test
    public void testReplaceMemberFailsIfCantProvisionReplacement() throws Exception {
        final int failNum = 2;
        final AtomicInteger counter = new AtomicInteger(0);
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        int num = counter.incrementAndGet();
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(FailingEntity.class)
                                .configure(flags)
                                .configure(FailingEntity.FAIL_ON_START, (num==failNum))
                                .parent(parent));
                    }}));

        cluster.start(ImmutableList.of(loc));
        Entity member = Iterables.get(cluster.getMembers(), 0);

        try {
            cluster.replaceMember(member.getId());
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("failed to grow")) throw e;
            if (Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class) != null) throw e;
        }
        assertEquals(ImmutableSet.copyOf(cluster.getMembers()), ImmutableSet.of(member));
    }

    @Test
    public void testReplaceMemberRemovesAndThowsIfFailToStopOld() throws Exception {
        final int failNum = 1;
        final AtomicInteger counter = new AtomicInteger(0);
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new EntityFactory() {
                    @Override public Entity newEntity(Map flags, Entity parent) {
                        int num = counter.incrementAndGet();
                        return app.getManagementContext().getEntityManager().createEntity(EntitySpec.create(FailingEntity.class)
                                .configure(flags)
                                .configure(FailingEntity.FAIL_ON_STOP, (num==failNum))
                                .parent(parent));
                    }}));

        cluster.start(ImmutableList.of(loc));
        Entity member = Iterables.get(cluster.getMembers(), 0);

        try {
            cluster.replaceMember(member.getId());
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, StopFailedRuntimeException.class) == null) throw e;
            boolean found = false;
            for (Throwable t : Throwables.getCausalChain(e)) {
                if (t.toString().contains("Simulating entity stop failure")) {
                    found = true;
                    break;
                }
            }
            if (!found) throw e;
        }
        assertFalse(Entities.isManaged(member));
        assertEquals(cluster.getMembers().size(), 1);
    }

    private Throwable unwrapException(Throwable e) {
        if (e instanceof ExecutionException) {
            return unwrapException(e.getCause());
        } else if (e instanceof org.codehaus.groovy.runtime.InvokerInvocationException) {
            return unwrapException(e.getCause());
        } else {
            return e;
        }
    }
}
