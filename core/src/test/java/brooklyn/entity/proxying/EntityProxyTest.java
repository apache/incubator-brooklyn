package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.Set;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.management.EntityManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class EntityProxyTest {

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(TestEntity.Spec.newInstance());
        managementContext = app.getManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testBuiltAppGivesProxies() {
        assertIsProxy(entity);
        assertIsProxy(app);
    }

    @Test
    public void testGetChildrenAndParentsReturnsProxies() {
        TestEntity child = (TestEntity) Iterables.get(app.getChildren(), 0);
        Application parent = (Application) child.getParent();
        
        assertIsProxy(child);
        assertIsProxy(parent);
    }
    
    @Test
    public void testEffectorOnProxyIsRecorded() {
        Object result = entity.identityEffector("abc");
        assertEquals(result, "abc");
        
        Set<Task<?>> tasks = managementContext.getExecutionManager().getTasksWithAllTags(
                ImmutableList.of(ManagementContextInternal.EFFECTOR_TAG, entity));
        Task<?> task = Iterables.get(tasks, 0);
        assertEquals(tasks.size(), 1, "tasks="+tasks);
        assertTrue(task.getDescription().contains("identityEffector"));
    }
    
    @Test
    public void testEntityManagerQueriesGiveProxies() {
        EntityManager entityManager = managementContext.getEntityManager();
        
        Application retrievedApp = (Application) entityManager.getEntity(app.getId());
        TestEntity retrievedEntity = (TestEntity) entityManager.getEntity(entity.getId());

        assertIsProxy(retrievedApp);
        assertIsProxy(retrievedEntity);
        
        Collection<Entity> entities = entityManager.getEntities();
        for (Entity e : entities) {
            assertIsProxy(e);
        }
        assertEquals(ImmutableSet.copyOf(entities), ImmutableSet.of(app, entity));
    }

    @Test
    public void testCreateAndManageChild() {
        TestEntity result = entity.createAndManageChild(TestEntity.Spec.newInstance());
        assertIsProxy(result);
        assertIsProxy(Iterables.get(entity.getChildren(), 0));
        assertIsProxy(result.getParent());
        assertIsProxy(managementContext.getEntityManager().getEntity(result.getId()));
    }

    @Test
    public void testDisplayName() {
        TestEntity result = entity.createAndManageChild(TestEntity.Spec.newInstance().displayName("Boo"));
        assertIsProxy(result);
        assertEquals(result.getDisplayName(), "Boo");
    }

    @Test
    public void testCreateRespectsFlags() {
        TestEntity entity2 = app.createAndManageChild(TestEntity.Spec.newInstance().
                configure("confName", "boo"));
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "boo");
    }

    @Test
    public void testCreateRespectsConfigKey() {
        TestEntity entity2 = app.createAndManageChild(TestEntity.Spec.newInstance().
                configure(TestEntity.CONF_NAME, "foo"));
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "foo");
    }

    @Test
    public void testCreateRespectsConfInMap() {
        TestEntity entity2 = app.createAndManageChild(TestEntity.Spec.newInstance().
                configure(MutableMap.of(TestEntity.CONF_NAME, "bar")));
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "bar");
    }

    @Test
    public void testCreateRespectsFlagInMap() {
        TestEntity entity2 = app.createAndManageChild(TestEntity.Spec.newInstance().
                configure(MutableMap.of("confName", "baz")));
        assertEquals(entity2.getConfig(TestEntity.CONF_NAME), "baz");
    }

    @Test
    public void testCreateInAppWithClassAndMap() {
        StartableApplication app2 = null;
        try {
            ApplicationBuilder appB = new ApplicationBuilder() {
                @Override
                protected void doBuild() {
                    addChild(MutableMap.of("confName", "faz"), TestEntity.class);
                }
            };
            app2 = appB.manage();
            assertEquals(Iterables.getOnlyElement(app2.getChildren()).getConfig(TestEntity.CONF_NAME), "faz");
        } finally {
            if (app2 != null) Entities.destroyAll(app2);
        }
    }

    private void assertIsProxy(Entity e) {
        assertFalse(e instanceof AbstractEntity, "e="+e+";e.class="+(e != null ? e.getClass() : null));
        assertTrue(e instanceof EntityProxy, "e="+e+";e.class="+(e != null ? e.getClass() : null));
    }
}
