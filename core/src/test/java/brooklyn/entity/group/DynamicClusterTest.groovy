package brooklyn.entity.group

import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.jclouds.util.Throwables2;
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.trait.Changeable
import brooklyn.entity.trait.Resizable
import brooklyn.event.EntityStartException
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Predicates
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
        app = new TestApplication()
        loc = new SimulatedLocation()
        loc2 = new SimulatedLocation()
    }
    
    public void constructorOkayWithoutNewEntityFactoryArgument() {
        new DynamicCluster(app)
    }

    @Test(expectedExceptions = [ExecutionException, IllegalStateException, IllegalArgumentException, NullPointerException, ClassCastException.class])
    public void startRequiresThatNewEntityArgumentIsGiven() {
        new DynamicCluster(app).start([loc])
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = [IllegalArgumentException.class, ClassCastException.class])
    public void constructorRequiresThatNewEntityArgumentIsAnEntityFactory() {
        new DynamicCluster(factory:"error", app)
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void startMethodFailsIfLocationsParameterIsMissing() {
        DynamicCluster cluster = new DynamicCluster(factory:{ new TestEntity() }, app)
        try {
            cluster.start(null)
        } catch (Exception e) {
            throw unwrapException(e)
        }
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void startMethodFailsIfLocationsParameterIsEmpty() {
        DynamicCluster cluster = new DynamicCluster(factory:{ new TestEntity() }, app)
        try {
            cluster.start([])
        } catch (Exception e) {
            throw unwrapException(e)
        }
        fail "Did not throw expected exception"
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void startMethodFailsIfLocationsParameterHasMoreThanOneElement() {
        DynamicCluster cluster = new DynamicCluster(factory:{ new TestEntity() }, app)
        try {
            cluster.start([ loc, loc2 ])
        } catch (Exception e) {
            throw unwrapException(e)
        }
        fail "Did not throw expected exception"
    }

    @Test
    public void testClusterHasOneLocationAfterStarting() {
        DynamicCluster cluster = new DynamicCluster(factory:{ new TestEntity() }, app)
        cluster.start([loc])
        assertEquals(cluster.getLocations().size(), 1)
        assertEquals(cluster.getLocations() as List, [loc])
    }

    @Test
    public void resizeFromZeroToOneStartsANewEntityAndSetsItsOwner() {
        TestEntity entity
        DynamicCluster cluster = new DynamicCluster(factory:{ properties -> entity = new TestEntity(properties) }, app)
        cluster.start([loc])

        cluster.resize(1)
        assertEquals entity.counter.get(), 1
        assertEquals entity.owner, cluster
        assertEquals entity.application, app
    }

    @Test
    public void currentSizePropertyReflectsActualClusterSize() {
        Application app = new AbstractApplication() { }
        DynamicCluster cluster = new DynamicCluster(factory:{ properties -> new TestEntity(properties) }, app)
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
    }

    @Test
    public void clusterSizeAfterStartIsInitialSize() {
        DynamicCluster cluster = new DynamicCluster([ factory:{ properties -> new TestEntity(properties) }, initialSize:2 ], app)
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
            entity = new TestEntity(owner:app) {
	            List<Location> stashedLocations = null
	            @Override
	            void start(Collection<? extends Location> loc) {
	                super.start(loc)
	                stashedLocations = loc
	            }
	        }
        }
        DynamicCluster cluster = new DynamicCluster([ factory:newEntity, initialSize:1 ], app)
        cluster.start(locations)

        assertNotNull entity.stashedLocations
        assertEquals entity.stashedLocations.size(), 1
        assertEquals entity.stashedLocations[0], locations[0]
    }

    @Test
    public void resizeFromOneToZeroChangesClusterSize() {
        TestEntity entity
        DynamicCluster cluster = new DynamicCluster([ factory:{ properties -> entity = new TestEntity(properties) }, initialSize:1 ], app)
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
        Application app = new AbstractApplication() { }
        DynamicCluster cluster = new DynamicCluster(factory:
                { Map flags, Entity cluster -> 
                    Thread.sleep(STARTUP_TIME_MS); numStarted.incrementAndGet(); new TestEntity(flags, cluster)
                }, 
                app)
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
        DynamicCluster cluster = new DynamicCluster([ factory:{ properties -> entity = new TestEntity(properties) }, initialSize:1 ], app)
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
        DynamicCluster cluster = new DynamicCluster(app,  
                initialSize:0,
                factory:{ properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum)) 
                })
        
        cluster.start([loc])
        cluster.resize(3)
        assertEquals(cluster.currentSize, 2)
        assertEquals(cluster.ownedChildren.size(), 2)
        cluster.ownedChildren.each {
            assertFalse(((FailingEntity)it).fail)
        }
    }
    
    @Test
    public void testCanQuarantineFailedEntities() {
        final int failNum = 2
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = new DynamicCluster(app,  
                quarantineFailedEntities:true,
                initialSize:0,
                factory:{ properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum)) 
                })
        
        cluster.start([loc])
        cluster.resize(3)
        assertEquals(cluster.currentSize, 2)
        assertEquals(cluster.members.size, 2)
        assertEquals(Iterables.size(Iterables.filter(cluster.ownedChildren, Predicates.instanceOf(FailingEntity.class))), 3)
        cluster.members.each {
            assertFalse(((FailingEntity)it).fail)
        }
        
        assertEquals(cluster.getAttribute(DynamicCluster.QUARANTINE_GROUP).members.size(), 1)
        cluster.getAttribute(DynamicCluster.QUARANTINE_GROUP).members.each {
            assertTrue(((FailingEntity)it).fail)
        }
    }
    
    @Test
    public void defaultRemovalStrategyShutsDownNewestFirstWhenResizing() {
        TestEntity entity
        final int failNum = 2
        final List<Entity> creationOrder = []
        DynamicCluster cluster = new DynamicCluster([ factory:{ properties -> 
                    Entity result = new TestEntity(properties)
                    creationOrder << result
                    return result
                }, initialSize:0 ], app)
        
        cluster.start([loc])
        cluster.resize(1)
        cluster.resize(2)
        assertEquals(cluster.currentSize, 2)
        assertEquals(cluster.ownedChildren as List, creationOrder)
        
        // Now stop one
        cluster.resize(1)
        assertEquals(cluster.currentSize, 1)
        assertEquals(cluster.ownedChildren, creationOrder.subList(0, 1))
    }
        
    @Test
    public void resizeLoggedAsEffectorCall() {
        Resizable cluster = new DynamicCluster(factory:{ properties -> return new TestEntity(properties) }, app)
        app.start([loc])
        cluster.resize(1)
        
        Set<Task> tasks = app.getManagementContext().getExecutionManager().getTasksWithAllTags([cluster,"EFFECTOR"])
        assertEquals(tasks.size(), 2)
        assertTrue(Iterables.get(tasks, 0).getDescription().contains("start"))
        assertTrue(Iterables.get(tasks, 1).getDescription().contains("resize"))
    }
    
    @Test
    public void testStoppedChildIsRemoveFromGroup() {
        DynamicCluster cluster = new DynamicCluster([
                factory:{ properties -> return new TestEntity(properties) },
                initialSize:1 
            ], app)
        
        cluster.start([loc])
        
        TestEntity child = cluster.ownedChildren.get(0)
        child.stop()
        app.managementContext.unmanage(child)
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(cluster.ownedChildren.size(), 0)
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
        DynamicCluster cluster = new DynamicCluster([
                factory:{ properties -> return new TestEntity(properties) },
                initialSize:10,
                removalStrategy:removalStrategy
            ], app)
        
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
        DynamicCluster cluster = new DynamicCluster([
                factory:{ properties -> return new TestEntity(properties) },
                initialSize:10,
            ], app)
        
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
        
        DynamicCluster cluster = new DynamicCluster(
            [
                factory: { properties -> 
                        executingLatch.countDown()
                        continuationLatch.await()
                        return new TestEntity(properties)
                    },
                initialSize:0 
            ], app)
        
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
        DynamicCluster cluster = new DynamicCluster([
                factory:{ properties -> return new TestEntity(properties) },
                initialSize:1,
            ], app)
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        String replacementId = cluster.replaceMember(member.getId());
        Entity replacement = cluster.getManagementContext().getEntity(replacementId);
        
        assertEquals(cluster.members.size(), 1)
        assertFalse(cluster.members.contains(member))
        assertFalse(cluster.ownedChildren.contains(member))
        assertNotNull(replacement, "replacementId="+replacementId);
        assertTrue(cluster.members.contains(replacement), "replacement="+replacement+"; members="+cluster.members);
        assertTrue(cluster.ownedChildren.contains(replacement), "replacement="+replacement+"; children="+cluster.ownedChildren);
    }
    
    @Test
    public void testReplaceMemberThrowsIfMemberIdDoesNotResolve() {
        DynamicCluster cluster = new DynamicCluster([
                factory:{ properties -> return new TestEntity(properties) },
                initialSize:1,
            ], app)
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        try {
            cluster.replaceMember("wrong.id");
            fail();
        } catch (Exception e) {
            if (Throwables2.getFirstThrowableOfType(e, NoSuchElementException.class) == null) throw e;
            if (!Throwables2.getFirstThrowableOfType(e, NoSuchElementException.class).getMessage().contains("entity wrong.id cannot be resolved")) throw e;
        }
        
        assertEquals(cluster.members as Set, ImmutableSet.of(member));
    }
    
    @Test
    public void testReplaceMemberThrowsIfNotMember() {
        DynamicCluster cluster = new DynamicCluster([
                factory:{ properties -> return new TestEntity(properties) },
                initialSize:1,
            ], app)
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        try {
            cluster.replaceMember(app.getId());
            fail();
        } catch (Exception e) {
            if (Throwables2.getFirstThrowableOfType(e, NoSuchElementException.class) == null) throw e;
            if (!Throwables2.getFirstThrowableOfType(e, NoSuchElementException.class).getMessage().contains("is not a member")) throw e;
        }
        
        assertEquals(cluster.members as Set, ImmutableSet.of(member));
    }
    
    @Test
    public void testReplaceMemberFailsIfCantProvisionReplacement() {
        final int failNum = 2
        final AtomicInteger counter = new AtomicInteger(0)
        DynamicCluster cluster = new DynamicCluster(app,  
                initialSize:1,
                factory:{ properties -> 
                    int num = counter.incrementAndGet();
                    return new FailingEntity(properties, (num==failNum)) 
                })
        
        cluster.start([loc])
        Entity member = Iterables.get(cluster.members, 0);
        
        try {
            cluster.replaceMember(member.getId());
            fail();
        } catch (Exception e) {
            if (!e.toString().contains("failed to grow")) throw e;
            if (Throwables2.getFirstThrowableOfType(e, NoSuchElementException.class) != null) throw e;
        }
        assertEquals(cluster.members as Set, ImmutableSet.of(member));
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

class FailingEntity extends TestEntity {
    boolean fail;
    Class<? extends Exception> exceptionClazz;
    
    FailingEntity(Map flags, boolean fail) {
        this(flags, fail, EntityStartException.class);
    }
    FailingEntity(Map flags, boolean fail, Class<? extends Exception> exceptionClazz) {
        super(flags)
        this.fail = fail
        this.exceptionClazz = exceptionClazz;
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        if (fail) {
            Exception e = exceptionClazz.getConstructor(String.class).newInstance("Simulating entity start failure for test");
            throw e;
            throw new EntityStartException("Simulating entity start failure for test")
        }
    }
}
