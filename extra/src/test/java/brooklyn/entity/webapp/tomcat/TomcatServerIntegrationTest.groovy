package brooklyn.entity.webapp.tomcat

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.event.EntityStartException
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.test.TestUtils.BooleanWithMessage
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

/**
 * This tests the operation of the {@link TomcatServer} entity.
 * 
 * TODO clarify test purpose
 */
public class TomcatServerIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(brooklyn.entity.webapp.tomcat.TomcatServerIntegrationTest.class)
    
    /** don't use 8080 since that is commonly used by testing software */
    static int DEFAULT_HTTP_PORT = 7880

    static { TimeExtras.init() }

    static boolean httpPortLeftOpen = false;
    private int oldHttpPort = -1;

    @BeforeMethod(groups = [ "Integration" ])
    public void failIfHttpPortInUse() {
        if (isPortInUse(DEFAULT_HTTP_PORT, 5000L)) {
            httpPortLeftOpen = true;
            fail "someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
        }
    }
 
    @AfterMethod(groups = [ "Integration" ])
    public void ensureTomcatIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;

        boolean socketClosed = new Repeater("Checking Tomcat has shut down")
            .repeat({
		            if (shutdownSocket) shutdownSocket.close();
		            try { shutdownSocket = new Socket(InetAddress.localHost, Tomcat7SshSetup.DEFAULT_FIRST_SHUTDOWN_PORT); }
		            catch (SocketException e) { gotException = e; return; }
		            gotException = null
		        })
            .every(100, TimeUnit.MILLISECONDS)
            .until({ gotException })
            .limitIterationsTo(25)
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
        TomcatServer tc = [ owner:new TestApplication(), httpPort:DEFAULT_HTTP_PORT ]
        tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue tc.getAttribute(TomcatServer.SERVICE_UP)
        }, {
            tc.stop()
        })
    }
    
    @Test(groups = [ "Integration" ])
    public void publishesRequestAndErrorCountMetrics() {
        Application app = new TestApplication();
        TomcatServer tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT);
        tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        String url = tc.getAttribute(TomcatServer.ROOT_URL) + "does_not_exist"
        10.times { connectToURL(url) }
        
        executeUntilSucceedsWithShutdown(tc, {
            def requestCount = tc.getAttribute(TomcatServer.REQUEST_COUNT)
            def errorCount = tc.getAttribute(TomcatServer.ERROR_COUNT)
            logger.info "req=$requestCount, err=$errorCount"
            
            if (errorCount == null) {
                return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")
            } else {
                logger.info "$errorCount errors in total"
                assertTrue errorCount > 0
                assertEquals requestCount, errorCount
            }
            true
        }, useGroovyTruth:true, timeout:60*SECONDS)
    }
    
    @Test(groups = [ "Integration" ])
    public void publishesRequestsPerSecondMetric() {
        Application app = new TestApplication();
        TomcatServer tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT);
        tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        try {
            // reqs/sec initially zero
            executeUntilSucceeds( {
                    Double activityValue = tc.getAttribute(TomcatServer.AVG_REQUESTS_PER_SECOND)
                    if (activityValue == null) 
                        return new BooleanWithMessage(false, "activity not set yet ($activityValue)")
    
                    assertTrue activityValue in Double
                    assertEquals activityValue, 0.0d
                } )
            
            // apply workload on 1 per sec; reqs/sec should update 
            executeUntilSucceeds( {
    		        String url = tc.getAttribute(TomcatServer.ROOT_URL) + "foo"
    
                    long startTime = System.currentTimeMillis()
                    long elapsedTime = 0
                    
                    // need to maintain n requests per second for the duration of the window size
                    while (elapsedTime < TomcatServer.AVG_REQUESTS_PER_SECOND_PERIOD) {
                        int n = 10
                        n.times { connectToURL url }
                        Thread.sleep 1000
                        def requestCount = tc.getAttribute(TomcatServer.REQUEST_COUNT)
                        assertEquals requestCount % n, 0
                        elapsedTime = System.currentTimeMillis() - startTime
                    }
    
                    Double activityValue = tc.getAttribute(TomcatServer.AVG_REQUESTS_PER_SECOND)
                    assertEquals activityValue, 10.0d, 0.5d
    
                    true
                }, timeout:10*SECONDS, useGroovyTruth:true)
            
            // After suitable delay, expect to again get zero msgs/sec
            Thread.sleep(TomcatServer.AVG_REQUESTS_PER_SECOND_PERIOD)
            
            executeUntilSucceeds( {
                Double activityValue = tc.getAttribute(TomcatServer.AVG_REQUESTS_PER_SECOND)
                assertTrue activityValue in Double
                assertEquals activityValue, 0.0d
            } )

        } finally {
            tc.stop()
        }
    }
    
    /**
     * Tests that we get consecutive events with zero workrate, and with suitably small timestamps between them.
     */
    @Test(groups = [ "Integration" ])
    public void publishesZeroRequestsPerSecondMetricRepeatedly() {
        final int MAX_INTERVAL_BETWEEN_EVENTS = 1000 // should be every 500ms
        final int NUM_CONSECUTIVE_EVENTS = 3
        
        Application app = new TestApplication();
        TomcatServer tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT);
        tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        SubscriptionHandle subscriptionHandle
        SubscriptionContext subContext = app.getManagementContext().getSubscriptionContext(tc)
        try {
            final List<SensorEvent> events = new CopyOnWriteArrayList<SensorEvent>()
            subscriptionHandle = subContext.subscribe(tc, TomcatServer.AVG_REQUESTS_PER_SECOND, { 
                    println("publishesRequestsPerSecondMetricRepeatedly.onEvent: $it"); events.add(it) } as SensorEventListener)
            
            executeUntilSucceeds( {
                    assertTrue(events.size() > NUM_CONSECUTIVE_EVENTS)
                    long eventTime = 0
                    
                    for (SensorEvent event in events.subList(events.size()-NUM_CONSECUTIVE_EVENTS, events.size())) {
                        assertEquals(tc, event.getSource())
                        assertEquals(TomcatServer.AVG_REQUESTS_PER_SECOND, event.getSensor())
                        assertEquals(0.0d, event.getValue())
                        if (eventTime > 0) assertTrue(event.getTimestamp()-eventTime < MAX_INTERVAL_BETWEEN_EVENTS)
                        eventTime = event.getTimestamp()
                    }
                })

        } finally {
            if (subscriptionHandle) subContext.unsubscribe(subscriptionHandle)
            tc.stop()
        }
    }
    
    @Test(groups = [ "Integration" ])
    public void deployWebAppAppearsAtUrl() {
        Application app = new TestApplication();
        TomcatServer tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT);

        URL resource = getClass().getClassLoader().getResource("hello-world.war")
        assertNotNull resource
        tc.setConfig(TomcatServer.WAR, resource.getPath())

        tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(tc, {
            // TODO get this URL from a WAR file entity
	        String url = tc.getAttribute(TomcatServer.ROOT_URL) + "hello-world"
            assertTrue urlRespondsWithStatusCode200(url)
            true
        }, abortOnError:false)
    }
    
    @Test(groups=[ "Integration" ])
    public void canDeploySpringTravel() {
        Application app = new TestApplication();
        TomcatServer tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT);

        URL resource = getClass().getClassLoader().getResource("swf-booking-mvc.war")
        assertNotNull resource
        tc.setConfig(TomcatServer.WAR, resource.getPath())

        tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(tc, {
            // TODO get this URL from a WAR file entity
            String url = tc.getAttribute(TomcatServer.ROOT_URL) + "swf-booking-mvc/spring/intro"
            assertTrue urlRespondsWithStatusCode200(url)
            true
        }, abortOnError:false, timeout: 10*SECONDS)
    }

    @Test(groups = [ "Integration" ])
    public void detectFailureIfTomcatCantBindToPort() {
        ServerSocket listener = new ServerSocket(DEFAULT_HTTP_PORT);
        Thread t = new Thread({ try { for(;;) { Socket socket = listener.accept(); socket.close(); } } catch(Exception e) {} })
        t.start()
        try {
            Application app = new TestApplication()
            TomcatServer tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT)
            Exception caught = null
            try {
                tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
                fail("Should have thrown start-exception")
            } catch (EntityStartException e) {
                // success
                logger.debug "The exception that was thrown was:", caught
            } finally {
                tc.stop()
            }
            assertFalse tc.getAttribute(TomcatServer.SERVICE_UP)
        } finally {
            listener.close();
            t.join();
        }
    }
    
    @Test(groups = [ "Integration" ])
    public void createsPropertiesFilesWithEnvironmentVariables() {
        Application app = new TestApplication();
        TomcatServer tc = new TomcatServer(owner:app, httpPort:DEFAULT_HTTP_PORT);
        tc.setConfig(TomcatServer.PROPERTY_FILES.subKey("MYVAR1"),[akey:"aval",bkey:"bval"])
        tc.setConfig(TomcatServer.PROPERTY_FILES.subKey("MYVAR2"),[ckey:"cval",dkey:"dval"])
        tc.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        try {
            SshMachineLocation machine = tc.locations.first()
            String var1file = getEnvironmentVariable(tc, "MYVAR1")
            String var2file = getEnvironmentVariable(tc, "MYVAR2")
            File tmpFile1 = File.createTempFile("one", "tmp", new File("/tmp"))
            File tmpFile2 = File.createTempFile("two", "tmp", new File("/tmp"))
            tmpFile1.deleteOnExit()
            tmpFile2.deleteOnExit()
            machine.copyFrom var1file, tmpFile1.absolutePath
            machine.copyFrom var2file, tmpFile2.absolutePath
            
            Properties var1props = new Properties()
            var1props.load(new FileInputStream(tmpFile1))
            
            Properties var2props = new Properties()
            var2props.load(new FileInputStream(tmpFile2))
            
            assertPropertiesEquals(var1props, [akey:"aval",bkey:"bval"])
            assertPropertiesEquals(var2props, [ckey:"cval",dkey:"dval"])
        } finally {
            tc.stop()
        }
    }
    
    private String getEnvironmentVariable(TomcatServer tomcat, String var) {
        ByteArrayOutputStream outstream = new ByteArrayOutputStream()
        int result = tomcat.setup.machine.run(out:outstream, ["env"], tomcat.setup.runEnvironment)
        String outstr = new String(outstream.toByteArray())
        String[] outLines = outstr.split("\n")
        for (String line in outLines) {
            String[] envVariable = line.trim().split("=")
            if (envVariable && envVariable[0] == var) return envVariable[1]
        }
        throw new IllegalStateException("environment variable '$var' not found in $outstr")
    }
    
    private void assertPropertiesEquals(Properties props, Map expected) {
        assertEquals(props.stringPropertyNames(), expected.keySet())
        for (String key in props.stringPropertyNames()) {
            assertEquals(props.getProperty(key), expected.get(key))
        }
    }
}
