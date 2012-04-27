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
        // Create an entity and configure it with the above JMX service
        app = new AbstractApplication() {}
        
        entity = new VanillaJavaApp(owner:app,
                jmxPort:40123,
                rmiPort:0,
                jmxContext:null,
                mxbeanStatsEnabled:false) {

            @Override protected void connectSensors() {
                super.connectSensors();
                
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

    @Test(enabled=false) // FIXME
    public void testSimpleConnection() {
        jmxService = new JmxService("localhost", 40123)
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): "myval")

        app.start([new SshMachineLocation(address:"localhost")])
        
        // Starts with value defined when registering...
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals entity.getAttribute(stringAttribute), "myval"
        }
    }

    @Test(enabled=false) // FIXME
    public void testEntityWithDelayedJmxStartupWillKeepRetrying() {
        Thread t = new Thread({
            Thread.sleep(5000)
            jmxService = new JmxService("localhost", 40123)
            GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): "myval")
        })
        try {
            t.start()
            app.start([new SshMachineLocation(address:"localhost")])
            
            // Starts with value defined when registering...
            TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
                assertEquals entity.getAttribute(stringAttribute), "myval"
            }
            
        } finally {
            t.interrupt()
        }
    }
    
    @Test
    public void testJmxConnectionGoesDownRequiringReconnect() {
        jmxService = new JmxService("localhost", 40123)
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): "myval")

        app.start([new SshMachineLocation(address:"localhost")])
        
        // Starts with value defined when registering...
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals entity.getAttribute(stringAttribute), "myval"
        }
        
        // Shutdown the MBeanServer - simulates network failure so can't connect
        jmxService.shutdown()
        
        // FIXME How to tell that it's detected failure?
        Thread.sleep(5000)
//        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
//            assertEquals entity.getAttribute(stringAttribute), "myval"
//        }

        // Restart MBeanServer, and set attribute to different value; expect it to be polled again
        jmxService = new JmxService("localhost", 40123)
        GeneralisedDynamicMBean mbean2 = jmxService.registerMBean(objectName, (attributeName): "myval2")
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals entity.getAttribute(stringAttribute), "myval2"
        }
    }
}
