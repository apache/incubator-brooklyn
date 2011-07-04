package brooklyn.entity.webapp.jboss;

import static brooklyn.test.TestUtils.*

import static org.testng.Assert.*
import groovy.time.TimeDuration

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.location.Location

import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation

/**
 * Test the operation of the {@link JBossNode} class.
 * 
 * TODO clarify test purpose
 */
public class JBossNodeIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(brooklyn.entity.webapp.jboss.JBossNodeIntegrationTest)

    // Increment default ports to avoid tests running on 8080
    final static int PORT_INCREMENT = 300
    final static int BASE_HTTP_PORT = 8080
    final static int DEFAULT_HTTP_PORT = BASE_HTTP_PORT + PORT_INCREMENT

    private Application app
    private Location testLocation

    static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation(name:'london')
    }

    
    @AfterMethod(groups = [ "Integration" ])
    public void waitForShutdown() {
        log.info "Sleeping for shutdown"
        Thread.sleep 4000
    }
    
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        JBossNode jb = new JBossNode(owner:app, portIncrement: PORT_INCREMENT);
        jb.start([ testLocation ])
        assertTrue((new JBoss6SshSetup(jb, jb.machine)).isRunning())
        jb.shutdown()
        // Potential for JBoss to be in process of shutting down here..
        Thread.sleep 4000
        assertFalse((new JBoss6SshSetup(jb, jb.machine)).isRunning())
    }

    @Test(groups = "Integration")
    public void canAlterPortIncrement() {
        int pI = 1020
        int httpPort = BASE_HTTP_PORT + pI
        JBossNode jb = new JBossNode(owner:app, portIncrement: pI);
        // Assert httpPort is contactable.
        log.info "Starting JBoss with HTTP port $httpPort"
        jb.start([ testLocation ])
        executeUntilSucceedsWithShutdown(jb, {
            def port = jb.getAttribute(JBossNode.HTTP_PORT)
            def url = "http://localhost:$port"
            assertTrue urlRespondsWithStatusCode200(url)
            true
        }, abortOnError:false)
    }
    
    @Test(enabled = false, groups = [ "Integration" ])
    public void canStartMultipleJBossNodes() {
        def aInc = 400
        JBossNode nodeA = new JBossNode(owner:app, portIncrement:aInc);
        nodeA.start([ testLocation ])
        
        def bInc = 450
        JBossNode nodeB = new JBossNode(owner:app, portIncrement:bInc);
        nodeB.start([ testLocation ])
        
        executeUntilSucceedsWithFinallyBlock({
            def aHttp = nodeA.getAttribute(JBossNode.HTTP_PORT)
            def bHttp = nodeB.getAttribute(JBossNode.HTTP_PORT)
            assertTrue urlRespondsWithStatusCode200("http://localhost:$aHttp")
            assertTrue urlRespondsWithStatusCode200("http://localhost:$bHttp")
            true
        }, {
            nodeA.shutdown()
            nodeB.shutdown()
        }, abortOnError:false)
        
    }
    
    @Test(groups = [ "Integration" ])
    public void publishesErrorCountMetric() {
        JBossNode jb = new JBossNode(owner:app, portIncrement:PORT_INCREMENT);
        jb.start([ testLocation ])
        executeUntilSucceedsWithShutdown(jb, {
            def errorCount = jb.getAttribute(JBossNode.ERROR_COUNT)
            if (errorCount == null) return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")

            // Connect to non-existent URL n times
            def n = 5
            def port = jb.getAttribute(JBossNode.HTTP_PORT)
            def url = "http://localhost:${port}/does_not_exist"
            n.times {
                def connection = connectToURL(url)
                int status = ((HttpURLConnection) connection).getResponseCode()
                log.info "connection to {} gives {}", url, status
            }
            Thread.sleep(1000L)
            errorCount = jb.getAttribute(JBossNode.ERROR_COUNT)
            println "$errorCount errors in total"

            assertTrue errorCount > 0
            assertEquals 0, errorCount % n
            true
        }, abortOnError:false, timeout:new TimeDuration(0, 0, 0, 10, 0), useGroovyTruth:true)
    }
}
