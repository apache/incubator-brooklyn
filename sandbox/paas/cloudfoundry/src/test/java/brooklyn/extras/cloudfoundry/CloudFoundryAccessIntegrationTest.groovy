package brooklyn.extras.cloudfoundry

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.Test

import com.google.common.collect.Sets;

import brooklyn.extras.cloudfoundry.CloudFoundryVmcCliAccess.AppRecord
import brooklyn.extras.cloudfoundry.CloudFoundryVmcCliAccess.CloudFoundryAppStatLine
import brooklyn.extras.cloudfoundry.CloudFoundryVmcCliAccess.CloudFoundryAppStats
import brooklyn.util.IdGenerator


/** requires vmc installed and configured */
class CloudFoundryAccessIntegrationTest {
    
    static final Logger log = LoggerFactory.getLogger(CloudFoundryAccessIntegrationTest.class)
    
    @Test(groups = [ "Integration" ])
    public void testVmcInfo() {
        List lines = new CloudFoundryVmcCliAccess().exec("vmc info") as List;
        String user = lines.find({ it.startsWith("User:") })
        String version = lines.find({ it.startsWith("Client:") })
        Assert.assertNotNull(user, "expected User: in output");
        user = user.substring(5).trim();
        version = version.substring(7).trim()
        log.info("vmc user is "+user+" and version is "+version);
        Assert.assertTrue(user.indexOf('@')>0);
    }

    @Test(groups = [ "Integration" ])
    public void testVmcAppsList() {
        Collection apps = new CloudFoundryVmcCliAccess().appNames;
        log.info("vmc apps gives: "+apps)
        //don't know anything is present, just assert no error
    }

    @Test(groups = [ "Integration" ])
    public void testVmcAppCreateRunUpdateScaleStats() {
        String id = "brooklyn-"+IdGenerator.makeRandomId(8).toLowerCase();
        CloudFoundryVmcCliAccess access = new CloudFoundryVmcCliAccess(appName: id);
        log.info("creating $id in ${access.appPath}");
        Collection apps1 = access.getAppNames(true);
        try {
            //create
            AppRecord record = access.runAppWar(war: "classpath://hello-world.war");
            Collection apps2 = Sets.difference(access.getAppNames(true), apps1);
            assertEquals(apps2, [ id ])
            //check record
            assertEquals(record.size, 1)
            assertEquals(record.url, id+"."+"cloudfoundry.com")
            assertEquals(record.state, "RUNNING")
            assertEquals(record.services, [])
            AppRecord r2 = access.getAppRecord(id);
            assertEquals(record, r2);
            //update
            access.runAppWar(war: "classpath://hello-world.war");
            CloudFoundryAppStats stats = access.stats();
            assertEquals(stats.size, 1)
            //scale
            access.resizeDelta(1);
            stats = access.stats();
            log.info("stats $stats")
            assertEquals(stats.size, 2)
//            //set url -- gives 702 not enabled
//            AppRecord r3 = access.runAppWar(war: "classpath://hello-world.war", url: "foo.bar.com");
//            assertEquals(r3, "foo.bar.com");
        } finally {
            log.info("destroying $id")
            access.destroyApp();
        }
        Collection apps3 = access.getAppNames(true);
        log.info("apps now $apps3")
        assertEquals(apps3, apps1) 
    }

}
