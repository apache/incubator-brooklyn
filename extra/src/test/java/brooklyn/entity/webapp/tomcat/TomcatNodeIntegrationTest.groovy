package brooklyn.entity.webapp.tomcat

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.Map

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.EntityStartException
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.LocalhostSshMachineProvisioner

/**
 * This tests the operation of the {@link TomcatNode} entity.
 * 
 * TODO clarify test purpose
 */
public class TomcatNodeIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.tomcat.TomcatNodeIntegrationTest.class)
    
    /** don't use 8080 since that is commonly used by testing software */
    static int DEFAULT_HTTP_PORT = 7880

    protected static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }

    static boolean httpPortLeftOpen = false;
    private int oldHttpPort=-1;

    @BeforeMethod(groups = [ "Integration" ])
    public void fail_if_http_port_in_use() {
        if (isPortInUse(DEFAULT_HTTP_PORT)) {
            httpPortLeftOpen = true;
            fail "someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
        }
    }
 
    @AfterMethod(groups = [ "Integration" ])
    //can't fail because that swallows the original exception, grrr!
    public void moan_if_http_port_in_use() {
        if (!httpPortLeftOpen && isPortInUse(DEFAULT_HTTP_PORT, 1000))
            logger.warn "port $DEFAULT_HTTP_PORT still running after test"
    }

    @Test
    public void tracksNodeState() {
        TomcatNode tc = [ owner: new TestApplication(), httpPort: DEFAULT_HTTP_PORT ]
        tc.start([ new SshMachineLocation(name:'london', provisioner:new LocalhostSshMachineProvisioner()) ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue tc.getAttribute(TomcatNode.NODE_UP)
        }, {
            tc.shutdown()
        })
    }
    
    @Test(groups = [ "Integration" ])
    public void publishesRequestAndErrorCountMetrics() {
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app, httpPort: DEFAULT_HTTP_PORT);
        tc.start([ new SshMachineLocation(name:'london', provisioner:new LocalhostSshMachineProvisioner()) ])
        executeUntilSucceedsWithShutdown(tc, {
            def port = tc.getAttribute(TomcatNode.HTTP_PORT)
            def errorCount = tc.getAttribute(TomcatNode.ERROR_COUNT)
 
            10.times { connectToURL("http://localhost:${port}/does_not_exist") }
            def requestCount = tc.getAttribute(TomcatNode.REQUEST_COUNT)
            errorCount = tc.getAttribute(TomcatNode.ERROR_COUNT)
            println "$requestCount/$errorCount"
            
            if (errorCount == null) {
                return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")
            } else { 
                logger.info "\n$errorCount errors in total"
                assertTrue errorCount > 0
                assertEquals requestCount, errorCount
            }
            true
        }, useGroovyTruth: true, timeout: 10*SECONDS)
    }
    
    @Test(groups = [ "Integration" ])
    public void publishesRequestsPerSecondMetric() {
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app, httpPort: DEFAULT_HTTP_PORT);
        tc.start([ new SshMachineLocation(name:'london', provisioner:new LocalhostSshMachineProvisioner()) ])
        executeUntilSucceedsWithShutdown(tc, {
                def activityValue = tc.getAttribute(TomcatNode.REQUESTS_PER_SECOND)
                if (activityValue == null || activityValue == -1) return new BooleanWithMessage(false, "activity not set yet ($activityValue)")

                assertEquals Integer, activityValue.class
                assertEquals 0, activityValue
                
                def port = tc.getAttribute(TomcatNode.HTTP_PORT)
                def connection = connectToURL "http://localhost:${port}/foo"
                assertEquals "Apache-Coyote/1.1", connection.getHeaderField("Server")

                Thread.sleep 1000
                activityValue = tc.getAttribute(TomcatNode.REQUESTS_PER_SECOND)
                assertEquals 1, activityValue
                true
            }, timeout:10*SECONDS, useGroovyTruth:true)
    }
    
    @Test(groups = [ "Integration" ])
    public void deployWebAppAppearsAtUrl() {
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app, httpPort: DEFAULT_HTTP_PORT);

        URL resource = this.getClass().getClassLoader().getResource("hello-world.war")
        assertNotNull resource
        tc.war = resource.getPath()

        tc.start([ new SshMachineLocation(name:'london', provisioner:new LocalhostSshMachineProvisioner()) ])
        executeUntilSucceedsWithShutdown(tc, {
            def port = tc.getAttribute(TomcatNode.HTTP_PORT)
            def url  = "http://localhost:${port}/hello-world"
            assertTrue urlRespondsWithStatusCode200(url)
            true
        }, abortOnError: false)
    }

    @Test(groups = [ "Integration" ])
    public void detectFailureIfTomcatCantBindToPort() {
        ServerSocket listener = new ServerSocket(DEFAULT_HTTP_PORT);
        Thread t = new Thread({ try { for(;;) { Socket socket = listener.accept(); socket.close(); } } catch(Exception e) {} })
        t.start()
        try {
            Application app = new TestApplication()
            TomcatNode tc = new TomcatNode(owner:app, httpPort: DEFAULT_HTTP_PORT)
            Exception caught = null
            try {
                tc.start([ new SshMachineLocation(name:'london', provisioner:new LocalhostSshMachineProvisioner()) ])
            } catch (EntityStartException e) {
                caught = e
            } finally {
                tc.shutdown()
            }
            assertNotNull caught
            assertFalse tc.getAttribute(TomcatNode.NODE_UP)
            logger.debug "The exception that was thrown was:", caught
        } finally {
            listener.close();
            t.join();
        }
    }
}
