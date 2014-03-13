package brooklyn.location.cloud;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;

import com.google.common.collect.ImmutableMap;

public class CustomMachineNamerTest {
    
    private TestApplication app;
    private TestEntity child;
    private ConfigBag config;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TestApp"));
        child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));
        config = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testMachineNameNoConfig() {
        config.configure(CloudLocationConfig.CALLER_CONTEXT, child);
        Assert.assertEquals(new CustomMachineNamer(config).generateNewMachineUniqueName(), "TestEnt");
    }
    
    @Test
    public void testMachineNameWithConfig() {
        child.setSequenceValue(999);
        config.configure(CustomMachineNamer.MACHINE_NAME_TEMPLATE, "number${entity.sequenceValue}");
        Assert.assertEquals(new CustomMachineNamer(config).generateNewMachineUniqueName(), "number999");
    }
    
    @Test
    public void testMachineNameWithExtraSubstitutions() {
        config.configure(CustomMachineNamer.MACHINE_NAME_TEMPLATE, "foo-${fooName}-bar-${barName}-baz-${bazName.substitution}")
            .configure(CustomMachineNamer.EXTRA_SUBSTITUTIONS, ImmutableMap.of("fooName", "foo", "barName", "bar", "bazName", this));
        Assert.assertEquals(new CustomMachineNamer(config).generateNewMachineUniqueName(), "foo-foo-bar-bar-baz-baz");
    }
    
    public String getSubstitution() {
        return "baz";
    }
}
