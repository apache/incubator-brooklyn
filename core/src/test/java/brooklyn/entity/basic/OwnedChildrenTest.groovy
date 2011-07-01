package brooklyn.entity.basic

import org.testng.Assert
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity

public class OwnedChildrenTest {

    private AbstractApplication app

    @BeforeMethod
    public void setUp() {
        app = new AbstractApplication() {}
    }
    
    @Test
    public void testSetOwnerInConstructorMap() {
        Entity e = new AbstractEntity(owner:app) {}
        
        Assert.assertEquals(app, e.getOwner())
        Assert.assertEquals(app.getOwnedChildren(), [e])
        Assert.assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetOwnerInConstructorArgument() {
        Entity e = new AbstractEntity(app) {}
        
        Assert.assertEquals(app, e.getOwner())
        Assert.assertEquals(app.getOwnedChildren(), [e])
        Assert.assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetOwnerInSetterMethod() {
        AbstractEntity e = new AbstractEntity() {}
        e.setOwner(app)
        
        Assert.assertEquals(app, e.getOwner())
        Assert.assertEquals(app.getOwnedChildren(), [e])
        Assert.assertEquals(e.getApplication(), app)
    }

    @Test
    public void testAddOwnedChild() {
        AbstractEntity e = new AbstractEntity() {}
        app.addOwnedChild(e)
        
        Assert.assertEquals(app, e.getOwner())
        Assert.assertEquals(app.getOwnedChildren(), [e])
        Assert.assertEquals(e.getApplication(), app)
    }
    
    @Test
    public void testSetOwnerWhenMatchesOwnerSetInConstructor() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        e.setOwner(app)
        
        Assert.assertEquals(e.getOwner(), app)
        Assert.assertEquals(app.getOwnedChildren(), [e])
    }
    
    @Test
    public void testSetOwnerWhenDiffersFromOwnerSetInConstructor() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        AbstractEntity e2 = new AbstractEntity() {}
        try {
            e.setOwner(e2)
            Assert.fail();
        } catch (UnsupportedOperationException ex) {
            // success; can't change owner
        }
    }
    
    @Test
    public void testOwnerCanHaveMultipleChildren() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        AbstractEntity e2 = new AbstractEntity(owner:app) {}
        
        Assert.assertEquals(e.getOwner(), app)
        Assert.assertEquals(e2.getOwner(), app)
        Assert.assertEquals(app.getOwnedChildren(), [e,e2])
    }
    
    @Test
    public void testHierarchyOfOwners() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        AbstractEntity e2 = new AbstractEntity(owner:e) {}
        AbstractEntity e3 = new AbstractEntity(owner:e2) {}
        
        Assert.assertEquals(app.getOwner(), null)
        Assert.assertEquals(e.getOwner(), app)
        Assert.assertEquals(e2.getOwner(), e)
        Assert.assertEquals(e3.getOwner(), e2)
        
        Assert.assertEquals(app.getOwnedChildren(), [e])
        Assert.assertEquals(e.getOwnedChildren(), [e2])
        Assert.assertEquals(e2.getOwnedChildren(), [e3])
        Assert.assertEquals(e3.getOwnedChildren(), [])
    }
    
    // @Test // FIXME fails currently
    public void testRemoveOwnedChild() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        app.removeOwnedChild(e)
        
        Assert.assertEquals(app.getOwnedChildren(), [])
        Assert.assertEquals(e.getOwner(), null)
    }
    
    @Test
    public void testOwnershipLoopForbiddenViaAddOwnedChild() {
        AbstractEntity e = new AbstractEntity() {}
        AbstractEntity e2 = new AbstractEntity(owner:e) {}
        try {
            e2.addOwnedChild(e)
            Assert.fail()
        } catch (IllegalStateException ex) {
            // success
        }
        
        Assert.assertEquals(e.getOwnedChildren(), [e2])
        Assert.assertEquals(e2.getOwnedChildren(), [])
        Assert.assertEquals(e.getOwner(), null)
        Assert.assertEquals(e2.getOwner(), e)
    }
    
    @Test
    public void testOwnershipLoopForbiddenViaSetOwner() {
        AbstractEntity e = new AbstractEntity() {}
        AbstractEntity e2 = new AbstractEntity(owner:e) {}
        try {
            e.setOwner(e2)
            Assert.fail()
        } catch (IllegalStateException ex) {
            // success
        }
        
        Assert.assertEquals(e.getOwnedChildren(), [e2])
        Assert.assertEquals(e2.getOwnedChildren(), [])
        Assert.assertEquals(e.getOwner(), null)
        Assert.assertEquals(e2.getOwner(), e)
    }
    
    @Test
    public void testOwningOneselfForbidden() {
        AbstractEntity e = new AbstractEntity() {}
        try {
            e.addOwnedChild(e)
            Assert.fail()
        } catch (IllegalStateException ex) {
            // success
        }
    }
    
    @Test
    public void testIsAncestor() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        AbstractEntity e2 = new AbstractEntity(owner:e) {}
        
        Assert.assertTrue(e2.isAncestor(app))
        Assert.assertTrue(e2.isAncestor(e))
        Assert.assertFalse(e.isAncestor(e2))
    }
    
    @Test
    public void testIsDescendant() {
        AbstractEntity e = new AbstractEntity(owner:app) {}
        AbstractEntity e2 = new AbstractEntity(owner:e) {}
        
        Assert.assertTrue(app.isDescendant(e))
        Assert.assertTrue(app.isDescendant(e2))
        Assert.assertFalse(e2.isDescendant(e))
    }
}
