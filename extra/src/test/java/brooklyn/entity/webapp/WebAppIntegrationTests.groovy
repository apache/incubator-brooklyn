package brooklyn.entity.webapp

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.entity.basic.JavaApp
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.tomcat.Tomcat7SshSetup
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.EntityStartException
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

/**
 * Tests that implementations of JavaWebApp can start up and shutdown, 
 * post request and error count metrics and deploy wars, etc.
 * 
 * Currently tests TomcatServer, JBoss6Server and JBoss7Server.
 */
class WebAppIntegrationTests {

    private static final Logger logger = LoggerFactory.getLogger(WebAppIntegrationTests.class)
    
    static { TimeExtras.init() }
    
    // Don't use 8080 since that is commonly used by testing software
    static int DEFAULT_HTTP_PORT = 7880
    
    // Port increment for JBoss 6.
    final static int PORT_INCREMENT = 400
    
    @BeforeMethod(groups=["Integration"])
    public void failIfHttpPortInUse() {
        if (isPortInUse(DEFAULT_HTTP_PORT, 5000L)) {
            httpPortLeftOpen = true;
            fail "someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
        }
    }
    
    @AfterMethod(groups=["Integration"])
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
    
    /**
     * Provides instances of TomcatServer and JBoss{6,7}Server to the tests below.
     */
    @DataProvider(name="basicEntities")
    public Object[][] basicEntities() {
        TomcatServer tomcat = [owner: new TestApplication(), httpPort: DEFAULT_HTTP_PORT]
        JBoss6Server jboss6 = [owner: new TestApplication(), portIncrement: PORT_INCREMENT]
        JBoss7Server jboss7 = [owner: new TestApplication(), httpPort: DEFAULT_HTTP_PORT]
        return [[tomcat], [jboss6], [jboss7]]
    }

