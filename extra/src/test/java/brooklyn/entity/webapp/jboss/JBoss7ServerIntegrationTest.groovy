package brooklyn.entity.webapp.jboss

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

class JBoss7ServerIntegrationTest {

    static { TimeExtras.init() }
    
    private static final int DEFAULT_HTTP_PORT = 43210
    
    private Application app
    private Location testLocation

    @BeforeMethod(groups="Integration")
    public void failIfHttpPortInUse() {
        if (isPortInUse(DEFAULT_HTTP_PORT, 5000L)) {
            httpPortLeftOpen = true;
            fail "someone is already listening on port $DEFAULT_HTTP_PORT; " +
                 "tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
        }
    }
    
    @BeforeMethod(groups="Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation(name:'london', count:2)
    }
    
    @Test(groups="Integration")
    public void canStartupAndShutdown() {
        JBoss7Server jb = new JBoss7Server(owner:app, httpPort: DEFAULT_HTTP_PORT);
        jb.start([ testLocation ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue jb.getAttribute(JavaWebApp.SERVICE_UP)
        }, {
            jb.stop()
        })
    }
    
    @Test(groups="Integration")
    public void publishesRequestAndErrorCountMetrics() {
        Application app = new TestApplication();
        JBoss7Server jb = new JBoss7Server(owner:app, httpPort:DEFAULT_HTTP_PORT);
        jb.start([ testLocation ])

        String url = jb.getAttribute(JBoss7Server.ROOT_URL) + "does_not_exist"
        println url
        10.times { connectToURL(url) }

        executeUntilSucceedsWithShutdown(jb, {
            def requestCount = jb.getAttribute(JBoss7Server.REQUEST_COUNT)
            def errorCount = jb.getAttribute(JBoss7Server.ERROR_COUNT)
            
            println "$requestCount/$errorCount"
            if (errorCount == null) {
                return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")
            } else {
                assertTrue errorCount > 0
                assertEquals requestCount, errorCount
            }
            true
        }, useGroovyTruth:true, timeout:5*SECONDS)
    }
    
}
