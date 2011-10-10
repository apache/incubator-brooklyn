package brooklyn.entity.webapp

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.tomcat.Tomcat7SshSetup
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras
import brooklyn.test.TestUtils.BooleanWithMessage

/**
 * Tests that implementations of JavaWebApp can start up and shutdown, 
 * post request and error count metrics and deploy wars, etc.
 * 
 * Currently tests {@link TomcatServer}, {@link JBoss6Server} and {@link JBoss7Server}.
 */
public class WebAppIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(WebAppIntegrationTest.class)
    
    static { TimeExtras.init() }
    
    // Don't use 8080 since that is commonly used by testing software
    public static final int DEFAULT_HTTP_PORT = 7880
    
    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400

    // The owner application entity for these tests
    Application application = new TestApplication()
    
    @BeforeMethod(groups = "Integration")
    public void failIfHttpPortInUse() {
        if (isPortInUse(DEFAULT_HTTP_PORT, 5000L)) {
            fail "someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
        }
    }

    @AfterMethod(alwaysRun=true)
    public void shutdownApp() {
        application.stop()
    }

    @AfterMethod(alwaysRun=true)
    public void ensureTomcatIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;

        boolean socketClosed = new Repeater("Checking Tomcat has shut down")
            .repeat {
                if (shutdownSocket) shutdownSocket.close();
                try { shutdownSocket = new Socket(InetAddress.localHost, Tomcat7SshSetup.DEFAULT_FIRST_SHUTDOWN_PORT); }
                catch (SocketException e) { gotException = e; return; }
                gotException = null
            }
            .every(100 * MILLISECONDS)
            .until { gotException }
            .limitIterationsTo(25)
            .run();

        if (socketClosed == false) {
            logger.error "Tomcat did not shut down - this is a failure of the last test run";
            OutputStreamWriter writer = new OutputStreamWriter(shutdownSocket.getOutputStream());
            writer.write("SHUTDOWN\r\n");
            writer.flush();
            writer.close();
            shutdownSocket.close();
            throw new Exception("Last test run did not shut down Tomcat")
        }
    }
    
    /**
     * Provides instances of {@link TomcatServer}, {@link JBoss6Server} and {@link JBoss7Server} to the tests below.
     *
     * TODO combine the data provider here with live integration test
     *
     * @see WebAppLiveIntegrationTest#basicEntities()
     */
    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        TomcatServer tomcat = [ owner:application, httpPort:DEFAULT_HTTP_PORT ]
        JBoss6Server jboss6 = [ owner:application, portIncrement:PORT_INCREMENT ]
        JBoss7Server jboss7 = [ owner:application, httpPort:DEFAULT_HTTP_PORT ]
        return [ [ tomcat ], [ jboss6 ], [ jboss7 ] ]
    }

    /**
     * Checks an entity can start, set SERVICE_UP to true and shutdown again.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void canStartAndStop(JavaWebApp entity) {
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(entity) {
            assertTrue entity.getAttribute(JavaApp.SERVICE_UP)
        }
        assertFalse entity.getAttribute(JavaApp.SERVICE_UP)
    }
    
    /**
     * Checks that an entity correctly sets request and error count metrics by
     * connecting to a non-existent URL several times.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void publishesRequestAndErrorCountMetrics(JavaWebApp entity) {
        entity.pollForHttpStatus = false
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        String url = entity.getAttribute(JavaWebApp.ROOT_URL) + "does_not_exist"
        
        executeUntilSucceeds(timeout:10*SECONDS) {
            assertTrue entity.getAttribute(JavaApp.SERVICE_UP)
        }
        
        final int n = 10
        n.times {
            def connection = connectToURL(url)
            int status = ((HttpURLConnection) connection).getResponseCode()
            log.info "connection to {} gives {}", url, status
        }
        
        executeUntilSucceedsWithShutdown(entity, useGroovyTruth:true, timeout:20*SECONDS) {
            def requestCount = entity.getAttribute(JavaWebApp.REQUEST_COUNT)
            def errorCount = entity.getAttribute(JavaWebApp.ERROR_COUNT)
            logger.info "req=$requestCount, err=$errorCount"
            
            if (errorCount == null) {
                return new BooleanWithMessage(false, "errorCount not set yet ($errorCount)")
            }

            // AS 7 seems to take a very long time to report error counts,
            // hence not using ==.  >= in case error pages include a favicon, etc.
            assertEquals errorCount, n
            assertTrue requestCount >= errorCount
            true
        }
    }
    
    /**
     * Checks an entity publishes correct requests/second figures and that these figures
     * fall to zero after a period of no activity.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void publishesRequestsPerSecondMetric(JavaWebApp entity) {
        entity.pollForHttpStatus = false
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        try {
            // reqs/sec initially zero
            executeUntilSucceeds(useGroovyTruth:true) {
                Double activityValue = entity.getAttribute(JavaWebApp.AVG_REQUESTS_PER_SECOND)
                if (activityValue == null)
                    return new BooleanWithMessage(false, "activity not set yet ($activityValue)")

                assertEquals activityValue, 0.0d
                true
            }
            
            // apply workload on 1 per sec; reqs/sec should update
            executeUntilSucceeds(timeout:10*SECONDS, useGroovyTruth:true) {
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
                assertEquals activityValue, 10.0d, 1.0d

                true
            }
            
            // After suitable delay, expect to again get zero msgs/sec
            Thread.sleep(JavaWebApp.AVG_REQUESTS_PER_SECOND_PERIOD)
            
            executeUntilSucceeds {
                Double activityValue = entity.getAttribute(JavaWebApp.AVG_REQUESTS_PER_SECOND)
                assertNotNull activityValue
                assertEquals activityValue, 0.0d
                true
            }
        } finally {
            entity.stop()
        }
    }
    
    /**
     * Tests that we get consecutive events with zero workrate, and with suitably small timestamps between them.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void publishesZeroRequestsPerSecondMetricRepeatedly(JavaWebApp entity) {
        final int MAX_INTERVAL_BETWEEN_EVENTS = 1000 // should be every 500ms
        final int NUM_CONSECUTIVE_EVENTS = 3

        entity.pollForHttpStatus = false
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        SubscriptionHandle subscriptionHandle
        SubscriptionContext subContext = entity.owner.managementContext.getSubscriptionContext(entity)

        try {
            final List<SensorEvent> events = new CopyOnWriteArrayList<SensorEvent>()
            subscriptionHandle = subContext.subscribe(entity, JavaWebApp.AVG_REQUESTS_PER_SECOND, {
                    println("publishesRequestsPerSecondMetricRepeatedly.onEvent: $it"); events.add(it) } as SensorEventListener)
            
            executeUntilSucceeds {
                assertTrue events.size() > NUM_CONSECUTIVE_EVENTS
                long eventTime = 0
                
                for (SensorEvent event in events.subList(events.size()-NUM_CONSECUTIVE_EVENTS, events.size())) {
                    assertEquals event.source, entity
                    assertEquals event.sensor, JavaWebApp.AVG_REQUESTS_PER_SECOND
                    assertEquals event.value, 0.0d
                    if (eventTime > 0) assertTrue(event.getTimestamp()-eventTime < MAX_INTERVAL_BETWEEN_EVENTS)
                    eventTime = event.getTimestamp()
                }
            }
        } finally {
            if (subscriptionHandle) subContext.unsubscribe(subscriptionHandle)
            entity.stop()
        }
    }

    /**
     * Twins the entities given by basicEntities() with links to WAR files
     * they should be able to deploy.  Correct deployment can be checked by
     * pinging the given URL.
     *
     * <ul>
     * <li>Everything can deploy hello world
     * <li>Tomcat can deploy Spring travel
     * <li>JBoss can deploy Seam travel
     * </ul>
     */
    @DataProvider(name = "entitiesWithWARAndURL")
    public Object[][] entitiesWithWAR() {
        basicEntities().collect {
            [   it[0],
                "hello-world.war",
                "hello-world/",
            ]
        } + [
            [   new TomcatServer(owner:application, httpPort:DEFAULT_HTTP_PORT), 
                "swf-booking-mvc.war",
                "swf-booking-mvc/spring/intro",
            ],
            // FIXME seam-booking does not work
//            [   new JBoss6Server(owner:application, portIncrement:PORT_INCREMENT),
//				"seam-booking-as6.war",
//                "seam-booking-as6/",
//            ],
//            [   new JBoss7Server(owner:application, httpPort:DEFAULT_HTTP_PORT),
//                "seam-booking-as7.war",
//                "seam-booking-as7/",
//            ],
        ]
    }

    /**
     * Tests given entity can deploy the given war.  Checks given httpURL to confirm success.
     */
    @Test(groups = "Integration", dataProvider = "entitiesWithWARAndURL")
    public void warDeployments(JavaWebApp entity, String war, String httpURL) {
        URL resource = getClass().getClassLoader().getResource(war)
        assertNotNull resource
        
        entity.setConfig(JavaWebApp.WAR, resource.path)
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(entity, abortOnError:false, timeout:10*SECONDS) {
            // TODO get this URL from a WAR file entity
            assertTrue urlRespondsWithStatusCode200(entity.getAttribute(JavaWebApp.ROOT_URL) + httpURL)
            true
        }
    }
}
