package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class ApplicationBuilderOverridingTest {

    private Application app;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroy(app);
        app = null;
    }

    @Test
    public void testUsesDefaultBasicApplicationClass() {
        app = new ApplicationBuilder() {
            @Override public void doBuild() {}
        }.manage();
        
        assertEquals(app.getEntityType().getName(), BasicApplicationImpl.class.getCanonicalName());
        assertIsProxy(app);
    }
    
    @Test
    public void testUsesSuppliedApplicationClass() {
        app = new ApplicationBuilder(BasicEntitySpec.newInstance(TestApplication.class)) {
            @Override public void doBuild() {}
        }.manage();
        
        assertEquals(app.getEntityType().getName(), TestApplicationImpl.class.getName());
    }

    @Test
    public void testUsesSuppliedManagementContext() {
        ManagementContext managementContext = Entities.newManagementContext();
        app = new ApplicationBuilder() {
            @Override public void doBuild() {}
        }.manage(managementContext);
        
        assertEquals(app.getManagementContext(), managementContext);
    }

    @Test
    public void testCreatesChildEntity() {
        final AtomicReference<TestEntity> expectedChild = new AtomicReference<TestEntity>();
        app = new ApplicationBuilder() {
            @Override public void doBuild() {
                expectedChild.set(createChild(TestEntity.Spec.newInstance()));
            }
        }.manage();
        
        assertIsProxy(expectedChild.get());
        assertEquals(ImmutableSet.copyOf(app.getChildren()), ImmutableSet.of(expectedChild.get()));
        assertEquals(expectedChild.get().getParent(), app);
    }

    @Test
    public void testAppHierarchyIsManaged() {
        final AtomicReference<TestEntity> expectedChild = new AtomicReference<TestEntity>();
        app = new ApplicationBuilder() {
            @Override public void doBuild() {
                Entity entity = createChild(TestEntity.Spec.newInstance());
                assertFalse(getManagementContext().getEntityManager().isManaged(entity));
            }
        }.manage();
        
        assertIsManaged(app);
        assertIsManaged(Iterables.get(app.getChildren(), 0));
    }

    private void assertIsProxy(Entity e) {
        assertFalse(e instanceof AbstractEntity, "e="+e+";e.class="+e.getClass());
        assertTrue(e instanceof EntityProxy, "e="+e+";e.class="+e.getClass());
    }
    
    private void assertIsManaged(Entity e) {
        assertTrue(((EntityLocal)e).getManagementSupport().isDeployed(), "e="+e);
    }
}
