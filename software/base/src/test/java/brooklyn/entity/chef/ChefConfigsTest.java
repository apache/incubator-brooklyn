package brooklyn.entity.chef;

import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.test.entity.TestApplication;

public class ChefConfigsTest {

    private TestApplication app = null;

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app!=null) Entities.destroyAll(app.getManagementContext());
        app = null;
    }
    
    @Test
    public void testAddToRunList() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        ChefConfigs.addToRunList(app, "a", "b");
        Set<? extends String> runs = app.getConfig(ChefConfig.CHEF_RUN_LIST);
        Assert.assertEquals(runs.size(), 2, "runs="+runs);
        Assert.assertTrue(runs.contains("a"));
        Assert.assertTrue(runs.contains("b"));
    }
    
}
