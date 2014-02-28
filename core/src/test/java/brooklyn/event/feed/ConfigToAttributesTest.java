package brooklyn.event.feed;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.TemplatedStringAttributeSensorAndConfigKey;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

public class ConfigToAttributesTest {

    private ManagementContextInternal managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testApplyTemplatedConfigWithEntity() {
        TestApplication app = managementContext.getEntityManager().createEntity(EntitySpec.create(TestApplication.class)
                .configure(TestEntity.CONF_NAME, "myval"));
        Entities.startManagement(app, managementContext);
        
        BasicAttributeSensorAndConfigKey<String> key = new TemplatedStringAttributeSensorAndConfigKey("mykey", "my descr", "${config['test.confName']!'notfound'}");
        String val = ConfigToAttributes.apply(app, key);
        assertEquals(app.getAttribute(key), val);
        assertEquals(val, "myval");

    }
    
    @Test
    public void testApplyTemplatedConfigWithManagementContext() {
        managementContext.getBrooklynProperties().put(TestEntity.CONF_NAME, "myglobalval");
        BasicAttributeSensorAndConfigKey<String> key = new TemplatedStringAttributeSensorAndConfigKey("mykey", "my descr", "${config['test.confName']!'notfound'}");
        String val = ConfigToAttributes.transform(managementContext, key);
        assertEquals(val, "myglobalval");
    }
}
