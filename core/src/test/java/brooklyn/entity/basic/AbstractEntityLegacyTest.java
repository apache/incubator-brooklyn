package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;

/**
 * Tests the deprecated use of AbstractAppliation, where its constructor is called directly.
 * 
 * @author aled
 */
public class AbstractEntityLegacyTest {

    private List<SimulatedLocation> locs;
    private TestApplication app;
    
    @ImplementedBy(MyEntityImpl.class)
    public interface MyEntity extends Entity {
        int getConfigureCount();

        int getConfigureDuringConstructionCount();
    }
    
    public static class MyEntityImpl extends AbstractEntity implements MyEntity {
        volatile int configureCount;
        volatile int configureDuringConstructionCount;
        
        public MyEntityImpl() {
            super();
            configureDuringConstructionCount = configureCount;
        }
        
        @Override
        public AbstractEntity configure(Map flags) {
            configureCount++;
            return super.configure(flags);
        }
        
        @Override
        public int getConfigureCount() {
            return configureCount;
        }
        
        @Override
        public int getConfigureDuringConstructionCount() {
            return configureDuringConstructionCount;
        }
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }
    
    @Test
    public void testLegacyConstructionCallsConfigureMethod() throws Exception {
        MyEntity entity = new MyEntityImpl();
        
        assertEquals(entity.getConfigureCount(), 1);
        assertEquals(entity.getConfigureDuringConstructionCount(), 1);
    }
    
    @Test
    public void testNewStyleCallsConfigureAfterConstruction() throws Exception {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        MyEntity entity = app.createChild(BasicEntitySpec.newInstance(MyEntity.class));
        
        assertEquals(entity.getConfigureCount(), 1);
        assertEquals(entity.getConfigureDuringConstructionCount(), 0);
    }
}
