package brooklyn.entity.salt;

import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class SaltConfigsTest {

    private TestApplication app = null;

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app!=null) Entities.destroyAll(app.getManagementContext());
        app = null;
    }
    
    @Test
    public void testAddToRunList() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        SaltConfigs.addToRunList(app, "a", "b");
        Set<? extends String> runs = app.getConfig(SaltConfig.SALT_RUN_LIST);
        Assert.assertEquals(runs, ImmutableSet.of("a", "b"));
    }
    
    @Test
    public void testAddToFormulas() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        SaltConfigs.addToFormulas(app, "k1", "v1");
        SaltConfigs.addToFormulas(app, "k2", "v2");
        Map<String, String> formulas = app.getConfig(SaltConfig.SALT_FORMULAS);
        Assert.assertEquals(formulas, ImmutableMap.of("k1", "v1", "k2", "v2"));
    }
    
    @Test
    public void testAddLaunchAttributes() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        SaltConfigs.addLaunchAttributes(app, ImmutableMap.of("k1", "v1"));
        Map<String, Object> attribs = app.getConfig(SaltConfig.SALT_LAUNCH_ATTRIBUTES);
        Assert.assertEquals(attribs, ImmutableMap.of("k1", "v1"));
    }
}
