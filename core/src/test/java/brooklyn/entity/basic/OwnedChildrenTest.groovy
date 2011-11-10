package brooklyn.entity.basic

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity

public class OwnedChildrenTest {

    private Application app

    @BeforeMethod
    public void setUp() {
        app = new AbstractApplication() {}
    }
    
    @Test
    public void testSetOwnerInConstructorMap() {
        Entity e = new AbstractEntity(owner:app) {}
        
        assertEquals(e.getOwner(), app)
        assertEquals(app.getOwnedChildren(), [e])
        assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetOwnerInConstructorArgument() {
        Entity e = new AbstractEntity(app) {}
        
        assertEquals(e.getOwner(), app)
        assertEquals(app.getOwnedChildren(), [e])
        assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetOwnerInSetterMethod() {
        Entity e = new AbstractEntity() {}
        e.setOwner(app)
        
        assertEquals(e.getOwner(), app)
        assertEquals(app.getOwnedChildren(), [e])
        assertEquals(e.getApplication(), app)
    }

    @Test
    public void testAddOwnedChild() {
        Entity e = new AbstractEntity() {}
        app.addOwnedChild(e)
        
        assertEquals(e.getOwner(), app)
        assertEquals(app.getOwnedChildren(), [e])
        assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetOwnerWhenMatchesOwnerSetInConstructor() {
        Entity e = new AbstractEntity(owner:app) {}
        e.setOwner(app)
        
        assertEquals(e.getOwner(), app)
        assertEquals(app.getOwnedChildren(), [e])
    }
    
    @Test(expectedExceptions = [ UnsupportedOperationException.class ])
    public void testSetOwnerWhenDiffersFromOwnerSetInConstructor() {
        Entity e = new AbstractEntity(owner:app) {}
        Entity e2 = new AbstractEntity() {}
        e.setOwner(e2)
        fail();
    }
    
    @Test
    public void testOwnerCanHaveMultipleChildren() {
        Entity e = new AbstractEntity(owner:app) {}
        Entity e2 = new AbstractEntity(owner:app) {}
        
        assertEquals(e.getOwner(), app)
        assertEquals(e2.getOwner(), app)
        assertEquals(app.getOwnedChildren(), [e,e2])
    }
    
    @Test
    public void testHierarchyOfOwners() {
        Entity e = new AbstractEntity(owner:app) {}
        Entity e2 = new AbstractEntity(owner:e) {}
        Entity e3 = new AbstractEntity(owner:e2) {}
        
        assertEquals(app.getOwner(), null)
        assertEquals(e.getOwner(), app)
        assertEquals(e2.getOwner(), e)
        assertEquals(e3.getOwner(), e2)
        
        assertEquals(app.getOwnedChildren(), [e])
        assertEquals(e.getOwnedChildren(), [e2])
        assertEquals(e2.getOwnedChildren(), [e3])
        assertEquals(e3.getOwnedChildren(), [])
    }
    
    @Test(enabled = false) // FIXME fails currently
    public void testRemoveOwnedChild() {
        Entity e = new AbstractEntity(owner:app) {}
        app.removeOwnedChild(e)
        
        assertEquals(app.getOwnedChildren(), [])
        assertEquals(e.getOwner(), null)
    }
    
    @Test
    public void testOwnershipLoopForbiddenViaAddOwnedChild() {
        Entity e = new AbstractEntity() {}
        Entity e2 = new AbstractEntity(owner:e) {}
        try {
            e2.addOwnedChild(e)
            fail()
        } catch (IllegalStateException ex) {
            // success
        }
        
        assertEquals(e.getOwnedChildren(), [e2])
        assertEquals(e2.getOwnedChildren(), [])
        assertEquals(e.getOwner(), null)
        assertEquals(e2.getOwner(), e)
    }
    
    @Test
    public void testOwnershipLoopForbiddenViaSetOwner() {
        Entity e = new AbstractEntity() {}
        Entity e2 = new AbstractEntity(owner:e) {}
        try {
            e.setOwner(e2)
            fail()
        } catch (IllegalStateException ex) {
			ex.printStackTrace();
            // success
        }
        assertEquals(e.getOwnedChildren(), [e2])
        assertEquals(e2.getOwnedChildren(), [])
        assertEquals(e.getOwner(), null)
        assertEquals(e2.getOwner(), e)
    }
    
    @Test(expectedExceptions = [ IllegalStateException.class ])
    public void testOwningOneselfForbidden() {
        AbstractEntity e = new AbstractEntity() {}
        e.addOwnedChild(e)
        fail()
    }
    
    @Test
    public void testIsAncestor() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        AbstractEntity e2 = new AbstractEntity(owner:e) {}
        
		use (Entities) {
			assertTrue(e2.isAncestor(app))
			assertTrue(e2.isAncestor(e))
			assertFalse(e.isAncestor(e2))
		}
    }
    
    @Test
    public void testIsDescendant() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        AbstractEntity e2 = new AbstractEntity(owner:e) {}

		use (Entities) {
			assertTrue(app.isDescendant(e))
			assertTrue(app.isDescendant(e2))
			assertFalse(e2.isDescendant(e))
		}
    }
}
