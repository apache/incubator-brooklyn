package brooklyn.entity.java

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Lifecycle
import brooklyn.entity.basic.UsesJava;
import brooklyn.location.basic.SshMachineLocation

class VanillaJavaAppTest {

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
        String cp = "/Users/aled/eclipse-workspaces/cloudsoft/brooklyn/extra/target/test-classes"
        String main = "brooklyn.entity.java.ExampleVanillaMain"
        VanillaJavaApp javaProcess = new VanillaJavaApp(owner:app, main:main, classpath:[cp], args:[])
        app.start([loc])
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.RUNNING)
        
        javaProcess.stop()
        assertEquals(javaProcess.getAttribute(VanillaJavaApp.SERVICE_STATE), Lifecycle.STOPPED)
    }
}

