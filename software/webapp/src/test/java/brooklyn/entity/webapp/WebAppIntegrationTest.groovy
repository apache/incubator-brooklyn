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

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.legacy.JavaApp
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.test.TestUtils.BooleanWithMessage
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

/**
 * Tests that implementations of JavaWebApp can start up and shutdown, 
 * post request and error count metrics and deploy wars, etc.
 * 
 * Currently tests {@link TomcatServer}, {@link JBoss6Server} and {@link JBoss7Server}.
 */
public class WebAppIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(WebAppIntegrationTest.class)
    
    static { TimeExtras.init() }
    
    // Don't use 8080 since that is commonly used by testing software
    public static final int DEFAULT_HTTP_PORT = 7880
    
    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400

    // The owner application entity for these tests
    private List<AbstractApplication> applications = new ArrayList<AbstractApplication>()
    SoftwareProcessEntity entity
    
	static { TimeExtras.init() }
	
    @BeforeMethod(groups = "Integration")
    public void failIfHttpPortInUse() {
        if (isPortInUse(DEFAULT_HTTP_PORT, 5000L)) {
            fail "someone is already listening on port $DEFAULT_HTTP_PORT; tests assume that port $DEFAULT_HTTP_PORT is free on localhost"
        }
    }

    // Make sure everything created by newTestApplication() is shut down
    @AfterMethod(alwaysRun=true)
    public void shutdownApp() {
        for (AbstractApplication app : applications) {
            app.stop()
        }
        applications.clear()
    }

    @AfterMethod(alwaysRun=true)
    public void ensureTomcatIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;
        Integer shutdownPort = entity?.getAttribute(TomcatServer.TOMCAT_SHUTDOWN_PORT)
        
        if (shutdownPort != null) {
            boolean socketClosed = new Repeater("Checking Tomcat has shut down")
                .repeat {
                        if (shutdownSocket) shutdownSocket.close();
                        try { shutdownSocket = new Socket(InetAddress.localHost, shutdownPort); }
                        catch (SocketException e) { gotException = e; return; }
                        gotException = null
                    }
                .every(100 * MILLISECONDS)
                .until { gotException }
                .limitIterationsTo(25)
                .run();
    
            if (socketClosed == false) {
                log.error "Tomcat did not shut down - this is a failure of the last test run";
                log.warn "I'm sending a message to the Tomcat shutdown port";
                OutputStreamWriter writer = new OutputStreamWriter(shutdownSocket.getOutputStream());
                writer.write("SHUTDOWN\r\n");
                writer.flush();
                writer.close();
                shutdownSocket.close();
                throw new Exception("Last test run did not shut down Tomcat")
            }
        } else {
            log.info "Cannot shutdown, because shutdown-port not set for $entity";
        }
    }

    /** 
     * Create a new instance of TestApplication and append it to applications list
     * so it can be terminated suitable after each test has run.
     * @return
     */
    private TestApplication newTestApplication() {
        TestApplication ta = new TestApplication()
        applications.add(ta)
        return ta
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
		//FIXME we should start the application, not the entity
        TomcatServer tomcat = [ owner:newTestApplication(), httpPort:DEFAULT_HTTP_PORT ]
        JBoss6Server jboss6 = [ owner:newTestApplication(), portIncrement:PORT_INCREMENT ]
        JBoss7Server jboss7 = [ owner:newTestApplication(), httpPort:DEFAULT_HTTP_PORT ]
        return [ 
			[ tomcat ], 
			[ jboss6 ], 
			[ jboss7 ]
		]
    }

    /**
     * Checks an entity can start, set SERVICE_UP to true and shutdown again.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void canStartAndStop(SoftwareProcessEntity entity) {
        this.entity = entity
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(timeout: 120*SECONDS, entity) {
            assertTrue entity.getAttribute(JavaApp.SERVICE_UP)
        }
        assertFalse entity.getAttribute(JavaApp.SERVICE_UP)
    }
    
    /**
     * Checks that an entity correctly sets request and error count metrics by
     * connecting to a non-existent URL several times.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void publishesRequestAndErrorCountMetrics(SoftwareProcessEntity entity) {
        this.entity = entity
        entity.pollForHttpStatus = false
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        String url = entity.getAttribute(WebAppService.ROOT_URL) + "does_not_exist"
        
        executeUntilSucceeds(timeout:10*SECONDS) {
            assertTrue entity.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        }
        
        final int n = 10
        n.times {
            def connection = connectToURL(url)
            int status = ((HttpURLConnection) connection).getResponseCode()
            log.debug "connection to {} gives {}", url, status
        }
        
        executeUntilSucceedsWithShutdown(entity, timeout:20*SECONDS) {
            def requestCount = entity.getAttribute(WebAppService.REQUEST_COUNT)
            def errorCount = entity.getAttribute(WebAppService.ERROR_COUNT)
            log.info "req=$requestCount, err=$errorCount"
            
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
    public void publishesRequestsPerSecondMetric(SoftwareProcessEntity entity) {
        this.entity = entity
        entity.pollForHttpStatus = false
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        try {
            // reqs/sec initially zero
            executeUntilSucceeds(useGroovyTruth:true) {
                Double activityValue = entity.getAttribute(WebAppService.AVG_REQUESTS_PER_SECOND)
                if (activityValue == null)
                    return new BooleanWithMessage(false, "activity not set yet ($activityValue)")

                assertEquals activityValue, 0.0d
                true
            }
            
            // apply workload on 1 per sec; reqs/sec should update
            executeUntilSucceeds(timeout:10*SECONDS, useGroovyTruth:true) {
                String url = entity.getAttribute(WebAppService.ROOT_URL) + "foo"

                long startTime = System.currentTimeMillis()
                long elapsedTime = 0
                
                // need to maintain n requests per second for the duration of the window size
                while (elapsedTime < WebAppService.AVG_REQUESTS_PER_SECOND_PERIOD) {
                    int n = 10
                    n.times { connectToURL url }
                    Thread.sleep 1000
                    def requestCount = entity.getAttribute(WebAppService.REQUEST_COUNT)
                    assertEquals requestCount % n, 0
                    elapsedTime = System.currentTimeMillis() - startTime
                }

                Double activityValue = entity.getAttribute(WebAppService.AVG_REQUESTS_PER_SECOND)
                assertEquals activityValue, 10.0d, 1.0d

                true
            }
            
            // After suitable delay, expect to again get zero msgs/sec
            Thread.sleep(WebAppService.AVG_REQUESTS_PER_SECOND_PERIOD)
            
            executeUntilSucceeds {
                Double activityValue = entity.getAttribute(WebAppService.AVG_REQUESTS_PER_SECOND)
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
    public void publishesZeroRequestsPerSecondMetricRepeatedly(SoftwareProcessEntity entity) {
        this.entity = entity
        final int MAX_INTERVAL_BETWEEN_EVENTS = 1000 // events should publish every 500ms so this should be enough overhead
        final int NUM_CONSECUTIVE_EVENTS = 3

        entity.pollForHttpStatus = false 
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        SubscriptionHandle subscriptionHandle
        SubscriptionContext subContext = entity.subscriptionContext

        try {
            final List<SensorEvent> events = new CopyOnWriteArrayList<SensorEvent>()
            subscriptionHandle = subContext.subscribe(entity, WebAppService.AVG_REQUESTS_PER_SECOND, {
                    log.info("publishesRequestsPerSecondMetricRepeatedly.onEvent: $it"); events.add(it) } as SensorEventListener)
            
            executeUntilSucceeds {
                assertTrue events.size() > NUM_CONSECUTIVE_EVENTS, "events ${events.size()} > ${NUM_CONSECUTIVE_EVENTS}"
                long eventTime = 0
                
                for (SensorEvent event in events.subList(events.size()-NUM_CONSECUTIVE_EVENTS, events.size())) {
                    assertEquals event.source, entity
                    assertEquals event.sensor, WebAppService.AVG_REQUESTS_PER_SECOND
                    assertEquals event.value, 0.0d
                    if (eventTime > 0) assertTrue(event.getTimestamp()-eventTime < MAX_INTERVAL_BETWEEN_EVENTS,
						"events at ${eventTime} and ${event.getTimestamp()} exceeded maximum allowable interval ${MAX_INTERVAL_BETWEEN_EVENTS}")
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
    @DataProvider(name = "entitiesWithWarAndURL")
    public Object[][] entitiesWithWar() {
        basicEntities().collect {
            [   it[0],
                "hello-world.war",
                "hello-world/",
				"" // no sub-page path
            ]
        } + [
            [   new TomcatServer(owner:newTestApplication(), httpPort:DEFAULT_HTTP_PORT), 
                "swf-booking-mvc.war",
                "swf-booking-mvc/",
				"spring/intro",
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
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void warDeployments(SoftwareProcessEntity entity, String war, 
			String urlSubPathToWebApp, String urlSubPathToPageToQuery) {
        this.entity = entity
        URL resource = getClass().getClassLoader().getResource(war)
        assertNotNull resource
        
        entity.setConfig(JavaWebAppService.ROOT_WAR, resource.path)
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
		//tomcat may need a while to unpack everything
        executeUntilSucceedsWithShutdown(entity, abortOnError:false, timeout:60*SECONDS) {
            // TODO get this URL from a WAR file entity
            assertTrue urlRespondsWithStatusCode200(entity.getAttribute(WebAppService.ROOT_URL)+urlSubPathToPageToQuery)
            true
        }
    }
	
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void warDeploymentsNamed(SoftwareProcessEntity entity, String war, 
			String urlSubPathToWebApp, String urlSubPathToPageToQuery) {
        this.entity = entity
        URL resource = getClass().getClassLoader().getResource(war)
        assertNotNull resource
        
        entity.setConfig(JavaWebAppService.NAMED_DEPLOYMENTS, [resource.path])
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceedsWithShutdown(entity, abortOnError:false, timeout:60*SECONDS) {
            // TODO get this URL from a WAR file entity
            assertTrue urlRespondsWithStatusCode200(entity.getAttribute(WebAppService.ROOT_URL)+urlSubPathToWebApp+urlSubPathToPageToQuery)
            true
        }
    }
	
	public static void main(String ...args) {
		def t = new WebAppIntegrationTest();
		t.canStartAndStop(null)
	}
	
}
