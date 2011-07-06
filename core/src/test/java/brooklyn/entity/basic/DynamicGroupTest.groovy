package brooklyn.entity.basic

import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

class DynamicGroupTest {

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
    }
    
    @Test
    public void testGroupWithNoFilterReturnsNoMembers() {
        Assert.assertEquals(group.getMembers(), [])
    }
    
    @Test
    public void testGroupWithNonMatchingFilterReturnsNoMembers() {
        group.setEntityFilter( { false } )
        Assert.assertEquals(group.getMembers(), [])
    }
    
    @Test
    public void testGroupWithMatchingFilterReturnsOnlyMatchingMembers() {
        group.setEntityFilter( { it.getId().equals(e1.getId()) } )
        Assert.assertEquals(group.getMembers(), [e1])
    }
    
    @Test
    public void testGroupWithMatchingFilterReturnsEverythingThatMatches() {
        group.setEntityFilter( { true } )
        def a = group.getMembers() as HashSet
        Assert.assertEquals(group.getMembers() as HashSet, [e1, e2, app, group] as HashSet)
    }
}
