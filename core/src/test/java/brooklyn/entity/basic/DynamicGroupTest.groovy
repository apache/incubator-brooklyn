package brooklyn.entity.basic;

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.proxying.EntitySpecs
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.time.Duration

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets

public class DynamicGroupTest {

    private static final Logger LOG = LoggerFactory.getLogger(DynamicGroupTest.class);
    
    private static final int TIMEOUT_MS = 50*1000;
    private static final int VERY_SHORT_WAIT_MS = 100;
    
    private TestApplication app
    private DynamicGroup group
    private TestEntity e1
    private TestEntity e2
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        group = app.createAndManageChild(EntitySpecs.spec(DynamicGroup.class));
        e1 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
        e2 = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testGroupWithNoFilterReturnsNoMembers() {
        assertTrue(group.getMembers().isEmpty())
    }
    
    @Test
    public void testGroupWithNonMatchingFilterReturnsNoMembers() {
        group.setEntityFilter( { false } )
        assertTrue(group.getMembers().isEmpty())
    }
    
    @Test
    public void testGroupWithMatchingFilterReturnsOnlyMatchingMembers() {
        group.setEntityFilter( { it.getId().equals(e1.getId()) } )
        assertEquals(group.getMembers(), [e1])
    }
    
    @Test
    public void testCanUsePredicateAsFilter() {
        Predicate predicate = Predicates.equalTo(e1)
        group.setEntityFilter(predicate)
        assertEquals(group.getMembers(), [e1])
    }
    
    @Test
    public void testGroupWithMatchingFilterReturnsEverythingThatMatches() {
        group.setEntityFilter( { true } )
        assertEquals(ImmutableSet.copyOf(group.getMembers()), [e1, e2, app, group] as Set)
        assertEquals(group.getMembers().size(), 4)
    }
    
