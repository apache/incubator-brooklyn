package brooklyn.entity.webapp.tomcat

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.AbstractApplication
import brooklyn.event.EntityStartException
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras
import brooklyn.test.TestUtils.BooleanWithMessage

/**
 * This tests the operation of the {@link TomcatNode} entity.
 * 
 * TODO clarify test purpose
 */
public class TomcatNodeIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.tomcat.TomcatNodeIntegrationTest.class)
    
    /** don't use 8080 since that is commonly used by testing software */
    static int DEFAULT_HTTP_PORT = 7880

    static { TimeExtras.init() }

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
    public void ensureTomcatIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;

        boolean socketClosed = new Repeater("Checking Tomcat has shut down").repeat({
            if (shutdownSocket) shutdownSocket.close();
            try { shutdownSocket = new Socket(InetAddress.getByAddress((byte[])[127,0,0,1]), Tomcat7SshSetup.DEFAULT_FIRST_SHUTDOWN_PORT); }
            catch (SocketException e) { gotException = e; return; }
            gotException = null
        }).every(100, TimeUnit.MILLISECONDS).until({
            gotException
        }).limitIterationsTo(25)
        .run();

        if (socketClosed == false) {
            logger.error "Tomcat did not shut down - this is a failure of the last test run";
            logger.warn "I'm sending a message to the Tomcat shutdown port";
            OutputStreamWriter writer = new OutputStreamWriter(shutdownSocket.getOutputStream());
            writer.write("SHUTDOWN\r\n");
            writer.flush();
            writer.close();
            shutdownSocket.close();
            throw new Exception("Last test run did not shut down Tomcat")
        }
    }

    @Test(groups = [ "Integration" ])
    public void tracksNodeState() {
        TomcatNode tc = [ owner: new TestApplication(), httpPort: DEFAULT_HTTP_PORT ]
        tc.start([ new LocalhostMachineProvisioningLocation('london') ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue tc.getAttribute(TomcatNode.NODE_UP)
        }, {
            tc.shutdown()
        })
    }
    
    @Test(groups = [ "Integration" ])
    public void publishesRequestAndErrorCountMetrics() {
        TimeExtras.init();
        
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app, httpPort: DEFAULT_HTTP_PORT);
        tc.start([ new LocalhostMachineProvisioningLocation('london') ])
        int port = tc.getAttribute(TomcatNode.HTTP_PORT)
        10.times { connectToURL("http://localhost:${port}/does_not_exist") }
        
        executeUntilSucceedsWithShutdown(tc, {
            def requestCount = tc.getAttribute(TomcatNode.REQUEST_COUNT)
            def errorCount = tc.getAttribute(TomcatNode.ERROR_COUNT)
            println "req=$requestCount, err=$errorCount"
            
            if (errorCount == null) {
                return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")
            } else {
                logger.info "\n$errorCount errors in total"
                assertTrue errorCount > 0, "errorCount="+errorCount
                assertEquals requestCount, errorCount
            }
            true
        }, useGroovyTruth: true, timeout: 60*SECONDS)
    }
    
    @Test(groups = [ "Integration" ])
    public void publishesRequestsPerSecondMetric() {
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app, httpPort: DEFAULT_HTTP_PORT);
        tc.start([ new LocalhostMachineProvisioningLocation('london') ])
        executeUntilSucceedsWithShutdown(tc, {
                def activityValue = tc.getAttribute(TomcatNode.REQUESTS_PER_SECOND)
                if (activityValue == null || activityValue == -1) 
                    return new BooleanWithMessage(false, "activity not set yet ($activityValue)")

                assertEquals Double, activityValue.class
                assertEquals 0, activityValue
                
                def port = tc.getAttribute(TomcatNode.HTTP_PORT)
                def connection = connectToURL "http://localhost:${port}/foo"
                assertEquals "Apache-Coyote/1.1", connection.getHeaderField("Server")

                Thread.sleep 1000
                activityValue = tc.getAttribute(TomcatNode.REQUESTS_PER_SECOND)
                assertEquals 1d, activityValue
                true
            }, timeout:10*SECONDS, useGroovyTruth:true)
    }
    
    @Test(groups = [ "Integration" ])
    public void deployWebAppAppearsAtUrl() {
        Application app = new TestApplication();
        TomcatNode tc = new TomcatNode(owner: app, httpPort: DEFAULT_HTTP_PORT);

        URL resource = this.getClass().getClassLoader().getResource("hello-world.war")
        assertNotNull resource
        tc.setConfig(TomcatNode.WAR, resource.getPath())

        tc.start([ new LocalhostMachineProvisioningLocation('london') ])
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
                tc.start([ new LocalhostMachineProvisioningLocation('london') ])
                fail("Should have thrown start-exception")
            } catch (EntityStartException e) {
                // success
                logger.debug "The exception that was thrown was:", caught
            } finally {
                tc.shutdown()
            }
            assertFalse tc.getAttribute(TomcatNode.NODE_UP)
        } finally {
            listener.close();
            t.join();
        }
    }
}
