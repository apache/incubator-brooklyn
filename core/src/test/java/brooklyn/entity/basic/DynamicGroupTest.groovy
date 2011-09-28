package brooklyn.entity.basic

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity

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
    
}
