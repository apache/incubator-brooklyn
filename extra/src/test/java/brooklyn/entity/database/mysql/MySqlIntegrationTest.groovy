package brooklyn.entity.database.mysql;

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication

public class MySqlIntegrationTest {

    public static final Logger log = LoggerFactory.getLogger(MySqlIntegrationTest.class);
    
    @BeforeMethod(groups = [ "Integration" ])
    public void ensureNoInstance() {
    }
 
    @AfterMethod(groups = [ "Integration" ])
    public void ensureShutDown() {
    }

    @Test(groups = [ "Integration" ])
    public void runIt() {
        TestApplication tapp = new TestApplication(name: "MySqlIntegrationTest");
        MySqlNode mysql = new MySqlNode(tapp)
        
        try {
            tapp.start([new LocalhostMachineProvisioningLocation()]);
            
            log.info("MySQL started");
            
            System.in.read();
        } finally {
            tapp.destroy();
        }
    }
    
}
