package brooklyn.entity.webapp.jboss;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.junit.Assert.*

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation

class JBossNodeIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.jboss.JBossNodeIntegrationTest)

    // Increment default ports to avoid tests running on 8080
    final static int PORT_INCREMENT = 300
    final static int DEFAULT_HTTP_PORT = 8080 + PORT_INCREMENT

    private Application app
    private Location testLocation

    static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }

    @Before
    public void setup() {
        app = new TestApplication();
        testLocation = new SshMachineLocation(name:'london', host:'localhost')
    }

    @Before
    public void fail_if_http_port_in_use() {
        if (isPortInUse(DEFAULT_HTTP_PORT)) {
            fail "someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
        }
    }

    @After
    public void waitForShutdown() {
        logger.info "Sleeping for shutdown"
        Thread.sleep 4000
    }

    @Test
    public void canStartupAndShutdown() {
        JBossNode jb = new JBossNode(owner:app, portIncrement: PORT_INCREMENT);
        jb.start([testLocation])
        assert (new JBoss6SshSetup(jb, testLocation)).isRunning(testLocation)
        jb.shutdown()
        // Potential for JBoss to be in process of shutting down here..
        Thread.sleep 4000
        assert ! (new JBoss6SshSetup(jb, testLocation)).isRunning(testLocation)
    }

    @Test
    public void canAlterPortIncrement() {
        int pI = 1020
        JBossNode jb = new JBossNode(owner:app, portIncrement: pI);
        jb.start([testLocation])
        executeUntilSucceedsWithShutdown(jb, {
            def port = jb.getAttribute(JBossNode.HTTP_PORT)
            def url = "http://localhost:$port"
            assertTrue urlRespondsWithStatusCode200(url)
            true
        }, abortOnError: false)
    }
    
    @Test
    public void canStartMultipleJBossNodes() {

        def aInc = 400
        JBossNode nodeA = new JBossNode(owner:app, portIncrement: aInc);
        nodeA.start([testLocation])
        
        def bInc = 450
        JBossNode nodeB = new JBossNode(owner:app, portIncrement: bInc);
        nodeB.start([testLocation])
        
        executeUntilSucceedsWithFinallyBlock({
            def aHttp = nodeA.getAttribute(JBossNode.HTTP_PORT)
            def bHttp = nodeB.getAttribute(JBossNode.HTTP_PORT)
            assertTrue urlRespondsWithStatusCode200("http://localhost:$aHttp")
            assertTrue urlRespondsWithStatusCode200("http://localhost:$bHttp")
            true
        }, {
            nodeA.shutdown()
            nodeB.shutdown()
        }, abortOnError: false)
        
    }
    
    @Test
    public void publishesErrorCountMetric() {
        JBossNode jb = new JBossNode(owner:app, portIncrement: PORT_INCREMENT);
        jb.start([testLocation])
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
                logger.info "connection to {} gives {}", url, status
            }
            Thread.sleep(1000L)
            errorCount = jb.getAttribute(JBossNode.ERROR_COUNT)
            println "$errorCount errors in total"

            assertTrue errorCount > 0
            assertEquals 0, errorCount % n
            true
        }, abortOnError: false, timeout:10*SECONDS, useGroovyTruth:true)

    }

}
