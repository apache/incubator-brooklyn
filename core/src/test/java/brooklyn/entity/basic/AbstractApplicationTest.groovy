package brooklyn.entity.basic

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestEntity

class AbstractApplicationTest {

    private AbstractApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = new AbstractApplication() {};
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }
    
    // App and its children will be implicitly managed on first effector call on app
    @Test
    public void testStartAndStopWhenUnmanagedCallsChildren() {
        TestEntity child = new TestEntity(parent:app);
        
        app.start([new SimulatedLocation()])
        assertEquals(child.counter.get(), 1)
        
        app.stop()
        assertEquals(child.counter.get(), 0)
    }
    
    @Test
    public void testStartAndStopWhenManagedCallsChildren() {
        TestEntity child = new TestEntity(parent:app);
        Entities.startManagement(app);
        
        app.start([new SimulatedLocation()])
        assertEquals(child.counter.get(), 1)
        
        app.stop()
        assertEquals(child.counter.get(), 0)
    }
    
    @Test
    public void testStartDoesNotStartPremanagedChildren() {
        Entities.startManagement(app);
        
        TestEntity child = new TestEntity(parent:app);
        
        app.start([new SimulatedLocation()])
        assertEquals(child.counter.get(), 0)
    }
    
    @Test
    public void testStartDoesNotStartUnmanagedChildren() {
        TestEntity child = new TestEntity(parent:app);
        Entities.startManagement(app);
        Entities.unmanage(child);
        
        app.start([new SimulatedLocation()])
        assertEquals(child.counter.get(), 0)
    }
    
    @Test
    public void testStopDoesNotStopUnmanagedChildren() {
        TestEntity child = new TestEntity(parent:app);
        Entities.startManagement(app);
        
        app.start([new SimulatedLocation()]);
        assertEquals(child.counter.get(), 1);
        
        Entities.unmanage(child);
        
        app.stop();
        assertEquals(child.counter.get(), 1);
    }
    
    @Test
    public void testStopDoesNotStopPremanagedChildren() {
        Entities.startManagement(app);

        app.start([new SimulatedLocation()]);
        
        TestEntity child = new TestEntity(parent:app);
        
        app.stop();
        assertEquals(child.counter.get(), 0);
    }
}
