package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.management.EntityManager;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

public class EntityManagerTest extends BrooklynAppUnitTestSupport {

    private EntityManager entityManager;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entityManager = mgmt.getEntityManager();
    }
    
    @Test
    public void testCreateEntityUsingSpec() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity child = entity.addChild(EntitySpec.create(TestEntity.class).displayName("mychildname"));
        assertTrue(child instanceof EntityProxy, "child="+child);
        assertFalse(child instanceof TestEntityImpl, "child="+child);
        assertTrue(entity.getChildren().contains(child), "child="+child+"; children="+entity.getChildren());
        assertEquals(child.getDisplayName(), "mychildname");
    }
    
    @Test
    public void testCreateEntityUsingMapAndType() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity child = entity.addChild(EntitySpec.create(MutableMap.of("displayName", "mychildname"), TestEntity.class));
        assertTrue(child instanceof EntityProxy, "child="+child);
        assertFalse(child instanceof TestEntityImpl, "child="+child);
        assertTrue(entity.getChildren().contains(child), "child="+child+"; children="+entity.getChildren());
        assertEquals(child.getDisplayName(), "mychildname");
    }
    
    @Test
    public void testGetEntities() {
        TestApplication app2 = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity child = entity.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        Asserts.assertEqualsIgnoringOrder(entityManager.getEntitiesInApplication(app), ImmutableList.of(app, entity, child));
        Asserts.assertEqualsIgnoringOrder(entityManager.getEntities(), ImmutableList.of(app, entity, child, app2));
        Asserts.assertEqualsIgnoringOrder(entityManager.findEntities(Predicates.instanceOf(TestApplication.class)), ImmutableList.of(app, app2));
        Asserts.assertEqualsIgnoringOrder(entityManager.findEntitiesInApplication(app, Predicates.instanceOf(TestApplication.class)), ImmutableList.of(app));
    }
}
