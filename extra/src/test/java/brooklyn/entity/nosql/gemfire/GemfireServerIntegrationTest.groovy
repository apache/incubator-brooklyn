package brooklyn.entity.nosql.gemfire

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.trait.Startable
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * This tests the operation of the {@link GemfireServer} entity.
 * 
 * TODO clarify test purpose
 */
public class GemfireServerIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(GemfireServerIntegrationTest.class)
    
    static { TimeExtras.init() }

    private String licenseFile = "/Users/aled/eclipse-workspaces/trunk/admin/gemfire-deployment/gemfire-staging/gemfire/GemFire6512/gemfireLicense.zip"
    private String installDir = "/Users/aled/eclipse-workspaces/cloudsoft/brooklyn/gemfire"
    private String jarFile = "/Users/aled/eclipse-workspaces/bixby-demo/com.cloudsoftcorp.sample.booking.webapp/src/main/webapp/WEB-INF/lib/com.cloudsoftcorp.sample.booking.svc.api_3.2.0.v20110317-295-10281.jar"
    private String configFile = "/Users/aled/eclipse-workspaces/cloudsoft/brooklyn/gemfire/test/resources/eu/cache.xml"
    
    @BeforeMethod(groups = [ "Integration" ])
    public void setUp() {
    }
 
    @Test(groups = [ "Integration" ])
    public void testGemfireStartsAndStops() {
        Application app = new TestApplication()
        GemfireServer entity = new GemfireServer(owner:app)
        entity.setConfig(GemfireServer.LICENSE, licenseFile);
        entity.setConfig(GemfireServer.SUGGESTED_INSTALL_DIR, installDir)
        entity.setConfig(GemfireServer.JAR_FILE, jarFile)
        entity.setConfig(GemfireServer.CONFIG_FILE, configFile)
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(entity) {
            assertTrue entity.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse entity.getAttribute(JavaApp.SERVICE_UP)
    }
}
