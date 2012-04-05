package brooklyn.extras.cloudfoundry

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.Test

import brooklyn.extras.cloudfoundry.CloudFoundryVmcCliAccess.CloudFoundryAppStatLine
import brooklyn.extras.cloudfoundry.CloudFoundryVmcCliAccess.CloudFoundryAppStats
import brooklyn.test.TestUtils;
import brooklyn.util.IdGenerator


/** requires vmc installed and configured */
class CloudFoundryAccessIntegrationTest {
    
    static final Logger log = LoggerFactory.getLogger(CloudFoundryAccessIntegrationTest.class)
    
    @Test(groups = [ "Integration" ])
    public void testVmcInfo() {
        List lines = new CloudFoundryVmcCliAccess().exec("vmc info") as List;
        String user = lines.find({ it.startsWith("User:") })
        String version = lines.find({ it.startsWith("Client:") })
        Assert.assertNotNull("expected User: in output");
        user = user.substring(5).trim();
        version = version.substring(7).trim()
        log.info("vmc user is "+user+" and version is "+version);
        Assert.assertTrue(user.indexOf('@')>0);
    }

    @Test(groups = [ "Integration" ])
    public void testVmcAppsList() {
        List apps = new CloudFoundryVmcCliAccess().apps();
        log.info("vmc apps gives: "+apps)
        //don't know anything is present, just assert no error
    }

    @Test(groups = [ "Integration" ])
    public void testVmcAppCreateRunUpdateScaleStats() {
        String id = "brooklyn-"+IdGenerator.makeRandomId(8);
        CloudFoundryVmcCliAccess access = new CloudFoundryVmcCliAccess(appName: id);
        log.info("creating $id in ${access.appPath}");
        List apps1 = access.apps();
        try {
            //create
            access.runAppWar(war: "classpath://hello-world.war");
            List apps2 = access.apps();
            apps2.removeAll(apps1);
            assertEquals(apps2, [ id ])
            //update
            access.runAppWar(war: "classpath://hello-world.war");
            CloudFoundryAppStats stats = access.stats();
            assertEquals(stats.size, 1)
            //scale
            access.resizeDelta(1);
            stats = access.stats();
            log.info("stats $stats")
            assertEquals(stats.size, 2)
        } finally {
            log.info("destroying $id")
            access.destroyApp();
        }
        List apps3 = access.apps(true);
        log.info("apps now $apps3")
        assertEquals(apps3, apps1) 
    }

    @Test
    public void testParseStats() {
        CloudFoundryAppStatLine stats = 
            CloudFoundryAppStatLine.parse "| 0        | 0.0% (4)    | 116.6M (512M)  | 9.5M (2G)    | 0d:15h:41m:2s |"
        log.info("stats: "+stats);
        assertEquals(stats.memUsedMB, 116.6d);
    }
}
