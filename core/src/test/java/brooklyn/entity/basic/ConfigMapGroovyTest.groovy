package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertTrue

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ConfigMapTest.MyOtherEntity
import brooklyn.entity.basic.ConfigMapTest.MySubEntity
import brooklyn.test.entity.TestApplication

public class ConfigMapGroovyTest {

    private TestApplication app;
    private MySubEntity entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = new MySubEntity(app);
        Entities.manage(entity);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testGetConfigOfTypeClosureReturnsClosure() throws Exception {
        MyOtherEntity entity2 = new MyOtherEntity(app);
        entity2.setConfig(MyOtherEntity.CLOSURE_KEY, { return "abc" } );
        Entities.manage(entity2);
        
        Closure configVal = entity2.getConfig(MyOtherEntity.CLOSURE_KEY);
        assertTrue(configVal instanceof Closure, "configVal="+configVal);
        assertEquals(configVal.call(), "abc");
    }


}
