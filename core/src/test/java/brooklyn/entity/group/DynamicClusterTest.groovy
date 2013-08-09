package brooklyn.entity.group

import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.EntitySpec
import brooklyn.entity.trait.Changeable
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.test.entity.TestEntityImpl
import brooklyn.util.exceptions.Exceptions
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Predicates
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables

class DynamicClusterTest {

    private static final int TIMEOUT_MS = 2000
        
    static { TimeExtras.init() }
    
    TestApplication app
    SimulatedLocation loc
    SimulatedLocation loc2
    Random random = new Random()
    
    @BeforeMethod
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        loc = new SimulatedLocation()
        loc2 = new SimulatedLocation()
    }

    @Test
    public void creationOkayWithoutNewEntityFactoryArgument() {
        app.createAndManageChild(EntitySpec.create(DynamicCluster.class));
    }

    @Test
    public void constructionRequiresThatNewEntityArgumentIsAnEntityFactory() {
        try {
            app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                    .configure("factory", "error"));
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, IllegalArgumentException.class) == null) throw e;
        }
    }

    @Test
    public void startRequiresThatNewEntityArgumentIsGiven() {
        DynamicCluster c = app.createAndManageChild(EntitySpec.create(DynamicCluster.class));
        try {
            c.start([loc]);
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) throw e;
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterIsMissing() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { new TestEntityImpl() }));
        try {
            cluster.start(null)
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) throw e;
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterIsEmpty() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { new TestEntityImpl() }));
        try {
            cluster.start([])
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) throw e;
        }
    }

    @Test
    public void startMethodFailsIfLocationsParameterHasMoreThanOneElement() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { new TestEntityImpl() }));
        try {
            cluster.start([ loc, loc2 ])
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) throw e;
        }
    }

    @Test
    public void testClusterHasOneLocationAfterStarting() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { new TestEntityImpl() }));
        cluster.start([loc])
        assertEquals(cluster.getLocations().size(), 1)
        assertEquals(cluster.getLocations() as List, [loc])
    }

    @Test
    public void usingEntitySpecResizeFromZeroToOneStartsANewEntityAndSetsItsParent() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("memberSpec", EntitySpec.create(TestEntity.class)));
        
        cluster.start([loc])

        cluster.resize(1)
        Entity entity = Iterables.getOnlyElement(cluster.getChildren());
        assertEquals entity.count, 1
        assertEquals entity.parent, cluster
        assertEquals entity.application, app
    }

    @Test
    public void resizeFromZeroToOneStartsANewEntityAndSetsItsParent() {
        TestEntity entity
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> entity = new TestEntityImpl(properties) }));

        cluster.start([loc])

        cluster.resize(1)
        assertEquals entity.counter.get(), 1
        assertEquals entity.parent, cluster
        assertEquals entity.application, app
    }

    @Test
    public void currentSizePropertyReflectsActualClusterSize() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> return new TestEntityImpl(properties) }));
        
        assertEquals cluster.currentSize, 0

        cluster.start([loc])
        assertEquals cluster.currentSize, 1
        assertEquals cluster.getAttribute(Changeable.GROUP_SIZE), 1

        int newSize = cluster.resize(0)
        assertEquals newSize, 0
        assertEquals newSize, cluster.currentSize
        assertEquals newSize, cluster.members.size()
        assertEquals newSize, cluster.getAttribute(Changeable.GROUP_SIZE)

        newSize = cluster.resize(4)
        assertEquals newSize, 4
        assertEquals newSize, cluster.currentSize
        assertEquals newSize, cluster.members.size()
        assertEquals newSize, cluster.getAttribute(Changeable.GROUP_SIZE)
        
        newSize = cluster.resize(0)
        assertEquals newSize, 0
        assertEquals newSize, cluster.currentSize
        assertEquals newSize, cluster.members.size()
        assertEquals newSize, cluster.getAttribute(Changeable.GROUP_SIZE)
    }

    @Test
    public void clusterSizeAfterStartIsInitialSize() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> return new TestEntityImpl(properties) })
                .configure("initialSize", 2));
        
        cluster.start([loc])
        assertEquals cluster.currentSize, 2
        assertEquals cluster.members.size(), 2
        assertEquals cluster.getAttribute(Changeable.GROUP_SIZE), 2
    }

    @Test
    public void clusterLocationIsPassedOnToEntityStart() {
        Collection<Location> locations = [ loc ]
        TestEntity entity
        def newEntity = { properties ->
            entity = new TestEntityImpl(parent:app) {
	            List<Location> stashedLocations = null
	            @Override
	            void start(Collection<? extends Location> loc) {
	                super.start(loc)
	                stashedLocations = loc
	            }
	        }
        }
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", newEntity)
                .configure("initialSize", 1));
        
        cluster.start(locations)

        assertNotNull entity.stashedLocations
        assertEquals entity.stashedLocations.size(), 1
        assertEquals entity.stashedLocations[0], locations[0]
    }

    @Test
    public void resizeFromOneToZeroChangesClusterSize() {
        TestEntity entity
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> entity = new TestEntityImpl(properties) })
                .configure("initialSize", 1));
        
        cluster.start([loc])
        assertEquals cluster.currentSize, 1
        assertEquals entity.counter.get(), 1
        cluster.resize(0)
        assertEquals cluster.currentSize, 0
        assertEquals entity.counter.get(), 0
    }

    @Test
    public void concurrentResizesToSameNumberCreatesCorrectNumberOfNodes() {
        final int OVERHEAD_MS = 500
        final int STARTUP_TIME_MS = 50
        final AtomicInteger numStarted = new AtomicInteger(0)
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { 
                    Map flags, Entity cluster -> 
                    Thread.sleep(STARTUP_TIME_MS); numStarted.incrementAndGet(); new TestEntityImpl(flags, cluster)
                }));
        
        assertEquals cluster.currentSize, 0
        cluster.start([loc])

        ExecutorService executor = Executors.newCachedThreadPool()
        List<Throwable> throwables = new CopyOnWriteArrayList<Throwable>()
        
        try {
            for (int i in 1..10) {
                executor.submit( {
                    try {
                        cluster.resize(2)
                    } catch (Throwable e) {
                        throwables.add(e)
                    }
                })
            }
            
            executor.shutdown()
            assertTrue(executor.awaitTermination(10*STARTUP_TIME_MS+OVERHEAD_MS, TimeUnit.MILLISECONDS))
            if (throwables.size() > 0) throw throwables.get(0)
            assertEquals(cluster.currentSize, 2)
            assertEquals(cluster.getAttribute(Changeable.GROUP_SIZE), 2)
            assertEquals(numStarted.get(), 2)
        } finally {
            executor.shutdownNow();
        }
    }

    @Test(enabled = false)
    public void stoppingTheClusterStopsTheEntity() {
        TestEntity entity
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> entity = new TestEntityImpl(properties) })
                .configure("initialSize", 1));
        
        cluster.start([loc])
        assertEquals entity.counter.get(), 1
        cluster.stop()
        assertEquals entity.counter.get(), 0
    }
    
    /**
     * This tests the fix for ENGR-1826.
     */
    @Test
    public void failingEntitiesDontBreakClusterActions() {
        final int failNum = 2
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", { properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum)) 
                }));
        
        cluster.start([loc])
        cluster.resize(3)
        assertEquals(cluster.currentSize, 2)
        assertEquals(cluster.children.size(), 2)
        cluster.children.each {
            assertFalse(((FailingEntity)it).failOnStart)
        }
    }
    
    @Test
    public void testInitialQuorumSizeSufficientForStartup() {
        final int failNum = 1;
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 2)
                .configure(DynamicCluster.INITIAL_QUORUM_SIZE, 1)
                .configure("factory", { properties ->
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum))
                }));
        
        cluster.start([loc])
        assertEquals(cluster.getCurrentSize(), 1)
        assertEquals(cluster.getChildren().size(), 1)
        for (Entity child : cluster.getChildren()) {
            assertFalse(((FailingEntity)child).failOnStart)
        }
    }
    
    @Test
    public void testInitialQuorumSizeDeafultsToInitialSize() throws Exception {
        final int failNum = 1;
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 2)
                .configure("factory", { properties ->
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum))
                }));
        
        try {
            cluster.start([loc])
        } catch (Exception e) {
            IllegalStateException unwrapped = Exceptions.getFirstThrowableOfType(e, IllegalStateException.class);
            if (unwrapped != null && unwrapped.getMessage().contains("failed to get to initial size")) {
                // success
            } else {
                throw e; // fail
            }
        }
        assertEquals(cluster.getCurrentSize(), 1)
        assertEquals(cluster.getChildren().size(), 1)
        for (Entity child : cluster.getChildren()) {
            assertFalse(((FailingEntity)child).failOnStart)
        }
    }
    
    @Test
    public void testCanQuarantineFailedEntities() {
        final int failNum = 2
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("quarantineFailedEntities", true)
                .configure("initialSize", 0)
                .configure("factory", { properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum)) 
                }));
        
        cluster.start([loc])
        cluster.resize(3)
        assertEquals(cluster.currentSize, 2)
        assertEquals(cluster.getMembers().size(), 2)
        assertEquals(Iterables.size(Iterables.filter(cluster.children, Predicates.instanceOf(FailingEntity.class))), 3)
        cluster.members.each {
            assertFalse(((FailingEntity)it).failOnStart)
        }
        
        assertEquals(cluster.getAttribute(DynamicCluster.QUARANTINE_GROUP).members.size(), 1)
        cluster.getAttribute(DynamicCluster.QUARANTINE_GROUP).members.each {
            assertTrue(((FailingEntity)it).failOnStart)
        }
    }
    
    @Test
    public void defaultRemovalStrategyShutsDownNewestFirstWhenResizing() {
        TestEntity entity
        final int failNum = 2
        final List<Entity> creationOrder = []
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", { properties -> 
                    Entity result = new TestEntityImpl(properties)
                    creationOrder << result
                    return result
                }));
        
        cluster.start([loc])
        cluster.resize(1)
        cluster.resize(2)
        assertEquals(cluster.currentSize, 2)
        assertEquals(ImmutableSet.copyOf(cluster.getChildren()), ImmutableSet.copyOf(creationOrder), "actual="+cluster.getChildren())
        
        // Now stop one
        cluster.resize(1)
        assertEquals(cluster.currentSize, 1)
        assertEquals(ImmutableList.copyOf(cluster.getChildren()), creationOrder.subList(0, 1))
    }
        
    @Test
    public void resizeLoggedAsEffectorCall() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> return new TestEntityImpl(properties) }));
        
        app.start([loc])
        cluster.resize(1)
        
        Set<Task> tasks = app.getManagementContext().getExecutionManager().getTasksWithAllTags([cluster,"EFFECTOR"])
        assertEquals(tasks.size(), 2)
        assertTrue(Iterables.get(tasks, 0).getDescription().contains("start"))
        assertTrue(Iterables.get(tasks, 1).getDescription().contains("resize"))
    }
    
    @Test
    public void testStoppedChildIsRemoveFromGroup() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", { properties -> return new TestEntityImpl(properties) }));
        
        cluster.start([loc])
        
        TestEntity child = cluster.children.get(0)
        child.stop()
        Entities.unmanage(child)
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(cluster.children.size(), 0)
            assertEquals(cluster.currentSize, 0)
            assertEquals(cluster.members.size(), 0)
        }
    }
    
    @Test
    public void testPluggableRemovalStrategyIsUsed() {
        List<Entity> removedEntities = []
        
        Closure removalStrategy = { Collection<Entity> contenders ->
            Entity choice = Iterables.get(contenders, random.nextInt(contenders.size()))
            removedEntities.add(choice)
            return choice
        }
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> return new TestEntityImpl(properties) })
                .configure("initialSize", 10)
                .configure("removalStrategy", removalStrategy));
        
        cluster.start([loc])
        Set origMembers = cluster.members as Set
        
        for (int i = 10; i >= 0; i--) {
            cluster.resize(i)
            assertEquals(cluster.getAttribute(Changeable.GROUP_SIZE), i)
            assertEquals(removedEntities.size(), 10-i)
            assertEquals(ImmutableSet.copyOf(Iterables.concat(cluster.members, removedEntities)), origMembers)
        }
    }
    
    @Test
    public void testPluggableRemovalStrategyCanBeSetAfterConstruction() {
        List<Entity> removedEntities = []
        
        Closure removalStrategy = { Collection<Entity> contenders ->
            Entity choice = Iterables.get(contenders, random.nextInt(contenders.size()))
            removedEntities.add(choice)
            return choice
        }
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> return new TestEntityImpl(properties) })
                .configure("initialSize", 10));
        
        cluster.start([loc])
        Set origMembers = cluster.members as Set

        cluster.setRemovalStrategy(removalStrategy)
        
        for (int i = 10; i >= 0; i--) {
            cluster.resize(i)
            assertEquals(cluster.getAttribute(Changeable.GROUP_SIZE), i)
            assertEquals(removedEntities.size(), 10-i)
            assertEquals(ImmutableSet.copyOf(Iterables.concat(cluster.members, removedEntities)), origMembers)
        }
    }

    @Test
    public void testResizeDoesNotBlockCallsToQueryGroupMembership() {
        CountDownLatch executingLatch = new CountDownLatch(1)
        CountDownLatch continuationLatch = new CountDownLatch(1)
        
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 0)
                .configure("factory", { properties -> 
                        executingLatch.countDown()
                        continuationLatch.await()
                        return new TestEntityImpl(properties)
                    }));
        
        cluster.start([loc])

        Thread thread = new Thread( { cluster.resize(1) })
        try {
            // wait for resize to be executing
            thread.start()
            executingLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            
            // ensure can still call methods on group, to query/update membership
            assertEquals(cluster.getMembers(), [])
            assertEquals(cluster.getCurrentSize(), 0)
            assertFalse(cluster.hasMember(cluster))
            cluster.addMember(cluster)
            assertTrue(cluster.removeMember(cluster))
            
            // allow the resize to complete            
            continuationLatch.countDown()
            thread.join(TIMEOUT_MS)
            assertFalse(thread.isAlive())
        } finally {
            thread.interrupt()
        }
    }
    
    @Test
    public void testReplacesMember() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", { properties -> return new TestEntityImpl(properties) }));
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        String replacementId = cluster.replaceMember(member.getId());
        Entity replacement = cluster.getManagementContext().getEntityManager().getEntity(replacementId);
        
        assertEquals(cluster.members.size(), 1)
        assertFalse(cluster.members.contains(member))
        assertFalse(cluster.children.contains(member))
        assertNotNull(replacement, "replacementId="+replacementId);
        assertTrue(cluster.members.contains(replacement), "replacement="+replacement+"; members="+cluster.members);
        assertTrue(cluster.children.contains(replacement), "replacement="+replacement+"; children="+cluster.children);
    }
    
    @Test
    public void testReplaceMemberThrowsIfMemberIdDoesNotResolve() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", { properties -> return new TestEntityImpl(properties) }));
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        try {
            cluster.replaceMember("wrong.id");
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class) == null) throw e;
            if (!Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class).getMessage().contains("entity wrong.id cannot be resolved")) throw e;
        }
        
        assertEquals(cluster.members as Set, ImmutableSet.of(member));
    }
    
    @Test
    public void testReplaceMemberThrowsIfNotMember() {
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", { properties -> return new TestEntityImpl(properties) }));
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        try {
            cluster.replaceMember(app.getId());
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class) == null) throw e;
            if (!Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class).getMessage().contains("is not a member")) throw e;
        }
        
        assertEquals(cluster.members as Set, ImmutableSet.of(member));
    }
    
    @Test
    public void testReplaceMemberFailsIfCantProvisionReplacement() {
        final int failNum = 2
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("factory", { properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum)) 
                }));
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        try {
            cluster.replaceMember(member.getId());
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("failed to grow")) throw e;
            if (Exceptions.getFirstThrowableOfType(e, NoSuchElementException.class) != null) throw e;
        }
        assertEquals(cluster.members as Set, ImmutableSet.of(member));
    }
    
    @Test
    public void testReplaceMemberRemovesAndThowsIfFailToStopOld() {
        final int failNum = 1
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 1)
                .configure("factory", { properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, false, (num==failNum), IllegalStateException.class) 
                }));
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        try {
            cluster.replaceMember(member.getId());
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("Simulating entity stop failure")) throw e;
            if (Exceptions.getFirstThrowableOfType(e, IllegalStateException.class) == null) throw e;
        }
        assertFalse(Entities.isManaged(member));
        assertEquals(cluster.members.size(), 1);
    }
    
    private Throwable unwrapException(Throwable e) {
        if (e instanceof ExecutionException) {
            return unwrapException(e.cause)
        } else if (e instanceof org.codehaus.groovy.runtime.InvokerInvocationException) {
            return unwrapException(e.cause)
        } else {
            return e
        }
    }
}

class FailingEntity extends TestEntityImpl {
    final boolean failOnStart;
    final boolean failOnStop;
    final Class<? extends Exception> exceptionClazz;

    public FailingEntity(Map flags, boolean failOnStart) {
        this(flags, failOnStart, false);
    }
    
    public FailingEntity(Map flags, boolean failOnStart, boolean failOnStop) {
        this(flags, failOnStart, failOnStop, IllegalStateException.class);
    }
    
    public FailingEntity(Map flags, boolean failOnStart, boolean failOnStop, Class<? extends Exception> exceptionClazz) {
        super(flags)
        this.failOnStart = failOnStart;
        this.failOnStop = failOnStop;
        this.exceptionClazz = exceptionClazz;
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        if (failOnStart) {
            Exception e = exceptionClazz.getConstructor(String.class).newInstance("Simulating entity start failure for test");
            throw e;
        }
    }
    
    @Override
    public void stop() {
        if (failOnStop) {
            Exception e = exceptionClazz.getConstructor(String.class).newInstance("Simulating entity stop failure for test");
            throw e;
        }
    }
}
