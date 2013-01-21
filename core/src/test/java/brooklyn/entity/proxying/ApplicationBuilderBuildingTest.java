package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplicationImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.injava.ExampleJavaPolicy;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ApplicationBuilderBuildingTest {

    private Application app;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroy(app);
        app = null;
    }

    @Test
    public void testUsesDefaultBasicApplicationClass() {
        app = ApplicationBuilder.builder().manage();
        
        assertEquals(app.getEntityType().getName(), BasicApplicationImpl.class.getCanonicalName());
        assertIsProxy(app);
    }
    
    @Test
    public void testUsesSuppliedApplicationClass() {
        app = ApplicationBuilder.builder(BasicEntitySpec.newInstance(TestApplication.class)).manage();
        
        assertEquals(app.getEntityType().getName(), TestApplicationImpl.class.getName());
    }

    @Test
    public void testUsesSuppliedManagementContext() {
        ManagementContext managementContext = Entities.newManagementContext();
        app = ApplicationBuilder.builder().manage(managementContext);
        
        assertEquals(app.getManagementContext(), managementContext);
    }

    @Test
    public void testCreatesChildEntity() {
        app = ApplicationBuilder.builder()
                .child(TestEntity.Spec.newInstance())
                .manage();
        Entity child = Iterables.getOnlyElement(app.getChildren());
        
        assertIsProxy(child);
        assertEquals(child.getParent(), app);
        assertTrue(child instanceof TestEntity, "child="+child);
    }

    @Test
    public void testCreatesEntityWithPolicy() {
        ExampleJavaPolicy policy = new ExampleJavaPolicy();
        app = ApplicationBuilder.builder()
                .child(TestEntity.Spec.newInstance().policy(policy))
                .manage();
        Entity child = Iterables.getOnlyElement(app.getChildren());
        
        assertEquals(ImmutableList.copyOf(child.getPolicies()), ImmutableList.of(policy));
    }

    @Test
    public void testAppHierarchyIsManaged() {
        app = ApplicationBuilder.builder()
                .child(TestEntity.Spec.newInstance())
                .manage();
        
        assertIsManaged(app);
        assertIsManaged(Iterables.getOnlyElement(app.getChildren()));
    }

    private void assertIsProxy(Entity e) {
        assertFalse(e instanceof AbstractEntity, "e="+e+";e.class="+e.getClass());
        assertTrue(e instanceof EntityProxy, "e="+e+";e.class="+e.getClass());
    }
    
    private void assertIsManaged(Entity e) {
        assertTrue(((EntityLocal)e).getManagementSupport().isDeployed(), "e="+e);
    }
}