    @Test
    public void testGroupDetectsNewlyManagedMatchingMember() {
        Entity e3 = new AbstractEntity() {}
        group.setEntityFilter( { it.getId().equals(e3.getId()) } )
        e3.setParent(app);
        
        assertEquals(group.getMembers(), [])
        
        Entities.manage(e3);
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(group.getMembers(), [e3])
        }
    }

    @Test
    public void testGroupUsesNewFilter() {
        Entity e3 = new AbstractEntity(app) {}
        Entities.manage(e3);
        group.setEntityFilter( { it.getId().equals(e3.getId()) } )
        
        assertEquals(group.getMembers(), [e3])
    }

    @Test
    public void testGroupDetectsChangedEntities() {
        final BasicAttributeSensor<String> MY_ATTRIBUTE = [ String, "test.myAttribute", "My test attribute" ]
    
        group.setEntityFilter( { it.getAttribute(MY_ATTRIBUTE) == "yes" } )
        group.addSubscription(null, MY_ATTRIBUTE)
        
        assertEquals(group.getMembers(), [])
        
        // When changed (such that subscription spots it), then entity added
        e1.setAttribute(MY_ATTRIBUTE, "yes")
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(group.getMembers(), [e1])
        }

        // When it stops matching, entity is removed        
        e1.setAttribute(MY_ATTRIBUTE, "no")
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(group.getMembers(), [])
        }
    }
    
    @Test
    public void testGroupDetectsChangedEntitiesMatchingFilter() {
        final BasicAttributeSensor<String> MY_ATTRIBUTE = [ String, "test.myAttribute", "My test attribute" ]
        group.setEntityFilter( { 
            if (!(it.getAttribute(MY_ATTRIBUTE) == "yes")) 
                return false
            if (it == e1) {
                LOG.info("testGroupDetectsChangedEntitiesMatchingFilter scanned e1 when MY_ATTRIBUTE is yes; not a bug, but indicates things may be running slowly")
                return false
            }
            return true
        } )
        group.addSubscription(null, MY_ATTRIBUTE, { SensorEvent event -> e1 != event.source } as Predicate)
        
        assertEquals(group.getMembers(), [])
        
        // Does not subscribe to things which do not match predicate filter, 
        // so event from e1 should normally be ignored 
        // but pending rescans may cause it to pick up e1, so we ignore e1 in the entity filter also
        e1.setAttribute(MY_ATTRIBUTE, "yes")
        e2.setAttribute(MY_ATTRIBUTE, "yes")
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(group.getMembers(), [e2])
        }
    }
    
    @Test
    public void testGroupRemovesUnmanagedEntity() {
        group.setEntityFilter( { it.getId().equals(e1.getId()) } )
        assertEquals(group.getMembers(), [e1])
        
        Entities.unmanage(e1)
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(group.getMembers(), [])
        }
    }
    
    @Test
    public void testStoppedGroupIgnoresComingAndGoingsOfEntities() {
        Entity e3 = new AbstractEntity() {}
        group.setEntityFilter( { it instanceof TestEntity } )
        assertEquals(ImmutableSet.copyOf(group.getMembers()), [e1, e2] as Set)
        group.stop()
        
        e3.setParent(app)
        Entities.manage(e3)
        assertSucceedsContinually(timeout:VERY_SHORT_WAIT_MS) {
            assertEquals(ImmutableSet.copyOf(group.getMembers()), [e1, e2] as Set)
        }
                
        Entities.unmanage(e1)
        assertSucceedsContinually(timeout:VERY_SHORT_WAIT_MS) {
            assertEquals(ImmutableSet.copyOf(group.getMembers()), [e1, e2] as Set)
        }
    }
    

    // Motivated by strange behavior observed testing load-balancing policy, but this passed...
    //
    // Note that addMember/removeMember is now async for when member-entity is managed/unmanaged,
    // so to avoid race where entity is already unmanaged by the time addMember does its stuff,
    // we wait for it to really be added.
    @Test
    public void testGroupAddsAndRemovesManagedAndUnmanagedEntitiesExactlyOnce() {
        int NUM_CYCLES = 100
        group.setEntityFilter( { it instanceof TestEntity } )
        Set<TestEntity> entitiesNotified = [] as Set
        AtomicInteger notificationCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<Exception>()
        
        app.subscribe(group, DynamicGroup.MEMBER_ADDED, { SensorEvent<Entity> event ->
                try {
                    LOG.debug("Notified of member added: member={}, thread={}", event.getValue(), Thread.currentThread().getName());
                    Entity source = event.getSource()
                    Object val = event.getValue()
                    assertEquals(group, event.getSource())
                    assertTrue(entitiesNotified.add(val))
                    notificationCount.incrementAndGet()
                } catch (Throwable t) {
                    LOG.error("Error on event $event", t);
                    exceptions.add(new Exception("Error on event $event", t))
                }
            } as SensorEventListener);

        app.subscribe(group, DynamicGroup.MEMBER_REMOVED, { SensorEvent<Entity> event ->
                try {
                    LOG.debug("Notified of member removed: member={}, thread={}", event.getValue(), Thread.currentThread().getName());
                    Entity source = event.getSource()
                    Object val = event.getValue()
                    assertEquals(group, event.getSource())
                    assertTrue(entitiesNotified.remove(val))
                    notificationCount.incrementAndGet()
                } catch (Throwable t) {
                    LOG.error("Error on event $event", t);
                    exceptions.add(new Exception("Error on event $event", t))
                }
            } as SensorEventListener);

        for (i in 1..NUM_CYCLES) {
            TestEntity entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
            executeUntilSucceeds {
                entitiesNotified.contains(entity);
            }
            Entities.unmanage(entity);
        }

        TestUtils.executeUntilSucceeds(timeout:Duration.TEN_SECONDS) {
            return notificationCount.get() == (NUM_CYCLES*2) || exceptions.size() > 0
        }

        if (exceptions.size() > 0) {
            throw exceptions.get(0)
        }
        
        assertEquals(notificationCount.get(), NUM_CYCLES*2)
    }
    
    // The entityAdded/entityRemoved is now async for when member-entity is managed/unmanaged,
    // but it should always be called sequentially (i.e. semantics of a single-threaded executor).
    // Test is deliberately slow in processing entityAdded/removed calls, to try to cause
    // concurrent calls if they are going to happen at all.
    @Test(groups="Integration")
    public void testEntityAddedAndRemovedCalledSequentially() {
        int NUM_CYCLES = 10;
        final Set<Entity> knownMembers = Sets.newLinkedHashSet();
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger concurrentCallsCount = new AtomicInteger(0);
        final List<Exception> exceptions = new CopyOnWriteArrayList<Exception>();
        
        DynamicGroup group2 = new DynamicGroupImpl() {
            @Override protected void onEntityAdded(Entity item) {
                try {
                    onCall("Member added: member="+item);
                    assertTrue(knownMembers.add(item));
                } catch (Throwable t) {
                    exceptions.add(new Exception("Error detected adding "+item, t));
                    throw t;
                }
            }
            @Override protected void onEntityRemoved(Entity item) {
                try {
                    onCall("Member removed: member="+item);
                    assertTrue(knownMembers.remove(item));
                } catch (Throwable t) {
                    exceptions.add(new Exception("Error detected adding "+item, t));
                    throw t;
                }
            }
            private void onCall(String msg) {
                LOG.debug(msg+", thread="+Thread.currentThread().getName());
                try {
                    assertEquals(concurrentCallsCount.incrementAndGet(), 1);
                    Thread.sleep(100);
                } finally {
                    concurrentCallsCount.decrementAndGet();
                }
                notificationCount.incrementAndGet();
            }
        };
        ((EntityLocal)group2).setConfig(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(TestEntity.class));
        app.addChild(group2);
        group2.init();
        Entities.manage(group2);
        
        for (int i = 0; i < NUM_CYCLES; i++) {
            TestEntity entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
            Entities.unmanage(entity);
        }

        TestUtils.executeUntilSucceeds(timeout:new groovy.time.TimeDuration(0, 0, 10, 0)) {
            return notificationCount.get() == (NUM_CYCLES*2) || exceptions.size() > 0;
        }

        if (exceptions.size() > 0) {
            throw exceptions.get(0);
        }
        
        assertEquals(notificationCount.get(), NUM_CYCLES*2);
    }
    
    // See Deadlock in https://github.com/brooklyncentral/brooklyn/issues/378
    @Test
    public void testDoesNotDeadlockOnManagedAndMemberAddedConcurrently() throws Exception {
        final CountDownLatch rescanReachedLatch = new CountDownLatch(1);
        final CountDownLatch entityAddedReachedLatch = new CountDownLatch(1);
        final CountDownLatch rescanLatch = new CountDownLatch(1);
        final CountDownLatch entityAddedLatch = new CountDownLatch(1);
        
        final TestEntity e3 = app.createChild(EntitySpecs.spec(TestEntity.class));
        Predicate filter = Predicates.equalTo(e3);
        
        DynamicGroup group2 = new DynamicGroupImpl() {
            @Override public void rescanEntities() {
                rescanReachedLatch.countDown();
                rescanLatch.await();
                super.rescanEntities();
            }
            @Override protected void onEntityAdded(Entity item) {
                entityAddedReachedLatch.countDown();
                entityAddedLatch.await();
                super.onEntityAdded(item);
            }
        };
        ((EntityLocal)group2).setConfig(DynamicGroup.ENTITY_FILTER, filter);
        app.addChild(group2);
        group2.init();
        
        Thread t1 = new Thread(new Runnable() {
            @Override public void run() {
                Entities.manage(group2);
            }});
        
        Thread t2 = new Thread(new Runnable() {
            @Override public void run() {
                Entities.manage(e3);
            }});

        t1.start();
        try {
            assertTrue(rescanReachedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            
            t2.start();
            assertTrue(entityAddedReachedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            
            entityAddedLatch.countDown();
            rescanLatch.countDown();
            
            t2.join(TIMEOUT_MS);
            t1.join(TIMEOUT_MS);
            assertFalse(t1.isAlive());
            assertFalse(t2.isAlive());
            
        } finally {
            t1.interrupt();
            t2.interrupt();
        }
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(group2.getMembers(), [e3]);
        }
    }
}
