package brooklyn.entity.java

import static org.testng.Assert.*
import static brooklyn.test.TestUtils.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Lifecycle
import brooklyn.entity.basic.UsesJava
import brooklyn.entity.basic.UsesJmx
import brooklyn.location.basic.SshMachineLocation

class VanillaJavaAppTest {

    private static final long TIMEOUT_MS = 10*1000
    
    AbstractApplication app
    SshMachineLocation loc
    
    @BeforeMethod
    public void setUp() {
        app = new AbstractApplication() {}
        loc = new SshMachineLocation(address:"localhost")
    }
    
    @AfterMethod
    public void tearDown() {
        app?.stop()
    }
    
    @Test
    public void testReadsConfigFromFlags() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:"my.Main", classpath:["c1", "c2"], args:["a1", "a2"])
        assertEquals(javaProcess.getMain(), "my.Main")
        assertEquals(javaProcess.getClasspath(), ["c1","c2"])
        assertEquals(javaProcess.getConfig(VanillaJavaApp.ARGS), ["a1", "a2"])
    }
    
    @Test(groups=["WIP", "Integration"])
    public void testJavaSystemProperties() {
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:"my.Main", classpath:["c1", "c2"], args:["a1", "a2"])
        javaProcess.setConfig(UsesJava.JAVA_OPTIONS, ["fooKey":"fooValue", "barKey":"barValue"])
        // TODO: how to test: launch standalone app that outputs system properties to stdout? Probe via JMX?
    }
    
    // FIXME Hard-codes Aled's path; needs fixed!
    @Test(groups=["WIP", "Integration"])
    public void testStartsAndStops() {
        String cp = "/Users/aled/eclipse-workspaces/cloudsoft/brooklyn/software/base/target/test-classes"
        String main = "brooklyn.entity.java.ExampleVanillaMain"
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:main, classpath:[cp], args:[])
        app.start([loc])
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.RUNNING)
        
        javaProcess.stop()
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.STOPPED)
    }
    
    // FIXME Hard-codes Aled's path; needs fixed!
    @Test(groups=["WIP", "Integration"])
    public void testHasJvmMXBeanSensorVals() {
        String cp = "/Users/aled/eclipse-workspaces/cloudsoft/brooklyn/software/base/target/test-classes"
        String main = "brooklyn.entity.java.ExampleVanillaMain"
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:main, classpath:[cp], args:[])
        app.start([loc])
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            long init = javaProcess.getAttribute(VanillaJavaApp.INIT_HEAP_MEMORY)
            long used = javaProcess.getAttribute(VanillaJavaApp.USED_HEAP_MEMORY)
            long committed = javaProcess.getAttribute(VanillaJavaApp.COMMITTED_HEAP_MEMORY)
            long max = javaProcess.getAttribute(VanillaJavaApp.MAX_HEAP_MEMORY)
            
            assertNotNull(used)
            assertNotNull(init)
            assertNotNull(committed)
            assertNotNull(max)
            assertTrue(init <= max, String.format("init %d > max %d heap memory", init, max))
            assertTrue(used <= committed, String.format("used %d > committed %d heap memory", used, committed))
            assertTrue(committed <= max, String.format("committed %d > max %d heap memory", committed, max))

            long current = javaProcess.getAttribute(VanillaJavaApp.CURRENT_THREAD_COUNT)
            long peak = javaProcess.getAttribute(VanillaJavaApp.PEAK_THREAD_COUNT)
            
            assertNotNull(current)
            assertNotNull(peak)
            assertTrue(current <= peak, String.format("current %d > peak %d thread count", current, peak))

            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.NON_HEAP_MEMORY_USAGE))
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.START_TIME))
            assertNotNull(javaProcess.getAttribute(VanillaJavaApp.SYSTEM_LOAD_AVERAGE))
            //assertNotNull(javaProcess.getAttribute(VanillaJavaApp.GARBAGE_COLLECTION_TIME)) TODO: work on providing this
        }
    }
    
    // FIXME Hard-codes Aled's path; needs fixed!
    @Test(groups=["WIP", "Integration"])
    public void testStartsWithJmxPortSpecifiedInConfig() {
        String cp = "/Users/aled/eclipse-workspaces/cloudsoft/brooklyn/software/base/target/test-classes"
        String main = "brooklyn.entity.java.ExampleVanillaMain"
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:main, classpath:[cp], args:[])
        javaProcess.setConfig(UsesJmx.JMX_PORT, 54321)
        app.start([loc])
        
        assertEquals(javaProcess.getAttribute(UsesJmx.JMX_PORT), 54321)
    }
}

