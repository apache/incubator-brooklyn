package brooklyn.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;

/**
 * To be extended by live tests.
 * <p>
 * Uses a management context that will not load {@code ~/.brooklyn/catalog.xml} but will
 * read from the default {@code ~/.brooklyn/brooklyn.properties}.
 */
public class BrooklynAppLiveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynAppLiveTestSupport.class);

    protected TestApplication app;
    protected ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        if (mgmt!=null) {
            app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        } else {
            mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
            app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        }
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (mgmt != null) Entities.destroyAll(mgmt);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDown method", t);
        } finally {
            mgmt = null;
        }
    }

}
