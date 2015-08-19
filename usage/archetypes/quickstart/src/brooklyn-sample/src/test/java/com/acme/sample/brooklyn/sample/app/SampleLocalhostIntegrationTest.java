package com.acme.sample.brooklyn.sample.app;

import java.util.Arrays;
import java.util.Iterator;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Sample integration tests which show how to launch the sample applications on localhost,
 * make some assertions about them, and then destroy them.
 */
@Test(groups="Integration")
public class SampleLocalhostIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SampleLocalhostIntegrationTest.class);
    
    private ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setup() {
        mgmt = new LocalManagementContext();
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }


    public void testSingle() {
        StartableApplication app = mgmt.getEntityManager().createEntity(
                EntitySpec.create(StartableApplication.class, SingleWebServerSample.class));
        Entities.startManagement(app, mgmt);
        Entities.start(app, Arrays.asList(mgmt.getLocationRegistry().resolve("localhost")));
        
        Iterator<Entity> children = app.getChildren().iterator();
        if (!children.hasNext()) Assert.fail("Should have had a single JBoss child; had none");
        
        Entity web = children.next();
        
        if (children.hasNext()) Assert.fail("Should have had a single JBoss child; had too many: "+app.getChildren());
        
        String url = web.getAttribute(JavaWebAppService.ROOT_URL);
        Assert.assertNotNull(url);
        
        String page = new ResourceUtils(this).getResourceAsString(url);
        log.info("Read web page for "+app+" from "+url+":\n"+page);
        Assert.assertTrue(!Strings.isBlank(page));
    }

    public void testCluster() {
        StartableApplication app = mgmt.getEntityManager().createEntity(
                EntitySpec.create(StartableApplication.class, ClusterWebServerDatabaseSample.class));
        Entities.startManagement(app, mgmt);
        Entities.start(app, Arrays.asList(mgmt.getLocationRegistry().resolve("localhost")));
        
        log.debug("APP is started");
        
        String url = app.getAttribute(JavaWebAppService.ROOT_URL);
        Assert.assertNotNull(url);

        String page = new ResourceUtils(this).getResourceAsString(url);
        log.info("Read web page for "+app+" from "+url+":\n"+page);
        Assert.assertTrue(!Strings.isBlank(page));
    }
    
}
