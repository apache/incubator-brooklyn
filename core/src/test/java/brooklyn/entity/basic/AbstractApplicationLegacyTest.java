package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.ExecutionException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Tests the deprecated use of AbstractAppliation, where its constructor is called directly.
 * 
 * @author aled
 */
public class AbstractApplicationLegacyTest {

    private List<SimulatedLocation> locs;
    private AbstractApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        locs = ImmutableList.of(new SimulatedLocation());
        app = new AbstractApplication() {};
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }
    
    // App and its children will be implicitly managed on first effector call on app
    @Test
    public void testStartAndStopCallsChildren() throws Exception {
        TestEntity child = new TestEntityImpl(app);
        
        app.invoke(AbstractApplication.START, ImmutableMap.of("locations", locs)).get();
        assertEquals(child.getCount(), 1);
        assertEquals(app.getManagementContext().getEntityManager().getEntity(app.getId()), app);
        assertEquals(app.getManagementContext().getEntityManager().getEntity(child.getId()), child);
        
        app.stop();
        assertEquals(child.getCount(), 0);
    }
    
    @Test
    public void testStartAndStopWhenManagedCallsChildren() {
        TestEntity child = new TestEntityImpl(app);
        Entities.startManagement(app);

        assertEquals(app.getManagementContext().getEntityManager().getEntity(app.getId()), app);
        assertEquals(app.getManagementContext().getEntityManager().getEntity(child.getId()), child);

        app.start(locs);
        assertEquals(child.getCount(), 1);
        
        app.stop();
        assertEquals(child.getCount(), 0);
    }
    
    @Test
    public void testStartDoesNotStartPremanagedChildren() {
        Entities.startManagement(app);
        
        TestEntity child = new TestEntityImpl(app);
        
        app.start(locs);
        assertEquals(child.getCount(), 0);
    }
    
    @Test
    public void testStartDoesNotStartUnmanagedChildren() {
        TestEntity child = new TestEntityImpl(app);
        Entities.startManagement(app);
        Entities.unmanage(child);
        
        app.start(locs);
        assertEquals(child.getCount(), 0);
    }
    
    @Test
    public void testStopDoesNotStopUnmanagedChildren() {
        TestEntity child = new TestEntityImpl(app);
        Entities.startManagement(app);
        
        app.start(locs);
        assertEquals(child.getCount(), 1);
        
        Entities.unmanage(child);
        
        app.stop();
        assertEquals(child.getCount(), 1);
    }
    
    @Test
    public void testStopDoesNotStopPremanagedChildren() {
        Entities.startManagement(app);

        app.start(locs);
        
        TestEntity child = new TestEntityImpl(app);
        
        app.stop();
        assertEquals(child.getCount(), 0);
    }
}
