package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

public class EntityFunctionsTest {

    private TestApplication app;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("mydisplayname"));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testAttribute() throws Exception {
        entity.setAttribute(TestEntity.NAME, "myname");
        assertEquals(EntityFunctions.attribute(TestEntity.NAME).apply(entity), "myname");
        assertNull(EntityFunctions.attribute(TestEntity.SEQUENCE).apply(entity));
    }
    
    @Test
    public void testConfig() throws Exception {
        entity.setConfig(TestEntity.CONF_NAME, "myname");
        assertEquals(EntityFunctions.config(TestEntity.CONF_NAME).apply(entity), "myname");
        assertNull(EntityFunctions.config(TestEntity.CONF_OBJECT).apply(entity));
    }
    
    @Test
    public void testDisplayName() throws Exception {
        assertEquals(EntityFunctions.displayName().apply(entity), "mydisplayname");
    }
    
    @Test
    public void testId() throws Exception {
        assertEquals(EntityFunctions.id().apply(entity), entity.getId());
    }
}
