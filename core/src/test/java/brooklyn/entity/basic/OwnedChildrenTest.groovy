package brooklyn.entity.basic

import brooklyn.management.internal.LocalManagementContext
import org.testng.annotations.AfterMethod

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity

public class OwnedChildrenTest {

    private Application app

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new AbstractApplication() {}
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        LocalManagementContext.terminateAll();
    }

    // Tests that the deprecated "owner" still works
    @Test
    public void testSetOwnerInConstructorMap() {
        Entity e = new AbstractEntity(owner:app) {}
        
        assertEquals(e.getParent(), app)
        assertEquals(app.getChildren(), [e])
        assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetParentInConstructorMap() {
        Entity e = new AbstractEntity(parent:app) {}
        
        assertEquals(e.getParent(), app)
        assertEquals(app.getChildren(), [e])
        assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetParentInConstructorArgument() {
        Entity e = new AbstractEntity(app) {}
        
        assertEquals(e.getParent(), app)
        assertEquals(app.getChildren(), [e])
        assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetParentInSetterMethod() {
        Entity e = new AbstractEntity() {}
        e.setParent(app)
        
        assertEquals(e.getParent(), app)
        assertEquals(app.getChildren(), [e])
        assertEquals(e.getApplication(), app)
    }

    @Test
    public void testAddChild() {
        Entity e = new AbstractEntity() {}
        app.addChild(e)
        
        assertEquals(e.getParent(), app)
        assertEquals(app.getChildren(), [e])
        assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetParentWhenMatchesParentSetInConstructor() {
        Entity e = new AbstractEntity(parent:app) {}
        e.setParent(app)
        
        assertEquals(e.getParent(), app)
        assertEquals(app.getChildren(), [e])
    }
    
    @Test(expectedExceptions = [ UnsupportedOperationException.class ])
    public void testSetParentWhenDiffersFromParentSetInConstructor() {
        Entity e = new AbstractEntity(parent:app) {}
        Entity e2 = new AbstractEntity() {}
        e.setParent(e2)
        fail();
    }
    
    @Test
    public void testParentCanHaveMultipleChildren() {
        Entity e = new AbstractEntity(parent:app) {}
        Entity e2 = new AbstractEntity(parent:app) {}
        
        assertEquals(e.getParent(), app)
        assertEquals(e2.getParent(), app)
        assertEquals(app.getChildren(), [e,e2])
    }
    
    @Test
    public void testHierarchyOfOwners() {
        Entity e = new AbstractEntity(parent:app) {}
        Entity e2 = new AbstractEntity(parent:e) {}
        Entity e3 = new AbstractEntity(parent:e2) {}
        
        assertEquals(app.getParent(), null)
        assertEquals(e.getParent(), app)
        assertEquals(e2.getParent(), e)
        assertEquals(e3.getParent(), e2)
        
        assertEquals(app.getChildren(), [e])
        assertEquals(e.getChildren(), [e2])
        assertEquals(e2.getChildren(), [e3])
        assertEquals(e3.getChildren(), [])
    }
    
    @Test(enabled = false) // FIXME fails currently
    public void testRemoveChild() {
        Entity e = new AbstractEntity(parent:app) {}
        app.removeChild(e)
        
        assertEquals(app.getChildren(), [])
        assertEquals(e.getParent(), null)
    }
    
    @Test
    public void testParentalLoopForbiddenViaAddChild() {
        Entity e = new AbstractEntity() {}
        Entity e2 = new AbstractEntity(parent:e) {}
        try {
            e2.addChild(e)
            fail()
        } catch (IllegalStateException ex) {
            // success
        }
        
        assertEquals(e.getChildren(), [e2])
        assertEquals(e2.getChildren(), [])
        assertEquals(e.getParent(), null)
        assertEquals(e2.getParent(), e)
    }
    
    @Test
    public void testParentalLoopForbiddenViaSetParent() {
        Entity e = new AbstractEntity() {}
        Entity e2 = new AbstractEntity(parent:e) {}
        try {
            e.setParent(e2)
            fail()
        } catch (IllegalStateException ex) {
			ex.printStackTrace();
            // success
        }
        assertEquals(e.getChildren(), [e2])
        assertEquals(e2.getChildren(), [])
        assertEquals(e.getParent(), null)
        assertEquals(e2.getParent(), e)
    }
    
    @Test(expectedExceptions = [ IllegalStateException.class ])
    public void testParentingOneselfForbidden() {
        AbstractEntity e = new AbstractEntity() {}
        e.addChild(e)
        fail()
    }
    
    @Test
    public void testIsAncestor() {
        AbstractEntity e = new AbstractEntity(parent:app) {}
        AbstractEntity e2 = new AbstractEntity(parent:e) {}
        
		use (Entities) {
			assertTrue(e2.isAncestor(app))
			assertTrue(e2.isAncestor(e))
			assertFalse(e.isAncestor(e2))
		}
    }
    
    @Test
    public void testIsDescendant() {
        AbstractEntity e = new AbstractEntity(parent:app) {}
        AbstractEntity e2 = new AbstractEntity(parent:e) {}

		use (Entities) {
			assertTrue(app.isDescendant(e))
			assertTrue(app.isDescendant(e2))
			assertFalse(e2.isDescendant(e))
		}
    }
}