    /**
     * Checks an entity can start, set SERVICE_UP to true and shutdown again.
     */
    @Test(groups=["Integration"], dataProvider="basicEntities")
    public void canStartupAndShutdown(JavaWebApp entity) {
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue entity.getAttribute(JavaApp.SERVICE_UP)
        }, {
            entity.stop()
            assertFalse entity.getAttribute(JavaApp.SERVICE_UP)
        })
    }
    
    /**
     * Checks that an entity correctly sets request and error count metrics by
     * connecting to a non-existent URL several times.
     */
    @Test(groups=["Integration"], dataProvider="basicEntities")
    public void publishesRequestAndErrorCountMetrics(JavaWebApp entity) {
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        String url = entity.getAttribute(JavaWebApp.ROOT_URL) + "does_not_exist"
        
        executeUntilSucceeds(timeout: 10*SECONDS, {
            assertTrue entity.getAttribute(JavaApp.SERVICE_UP)
        })
        
        10.times {
            def connection = connectToURL(url)
            int status = ((HttpURLConnection) connection).getResponseCode()
            log.info "connection to {} gives {}", url, status
        }
        
        executeUntilSucceedsWithShutdown(entity, {
            def requestCount = entity.getAttribute(JavaWebApp.REQUEST_COUNT)
            def errorCount = entity.getAttribute(JavaWebApp.ERROR_COUNT)
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
    
    /**
     * Checks an entity publishes correct requests/second figures and that these figures
     * fall to zero after a period of no activity.
     */
    @Test(groups=["Integration"], dataProvider="basicEntities")
    public void publishesRequestsPerSecondMetric(JavaWebApp entity) {
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        try {
            // reqs/sec initially zero
            executeUntilSucceeds({
                Double activityValue = entity.getAttribute(JavaWebApp.AVG_REQUESTS_PER_SECOND)
                if (activityValue == null)
                    return new BooleanWithMessage(false, "activity not set yet ($activityValue)")

                assertTrue activityValue in Double
                assertEquals activityValue, 0.0d
            }, useGroovyTruth: true)
            
            // apply workload on 1 per sec; reqs/sec should update
            executeUntilSucceeds({
                String url = entity.getAttribute(JavaWebApp.ROOT_URL) + "foo"

                long startTime = System.currentTimeMillis()
                long elapsedTime = 0
                
                // need to maintain n requests per second for the duration of the window size
                while (elapsedTime < JavaWebApp.AVG_REQUESTS_PER_SECOND_PERIOD) {
                    int n = 10
                    n.times { connectToURL url }
                    Thread.sleep 1000
                    def requestCount = entity.getAttribute(JavaWebApp.REQUEST_COUNT)
                    assertEquals requestCount % n, 0
                    elapsedTime = System.currentTimeMillis() - startTime
                }

                Double activityValue = entity.getAttribute(JavaWebApp.AVG_REQUESTS_PER_SECOND)
                assertEquals activityValue, 10.0d, 0.5d

                true
            }, timeout:10*SECONDS, useGroovyTruth:true)
            
            // After suitable delay, expect to again get zero msgs/sec
            Thread.sleep(JavaWebApp.AVG_REQUESTS_PER_SECOND_PERIOD)
            
            executeUntilSucceeds({
                Double activityValue = entity.getAttribute(JavaWebApp.AVG_REQUESTS_PER_SECOND)
                assertTrue activityValue in Double
                assertEquals activityValue, 0.0d
            })

        } finally {
            entity.stop()
        }
    }
    
    /**
     * Tests that we get consecutive events with zero workrate, and with suitably small timestamps between them.
     */
    @Test(groups=["Integration"], dataProvider="basicEntities")
    public void publishesZeroRequestsPerSecondMetricRepeatedly(JavaWebApp entity) {
        final int MAX_INTERVAL_BETWEEN_EVENTS = 1000 // should be every 500ms
        final int NUM_CONSECUTIVE_EVENTS = 3
        
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        SubscriptionHandle subscriptionHandle
        SubscriptionContext subContext = entity.owner.getManagementContext().getSubscriptionContext(entity)
        try {
            final List<SensorEvent> events = new CopyOnWriteArrayList<SensorEvent>()
            subscriptionHandle = subContext.subscribe(entity, JavaWebApp.AVG_REQUESTS_PER_SECOND, {
                    println("publishesRequestsPerSecondMetricRepeatedly.onEvent: $it"); events.add(it) } as SensorEventListener)
            
            executeUntilSucceeds({
                assertTrue(events.size() > NUM_CONSECUTIVE_EVENTS)
                long eventTime = 0
                
                for (SensorEvent event in events.subList(events.size()-NUM_CONSECUTIVE_EVENTS, events.size())) {
                    assertEquals(entity, event.getSource())
                    assertEquals(JavaWebApp.AVG_REQUESTS_PER_SECOND, event.getSensor())
                    assertEquals(0.0d, event.getValue())
                    if (eventTime > 0) assertTrue(event.getTimestamp()-eventTime < MAX_INTERVAL_BETWEEN_EVENTS)
                    eventTime = event.getTimestamp()
                }
            })

        } finally {
            if (subscriptionHandle) subContext.unsubscribe(subscriptionHandle)
            entity.stop()
        }
    }

    /**
     * Twins the entities given by basicEntities() with links to WAR files
     * they should be able to deploy.  Correct deployment can be checked by
     * pinging the given URL.
     */
    @DataProvider(name="entitiesWithWARAndURL")
    public Object[][] entitiesWithWAR() {
        // Everything can deploy hello world
        basicEntities().collect {
            [it[0], "hello-world.war", "hello-world"]
        } + [ // Tomcat can deploy Spring travel
            [new TomcatServer(owner: new TestApplication(), httpPort: DEFAULT_HTTP_PORT), 
                "swf-booking-mvc.war", "swf-booking-mvc/spring/intro"]
        ] /*+ [ // JBoss can deploy Seam travel
            [new JBoss6Server(owner: new TestApplication(), httpPort: DEFAULT_HTTP_PORT), null, null],
            [new JBoss7Server(owner: new TestApplication(), httpPort: DEFAULT_HTTP_PORT), null, null],
        ]*/
    }

    /**
     * Tests given entity can deploy the given war.  Checks given httpURL to confirm success.
     */
    @Test(groups=["Integration"], dataProvider="entitiesWithWARAndURL")
    public void warDeployments(JavaWebApp entity, String war, String httpURL) {
        URL resource = getClass().getClassLoader().getResource(war)
        assertNotNull resource
        
        entity.setConfig(JavaWebApp.WAR, resource.path)
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(entity, {
            // TODO get this URL from a WAR file entity
            assertTrue urlRespondsWithStatusCode200(entity.getAttribute(JavaWebApp.ROOT_URL) + httpURL)
            true
        }, abortOnError:false, timeout: 10*SECONDS)
    }

}
