package brooklyn.entity.basic

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicAttributeSensor

import com.google.common.base.Predicate


public class DynamicGroupTest {
    private AbstractApplication app
    private DynamicGroup group
    private AbstractEntity e1
    private AbstractEntity e2
    
    @BeforeMethod
    public void setUp() {
        app = new AbstractApplication() {}
        group = new DynamicGroup(owner:app) {}
        e1 = new AbstractEntity(owner:app) {}
        e2 = new AbstractEntity(owner:app) {}
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
}
