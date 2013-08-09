package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.util.collections.MutableMap;

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
        
        public MyEntityImpl(Entity parent) {
            super(parent);
            configureDuringConstructionCount = configureCount;
        }
        
        public MyEntityImpl(Map flags, Entity parent) {
            super(flags, parent);
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
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        MyEntity entity = app.createChild(EntitySpec.create(MyEntity.class));
        
        assertEquals(entity.getConfigureCount(), 1);
        assertEquals(entity.getConfigureDuringConstructionCount(), 0);
    }
    
    @Test
    public void testLegacyConstructionSetsDefaultDisplayName() throws Exception {
        app = new TestApplicationImpl();
        MyEntity entity = new MyEntityImpl(app);

        assertTrue(entity.getDisplayName().startsWith("MyEntityImpl:"+entity.getId().substring(0,4)), "displayName="+entity.getDisplayName());
        
        Entities.startManagement(app);
        assertTrue(entity.getDisplayName().startsWith("MyEntity:"+entity.getId().substring(0,4)), "displayName="+entity.getDisplayName());
    }
    
    @Test
    public void testLegacyConstructionUsesCustomDisplayName() throws Exception {
        app = new TestApplicationImpl(MutableMap.of("displayName", "appname"));
        MyEntity entity = new MyEntityImpl(MutableMap.of("displayName", "entityname"), app);
        MyEntity entity2 = new MyEntityImpl(MutableMap.of("name", "entityname2"), app);

        assertEquals(app.getDisplayName(), "appname");
        assertEquals(entity.getDisplayName(), "entityname");
        assertEquals(entity2.getDisplayName(), "entityname2");
    }
    
    @Test
    public void testNewStyleSetsDefaultDisplayName() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        MyEntity entity = app.createChild(EntitySpec.create(MyEntity.class));
        
        assertTrue(entity.getDisplayName().startsWith("MyEntity:"+entity.getId().substring(0,4)), "displayName="+entity.getDisplayName());
    }
    
    @Test
    public void testNewStyleUsesCustomDisplayName() throws Exception {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("appname"));
        MyEntity entity = app.createChild(EntitySpec.create(MyEntity.class).displayName("entityname"));
        
        assertEquals(app.getDisplayName(), "appname");
        assertEquals(entity.getDisplayName(), "entityname");
    }
}
