package brooklyn.entity.webapp.jboss;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link JBossNode} class.
 * 
 * TODO clarify test purpose
 */
public class JBossNodeIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(brooklyn.entity.webapp.jboss.JBossNodeIntegrationTest)

    // Increment default ports to avoid tests running on 8080
    final static int PORT_INCREMENT = 400

    static { TimeExtras.init() }
    
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
        testLocation = new LocalhostMachineProvisioningLocation(name:'london', count:2)
    }
    
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        JBossNode jb = new JBossNode(owner:app, portIncrement: PORT_INCREMENT);
        jb.start([ testLocation ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue jb.getAttribute(JavaWebApp.SERVICE_UP)
        }, {
            jb.stop()
        })
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
            nodeA.stop()
            nodeB.stop()
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
        }, abortOnError:false, timeout:10*SECONDS, useGroovyTruth:true)
    }
}
