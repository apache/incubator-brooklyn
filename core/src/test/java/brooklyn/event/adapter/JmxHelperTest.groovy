package brooklyn.event.adapter

import static org.testng.Assert.*

import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.test.GeneralisedDynamicMBean
import brooklyn.test.JmxService

/**
 * Test the operation of the {@link OldJmxSensorAdapter} class.
 * 
 * TODO clarify test purpose
 */
public class JmxHelperTest {
    private static final Logger log = LoggerFactory.getLogger(JmxHelperTest.class)

    private static final int TIMEOUT = 5000
    private static final int SHORT_WAIT = 250
    
    private JmxService jmxService
    private JmxHelper jmxHelper
    
    private String objectName = 'Brooklyn:type=MyTestMBean,name=myname'
    private ObjectName jmxObjectName = new ObjectName('Brooklyn:type=MyTestMBean,name=myname')
    private String attributeName = 'myattrib'
    private String opName = 'myop'
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        jmxHelper = new JmxHelper(JmxHelper.toConnectorUrl("localhost", 40123, null, "jmxrmi"))
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (jmxHelper != null) jmxHelper.disconnect();
        if (jmxService != null) jmxService.shutdown();
    }

    @Test
    public void testGetAttribute() {
        jmxService = new JmxService("localhost", 40123)
        jmxHelper.connect()
        
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): 42)
        assertEquals(jmxHelper.getAttribute(jmxObjectName, attributeName), 42)
    }

    @Test
    public void testReconnects() {
        jmxService = new JmxService("localhost", 40123)
        jmxHelper.connect()

        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): 42)
        assertEquals(jmxHelper.getAttribute(jmxObjectName, attributeName), 42)
        
        // Simulate temporary network-failure
        jmxService.shutdown()

        // Ensure that we have a failed query while the "network is down"         
        try {
            assertEquals(jmxHelper.getAttribute(jmxObjectName, attributeName), 42)
            fail()
        } catch (IOException e) {
            // success
        }

        // Simulate the network restarting
        jmxService = new JmxService("localhost", 40123)
        
        GeneralisedDynamicMBean mbean2 = jmxService.registerMBean(objectName, (attributeName): 43)
        assertEquals(jmxHelper.getAttribute(jmxObjectName, attributeName), 43)
    }
}
