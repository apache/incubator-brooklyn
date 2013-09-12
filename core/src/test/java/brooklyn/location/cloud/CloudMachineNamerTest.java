package brooklyn.location.cloud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

public class CloudMachineNamerTest {

    private static final Logger log = LoggerFactory.getLogger(CloudMachineNamerTest.class);
    
    private TestApplication app;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testGenerateGroupIdWithEntity() {
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class).displayName("TistApp"));
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("TestEnt"));

        ConfigBag cfg = new ConfigBag()
            .configure(CloudLocationConfig.CALLER_CONTEXT, child);

        String result = new CloudMachineNamer(cfg).generateNewGroupId();

        log.info("test entity child group id gives: "+result);
        // e.g. brooklyn-alex-tistapp-uube-testent-xisg-rwad
        Assert.assertTrue(result.length() <= 60);

        String user = Strings.maxlen(System.getProperty("user.name"), 4).toLowerCase();
        Assert.assertTrue(result.indexOf(user) >= 0);
        Assert.assertTrue(result.indexOf("-tistapp-") >= 0);
        Assert.assertTrue(result.indexOf("-testent-") >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(app.getId(), 4).toLowerCase()) >= 0);
        Assert.assertTrue(result.indexOf("-"+Strings.maxlen(child.getId(), 4).toLowerCase()) >= 0);
    }

    @Test
    public void testSanitize() {
        Assert.assertEquals(
                CloudMachineNamer.sanitize("me & you like _underscores but not !!! or dots...dots...dots"),
                "me-you-like-_underscores-but-not-or-dots-dots-dots"
            );
    }
}
