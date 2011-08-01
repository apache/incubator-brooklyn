package brooklyn.entity.webapp.jboss;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link JBoss6Server} class.
 * 
 * TODO clarify test purpose
 */
public class JBossServerIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(brooklyn.entity.webapp.jboss.JBossServerIntegrationTest)

    // Increment default ports to avoid tests running on 8080
    final static int PORT_INCREMENT = 400

    static { TimeExtras.init() }
    
    private Application app
    private Location testLocation

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation(name:'london', count:2)
    }
    
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        JBoss6Server jb = new JBoss6Server(owner:app, portIncrement: PORT_INCREMENT);
        jb.start([ testLocation ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue jb.getAttribute(JavaWebApp.SERVICE_UP)
        }, {
            jb.stop()
        })
    }

    @Test(enabled = false, groups = [ "Integration" ])
    public void canStartMultipleJBossServers() {
        def aInc = 400
        JBoss6Server nodeA = new JBoss6Server(owner:app, portIncrement:aInc);
        nodeA.start([ testLocation ])
        
        def bInc = 450
        JBoss6Server nodeB = new JBoss6Server(owner:app, portIncrement:bInc);
        nodeB.start([ testLocation ])
        
        executeUntilSucceedsWithFinallyBlock({
            String aUrl = nodeA.getAttribute(JBoss6Server.ROOT_URL)
            String bUrl = nodeB.getAttribute(JBoss6Server.ROOT_URL)
            assertTrue urlRespondsWithStatusCode200(aUrl)
            assertTrue urlRespondsWithStatusCode200(bUrl)
            true
        }, {
            nodeA.stop()
            nodeB.stop()
        }, abortOnError:false)
    }
    
    @Test(groups = [ "Integration" ])
    public void publishesErrorCountMetric() {
        JBoss6Server jb = new JBoss6Server(owner:app, portIncrement:PORT_INCREMENT);
        jb.start([ testLocation ])
        executeUntilSucceedsWithShutdown(jb, {
            def errorCount = jb.getAttribute(JBoss6Server.ERROR_COUNT)
            if (errorCount == null) return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")

            // Connect to non-existent URL n times
            int n = 5
            String url = jb.getAttribute(JBoss6Server.ROOT_URL) + "does_not_exist"
            log.info "connect to {}", url
            n.times {
                def connection = connectToURL(url)
                int status = ((HttpURLConnection) connection).getResponseCode()
                log.info "connection to {} gives {}", url, status
            }
            Thread.sleep(1000L)
            errorCount = jb.getAttribute(JBoss6Server.ERROR_COUNT)
            println "$errorCount errors in total"

            assertTrue errorCount > 0
            assertEquals 0, errorCount % n
            true
        }, abortOnError:false, timeout:10*SECONDS, useGroovyTruth:true)
    }
}
