package brooklyn.entity.webapp.tomcat;

import static brooklyn.test.TestUtils.isPortInUse;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.net.ServerSocket;

import org.jclouds.util.Throwables2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;

/**
 * This tests the operation of the {@link TomcatServer} entity.
 * 
 * FIXME this test is largely superseded by WebApp*IntegrationTest which tests inter alia Tomcat
 */
public class TomcatServerSimpleIntegrationTest {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(TomcatServerSimpleIntegrationTest.class);
    
    /** don't use 8080 since that is commonly used by testing software */
    static int DEFAULT_HTTP_PORT = 7880;

    static boolean httpPortLeftOpen = false;
    
    private TestApplication app;
    private TomcatServer tc;
    
    @BeforeMethod(alwaysRun=true)
    public void failIfHttpPortInUse() {
        if (isPortInUse(DEFAULT_HTTP_PORT, 5000L)) {
            httpPortLeftOpen = true;
            fail("someone is already listening on port "+DEFAULT_HTTP_PORT+"; tests assume that port "+DEFAULT_HTTP_PORT+" is free on localhost");
        }
    }
 
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }
    
    @Test(groups="Integration")
    public void detectFailureIfTomcatCantBindToPort() throws Exception {
        ServerSocket listener = new ServerSocket(DEFAULT_HTTP_PORT);
        try {
            app = ApplicationBuilder.newManagedApp(TestApplication.class);
            tc = app.createAndManageChild(EntitySpec.create(TomcatServer.class).configure("httpPort",DEFAULT_HTTP_PORT));
            
            try {
                tc.start(ImmutableList.of(app.getManagementContext().getLocationManager().manage(new LocalhostMachineProvisioningLocation())));
                fail("Should have thrown start-exception");
            } catch (Exception e) {
                // LocalhostMachineProvisioningLocation does NetworkUtils.isPortAvailable, so get -1
                IllegalArgumentException iae = Throwables2.getFirstThrowableOfType(e, IllegalArgumentException.class);
                if (iae == null || iae.getMessage() == null || !iae.getMessage().equals("port for httpPort is null")) throw e;
            } finally {
                tc.stop();
            }
            assertFalse(tc.getAttribute(TomcatServerImpl.SERVICE_UP));
        } finally {
            listener.close();
        }
    }
}
