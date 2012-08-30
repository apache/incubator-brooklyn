package brooklyn.entity.java

import static org.testng.Assert.*

import javax.management.ObjectName

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.basic.SimulatedLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.test.GeneralisedDynamicMBean
import brooklyn.test.JmxService
import brooklyn.test.TestUtils

public class EntityPollingTest {

    private static final int TIMEOUT_MS = 5000
    private static final int SHORT_WAIT = 250
    
    private JmxService jmxService
    private AbstractApplication app
    private Entity entity
    
    private BasicAttributeSensor<String> stringAttribute = [ String, "brooklyn.test.stringAttribute", "Brooklyn testing int attribute" ]
    private String objectName = 'Brooklyn:type=MyTestMBean,name=myname'
    private ObjectName jmxObjectName = new ObjectName('Brooklyn:type=MyTestMBean,name=myname')
    private String attributeName = 'myattrib'
    private String opName = 'myop'
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new AbstractApplication() {}

		/*
		 * Create an entity, using real entity code, but that swaps out the external process
		 * for a JmxService that we can control in the test.        
		 */
        entity = new VanillaJavaApp(owner:app,
                jmxPort:40123,
                jmxContext:null,
                mxbeanStatsEnabled:false) {

            @Override protected void connectSensors() {
                super.connectSensors();
                
				// Add a sensor that we can explicitly set in jmx
                jmxAdapter.objectName(jmxObjectName).with {
                    attribute(attributeName).subscribe(stringAttribute)
                }
            }
            
            @Override
            public VanillaJavaAppSshDriver newDriver(SshMachineLocation loc) {
                new VanillaJavaAppSshDriver(this, loc) {
                    @Override public void install() {
                        // no-op
                    }
                    @Override public void customize() {
                        // no-op
                    }
                    @Override public void launch() {
                        // no-op
                    }
                    @Override public boolean isRunning() {
                        return true
                    }
                    @Override public void stop() {
                        // no-op
                    }
                }
            }
        };        
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        app?.stop()
        jmxService?.shutdown()
    }

	// Tests that the happy path works
    @Test(groups="Integration")
    public void testSimpleConnection() {
        jmxService = new JmxService("localhost", 40123)
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): "myval")

        app.start([new SshMachineLocation(address:"localhost")])
        
        // Starts with value defined when registering...
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals entity.getAttribute(stringAttribute), "myval"
        }
    }

	// Test that connect will keep retrying (e.g. start script returns before the JMX server is up)
    @Test(groups="Integration")
    public void testEntityWithDelayedJmxStartupWillKeepRetrying() {
		// In 2 seconds time, we'll start the JMX server
        Thread t = new Thread({
            Thread.sleep(2000)
            jmxService = new JmxService("localhost", 40123)
            GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): "myval")
        })
        try {
            t.start()
            app.start([new SshMachineLocation(address:"localhost")])

            TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
                assertEquals entity.getAttribute(stringAttribute), "myval"
            }
            
        } finally {
            t.interrupt()
        }
    }
    
    @Test(groups="Integration")
    public void testJmxConnectionGoesDownRequiringReconnect() {
        jmxService = new JmxService("localhost", 40123)
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): "myval")

        app.start([new SshMachineLocation(address:"localhost")])
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals entity.getAttribute(stringAttribute), "myval"
        }
        
        // Shutdown the MBeanServer - simulates network failure so can't connect
        jmxService.shutdown()
        
        // TODO Want a better way of determining that the entity is down; ideally should have 
		// sensor for entity-down that's wired up to a JMX attribute?
        Thread.sleep(5000)

        // Restart MBeanServer, and set attribute to different value; expect it to be polled again
        jmxService = new JmxService("localhost", 40123)
        GeneralisedDynamicMBean mbean2 = jmxService.registerMBean(objectName, (attributeName): "myval2")
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals entity.getAttribute(stringAttribute), "myval2"
        }
    }
}
