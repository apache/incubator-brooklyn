package brooklyn.entity.basic

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity
import brooklyn.util.internal.LanguageUtils

import com.google.common.base.Predicate


public class DynamicGroupTest {
    
    private TestApplication app
    private DynamicGroup group
    private TestEntity e1
    private TestEntity e2
    
    @BeforeMethod
    public void setUp() {
        app = new TestApplication()
        group = new DynamicGroup(owner:app)
        e1 = new TestEntity(owner:app)
        e2 = new TestEntity(owner:app)
        app.getManagementContext().manage(app)
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
    public void testGroupWithMatchingFilterReturnsEverythingThatMatches() {
        group.setEntityFilter( { true } )
        assertEquals(group.getMembers().size(), 4)
        assertTrue(group.getMembers().containsAll([e1, e2, app, group]))
    }
    
    @Test
    public void testGroupDetectsNewlyManagedMatchingMember() {
        Entity e3 = new AbstractEntity() {}
        group.setEntityFilter( { it.getId().equals(e3.getId()) } )
        
        e3.setOwner(app)
        e3.getManagementContext().manage(e3)
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
        
        executeUntilSucceeds(timeout:5000) {
            assertEquals(group.getMembers(), [e1])
        }

        // When it stops matching, entity is removed        
        e1.setAttribute(MY_ATTRIBUTE, "no")
        
        executeUntilSucceeds(timeout:5000) {
            assertEquals(group.getMembers(), [])
        }
    }
    
    @Test
    public void testGroupDetectsChangedEntitiesMatchingFilter() {
        final BasicAttributeSensor<String> MY_ATTRIBUTE = [ String, "test.myAttribute", "My test attribute" ]
        
        group.setEntityFilter( { it.getAttribute(MY_ATTRIBUTE) == "yes" } )
        group.addSubscription(null, MY_ATTRIBUTE, { SensorEvent event -> e1 != event.source } as Predicate)
        
        assertEquals(group.getMembers(), [])
        
        // Ignores anything that does not match predicate filter; so event from e1 will be ignored
        e1.setAttribute(MY_ATTRIBUTE, "yes")
        e2.setAttribute(MY_ATTRIBUTE, "yes")
        
        executeUntilSucceeds(timeout:5000) {
            assertEquals(group.getMembers(), [e2])
        }
    }
    
    @Test
    public void testGroupRemovesUnmanagedEntity() {
        group.setEntityFilter( { it.getId().equals(e1.getId()) } )
        assertEquals(group.getMembers(), [e1])
        
        app.getManagementContext().unmanage(e1)
        
        executeUntilSucceeds(timeout:5000) {
            assertEquals(group.getMembers(), [])
        }
    }
    
    @Test
    public void testStoppedGroupIgnoresComingAndGoingsOfEntities() {
        Entity e3 = new AbstractEntity() {}
        group.setEntityFilter( { it instanceof TestEntity } )
        assertEquals(group.getMembers(), [e1, e2])
        group.stop()
        
        e3.setOwner(app)
        app.getManagementContext().manage(e3)
        assertEquals(group.getMembers(), [e1, e2])
        
        app.getManagementContext().unmanage(e1)
        assertEquals(group.getMembers(), [e1, e2])
    }
    

    // Motivated by strange behavior observed testing load-balancing policy, but this passed...
    @Test
    public void testGroupAddsAndRemovesManagedAndUnmanagedEntitiesExactlyOnce() {
        int NUM_CYCLES = 100
        group.setEntityFilter( { it instanceof TestEntity } )
        Set<TestEntity> entitiesNotified = [] as Set
        AtomicInteger notificationCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<Exception>()
        
        app.subscribe(group, DynamicGroup.MEMBER_ADDED, { SensorEvent<Entity> event ->
                try {
                    Entity source = event.getSource()
                    Object val = event.getValue()
                    assertEquals(group, event.getSource())
                    assertTrue(entitiesNotified.add(val))
                    notificationCount.incrementAndGet()
                } catch (Throwable t) {
                    exceptions.add(new Exception("Error on event $event", t))
                }
            } as SensorEventListener);

        app.subscribe(group, DynamicGroup.MEMBER_REMOVED, { SensorEvent<Entity> event ->
                try {
                    Entity source = event.getSource()
                    Object val = event.getValue()
                    assertEquals(group, event.getSource())
                    assertTrue(entitiesNotified.remove(val))
                    notificationCount.incrementAndGet()
                } catch (Throwable t) {
                    exceptions.add(new Exception("Error on event $event", t))
                }
            } as SensorEventListener);

        for (i in 1..NUM_CYCLES) {
            TestEntity entity = new TestEntity(owner:app)
            app.getManagementContext().manage(entity);
            app.getManagementContext().unmanage(entity);
        }

        LanguageUtils.repeatUntilSuccess(timeout:new groovy.time.TimeDuration(0, 0, 10, 0)) {
            return notificationCount.get() == (NUM_CYCLES*2) || exceptions.size() > 0
        }

        if (exceptions.size() > 0) {
            throw exceptions.get(0)
        }
        
        assertEquals(notificationCount.get(), NUM_CYCLES*2)
    }
}
