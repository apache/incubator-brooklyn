package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;

public class EntityManagerTest {

    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroy(app);
    }

    @Test
    public void testCreateEntityUsingSpec() {
        MyEntity entity = app.createAndManageChild(EntitySpecs.spec(MyEntity.class));
        TestEntity child = entity.createChild(EntitySpecs.spec(TestEntity.class).displayName("mychildname"));
        assertTrue(child instanceof EntityProxy, "child="+child);
        assertFalse(child instanceof TestEntityImpl, "child="+child);
        assertTrue(entity.getChildren().contains(child), "child="+child+"; children="+entity.getChildren());
        assertEquals(child.getDisplayName(), "mychildname");
    }
    
    @Test
    public void testCreateEntityUsingMapAndType() {
        MyEntity entity = app.createAndManageChild(EntitySpecs.spec(MyEntity.class));
        TestEntity child = entity.createChild(MutableMap.of("displayName", "mychildname"), TestEntity.class);
        assertTrue(child instanceof EntityProxy, "child="+child);
        assertFalse(child instanceof TestEntityImpl, "child="+child);
        assertTrue(entity.getChildren().contains(child), "child="+child+"; children="+entity.getChildren());
        assertEquals(child.getDisplayName(), "mychildname");
    }
    
    @ImplementedBy(MyEntityImpl.class)
    public static interface MyEntity extends Entity {
        /**
         * @deprecated Use {@link #addChild(EntitySpec)}
         */
        public <T extends Entity> T createChild(EntitySpec<T> spec);
        public <T extends Entity> T createChild(Map<?,?> flags, Class<T> type);
    }
    
    @ImplementedBy(MyEntityImpl.class)
    public static class MyEntityImpl extends AbstractEntity implements MyEntity {
        public <T extends Entity> T createChild(EntitySpec<T> spec) {
            return addChild(spec);
        }
        public <T extends Entity> T createChild(Map<?,?> flags, Class<T> type) {
            return addChild(EntitySpecs.spec(flags, type));
        }
    }
}
