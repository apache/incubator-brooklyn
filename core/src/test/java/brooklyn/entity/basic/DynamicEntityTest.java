package brooklyn.entity.basic;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.effector.EffectorTaskTest;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.util.collections.MutableMap;

public class DynamicEntityTest {

    Application app;
    
    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(BasicApplication.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testEffectorAddedDuringInit() {
        BasicEntity entity = app.addChild(EntitySpec.create(BasicEntity.class)
            .addInitializer(new EntityInitializer() {
                public void apply(EntityLocal entity) {
                    ((EntityInternal)entity).getMutableEntityType().addEffector(EffectorTaskTest.DOUBLE_1);
                }
            }));
        // TODO why doesn't the call to addChild above automatically manage the child (now that we use specs for creation) ?
        // (if there is a good reason, put it in addChild!)
        Entities.manage(entity);
        
        Assert.assertEquals(entity.invoke(EffectorTaskTest.DOUBLE_BODYLESS, MutableMap.of("numberToDouble", 5)).getUnchecked(), (Integer)10);
    }
    
}
