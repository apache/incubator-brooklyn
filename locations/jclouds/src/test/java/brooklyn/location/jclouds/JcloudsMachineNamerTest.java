package brooklyn.location.jclouds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.text.Strings;

public class JcloudsMachineNamerTest {

    private static final Logger log = LoggerFactory.getLogger(JcloudsMachineNamerTest.class);
    
    private TestApplication app;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testGenerateGroupIdInVcloud() {
        ConfigBag cfg = new ConfigBag()
            .configure(JcloudsLocationConfig.CLOUD_PROVIDER, "vcloud")
            .configure(JcloudsLocationConfig.CALLER_CONTEXT, "!mycontext!");
        
        String result = new JcloudsMachineNamer(cfg).generateNewGroupId();
        
        log.info("test mycontext vcloud group id gives: "+result);
        // brooklyn-user-mycontext!-1234
        // br-user-myco-1234
        Assert.assertTrue(result.length() <= 15);
        
        String user = Strings.maxlen(System.getProperty("user.name"), 2).toLowerCase();
        // (length 2 will happen if user is brooklyn)
        Assert.assertTrue(result.indexOf(user) >= 0);
        Assert.assertTrue(result.indexOf("-myc") >= 0);
    }
    
}
