package brooklyn.entity.webapp

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.CopyOnWriteArrayList

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.trait.Startable
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
import brooklyn.util.MutableMap
import brooklyn.util.internal.Repeater
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableSet

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
    public static final String DEFAULT_HTTP_PORT = "7880+"
    
    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400

    // The owner application entity for these tests
    private List<AbstractApplication> applications = new ArrayList<AbstractApplication>()
    SoftwareProcessEntity entity
    
	static { TimeExtras.init() }
    
    /*
     * Use of @DataProvider with test methods gives surprising behaviour with @AfterMethod.
     * Unless careful, this causes problems when trying to ensure everything is shutdown cleanly.
     *
     * Empirically, the rules seem to be...
     *  - @DataProvider method is called first; it creates a bunch of cases to run 
     *    (all sharing the same instance of WebAppIntegrationTest).
     *  - It runs the test method for the first time with the first @DataProvider value
     *  - It runs @AfterMethod
     *  - It runs the test method for the second @DataProvider value
     *  - It runs @AfterMethod
     *  - etc...
     *
     * Previously shutdownApp was calling stop on each app in applications, and clearing the applications set;
     * but then the second invocation of the method was starting an entity that was never stopped. Until recently,
     * every test method was also terminating the entity (belt-and-braces, but also brittle for if the method threw
     * an exception earlier). When that "extra" termination was removed, it meant the second and subsequent 
     * entities were never being stopped.
     *
     * Now we rely on having the test method set the entity field, so we can find out which application instance 
     * it is and calling stop on just that app + entity.
     */
    @AfterMethod(alwaysRun=true)
    public void shutdownApp() {
        if (entity != null) {
            AbstractApplication app = entity.getApplication();
            entity.stop();
            if (app != null) app.stop();
        }
    }

    @AfterMethod(alwaysRun=true)
    public void ensureTomcatIsShutDown() {
        Socket shutdownSocket = null;
        SocketException gotException = null;
        Integer shutdownPort = entity?.getAttribute(TomcatServer.SHUTDOWN_PORT)
        
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
        Entities.startManagement(ta);
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
    public JavaWebAppSoftwareProcess[][] basicEntities() {
		//FIXME we should start the application, not the entity
        TomcatServer tomcat = new TomcatServer(owner:newTestApplication(), httpPort:DEFAULT_HTTP_PORT);
        Entities.manage(tomcat);
        JBoss6Server jboss6 = new JBoss6Server( owner:newTestApplication(), portIncrement:PORT_INCREMENT);
        Entities.manage(jboss6);
        JBoss7Server jboss7 = new JBoss7Server(owner:newTestApplication(), httpPort:DEFAULT_HTTP_PORT);
        Entities.manage(jboss7);
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
        log.info("test=canStartAndStop; entity="+entity+"; app="+entity.getApplication())
        
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceeds(timeout: 120*SECONDS) {
            assertTrue entity.getAttribute(Startable.SERVICE_UP)
        }
        
        entity.stop()
        assertFalse entity.getAttribute(Startable.SERVICE_UP)
    }
    
    /**
     * Checks an entity can start, set SERVICE_UP to true and shutdown again.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void testReportsServiceDownWhenKilled(SoftwareProcessEntity entity) {
        this.entity = entity
        log.info("test=testReportsServiceDownWithKilled; entity="+entity+"; app="+entity.getApplication())
        
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceeds(timeout: 120*SECONDS) {
            assertTrue entity.getAttribute(Startable.SERVICE_UP)
        }
        
        entity.getDriver().kill();

        executeUntilSucceeds {
            assertFalse(entity.getAttribute(Startable.SERVICE_UP))
        }
    }
    
    /**
     * Checks that an entity correctly sets request and error count metrics by
     * connecting to a non-existent URL several times.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void publishesRequestAndErrorCountMetrics(SoftwareProcessEntity entity) {
        this.entity = entity
        log.info("test=publishesRequestAndErrorCountMetrics; entity="+entity+"; app="+entity.getApplication())
        
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        executeUntilSucceeds(timeout:10*SECONDS) {
            assertTrue entity.getAttribute(SoftwareProcessEntity.SERVICE_UP)
        }
        
        String url = entity.getAttribute(WebAppService.ROOT_URL) + "does_not_exist"
        
        final int n = 10
        n.times {
            def connection = connectToURL(url)
            int status = ((HttpURLConnection) connection).getResponseCode()
            log.info "connection to {} gives {}", url, status
        }
        
        executeUntilSucceeds(timeout:20*SECONDS) {
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
        log.info("test=publishesRequestsPerSecondMetric; entity="+entity+"; app="+entity.getApplication())
        
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        log.info("Entity "+entity+" started");
        
        try {
            // reqs/sec initially zero
            log.info("Waiting for initial avg-requests to be zero...");
            executeUntilSucceeds(useGroovyTruth:true) {
                Double activityValue = entity.getAttribute(WebAppService.AVG_REQUESTS_PER_SECOND)
                if (activityValue == null) {
                    return new BooleanWithMessage(false, "activity not set yet ($activityValue)")
                }
                assertEquals activityValue.doubleValue(), 0.0d, 0.000001d
                true
            }
            
            // apply workload on 1 per sec; reqs/sec should update
            executeUntilSucceeds(timeout:30*SECONDS) {
                String url = entity.getAttribute(WebAppService.ROOT_URL) + "does_not_exist"
                int desiredMsgsPerSec = 10
                
                Stopwatch stopwatch = new Stopwatch().start()
                int reqsSent = 0
                Integer preRequestCount = entity.getAttribute(WebAppService.REQUEST_COUNT)
                
                // need to maintain n requests per second for the duration of the window size
                log.info("Applying load for "+WebAppService.AVG_REQUESTS_PER_SECOND_PERIOD+"ms");
                while (stopwatch.elapsedMillis() < WebAppService.AVG_REQUESTS_PER_SECOND_PERIOD) {
                    long preReqsTime = stopwatch.elapsedMillis()
                    desiredMsgsPerSec.times { connectToURL url }
                    sleep(1000 - (stopwatch.elapsedMillis()-preReqsTime))
                    reqsSent += desiredMsgsPerSec
                }

                executeUntilSucceeds(timeout:1*SECONDS) {
                    Double avgReqs = entity.getAttribute(WebAppService.AVG_REQUESTS_PER_SECOND)
                    Integer requestCount = entity.getAttribute(WebAppService.REQUEST_COUNT)
                    
                    log.info("avg-requests="+avgReqs+"; total-requests="+requestCount);
                    assertEquals(avgReqs.doubleValue(), (double)desiredMsgsPerSec, 3.0d)
                    assertEquals(requestCount.intValue(), preRequestCount+reqsSent)
                }
            }
            
            // After suitable delay, expect to again get zero msgs/sec
            log.info("Waiting for avg-requests to drop to zero, for "+WebAppService.AVG_REQUESTS_PER_SECOND_PERIOD+"ms");
            Thread.sleep(WebAppService.AVG_REQUESTS_PER_SECOND_PERIOD)
            
            executeUntilSucceeds(timeout:10*SECONDS) {
                Double avgReqs = entity.getAttribute(WebAppService.AVG_REQUESTS_PER_SECOND)
                assertNotNull avgReqs
                assertEquals avgReqs.doubleValue(), 0.0d, 0.00001d
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
        log.info("test=publishesZeroRequestsPerSecondMetricRepeatedly; entity="+entity+"; app="+entity.getApplication())
        
        final int MAX_INTERVAL_BETWEEN_EVENTS = 1000 // events should publish every 500ms so this should be enough overhead
        final int NUM_CONSECUTIVE_EVENTS = 3

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
            [   new TomcatServer(MutableMap.of("httpPort",DEFAULT_HTTP_PORT),newTestApplication()),
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
    public void initialRootWarDeployments(SoftwareProcessEntity entity, String war, 
			String urlSubPathToWebApp, String urlSubPathToPageToQuery) {
        this.entity = entity
        log.info("test=initialRootWarDeployments; entity="+entity+"; app="+entity.getApplication())
        
        URL resource = getClass().getClassLoader().getResource(war)
        assertNotNull resource
        
        entity.setConfig(JavaWebAppService.ROOT_WAR, resource.path)
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
		//tomcat may need a while to unpack everything
        executeUntilSucceeds(abortOnError:false, timeout:60*SECONDS) {
            // TODO get this URL from a WAR file entity
            assertTrue urlRespondsWithStatusCode200(entity.getAttribute(WebAppService.ROOT_URL)+urlSubPathToPageToQuery)
            
            assertEquals(entity.getAttribute(JavaWebAppSoftwareProcess.DEPLOYED_WARS), ImmutableSet.of("/"))
            true
        }
    }
	
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void initialNamedWarDeployments(SoftwareProcessEntity entity, String war, 
			String urlSubPathToWebApp, String urlSubPathToPageToQuery) {
        this.entity = entity
        log.info("test=initialNamedWarDeployments; entity="+entity+"; app="+entity.getApplication())
        
        URL resource = getClass().getClassLoader().getResource(war)
        assertNotNull resource
        
        entity.setConfig(JavaWebAppService.NAMED_WARS, [resource.path])
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        executeUntilSucceeds(abortOnError:false, timeout:60*SECONDS) {
            // TODO get this URL from a WAR file entity
            assertTrue urlRespondsWithStatusCode200(entity.getAttribute(WebAppService.ROOT_URL)+urlSubPathToWebApp+urlSubPathToPageToQuery)
            true
        }
    }
	
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void testWarDeployAndUndeploy(JavaWebAppSoftwareProcess entity, String war, 
            String urlSubPathToWebApp, String urlSubPathToPageToQuery) {
        this.entity = entity;
        log.info("test=testWarDeployAndUndeploy; entity="+entity+"; app="+entity.getApplication())
        
        URL resource = getClass().getClassLoader().getResource(war);
        assertNotNull(resource);
        
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london') ])
        
        // Test deploying
        entity.deploy(resource.path, "myartifactname.war")
        executeUntilSucceeds(abortOnError:false, timeout:60*SECONDS) {
            // TODO get this URL from a WAR file entity
            assertTrue urlRespondsWithStatusCode200(entity.getAttribute(WebAppService.ROOT_URL)+"myartifactname/"+urlSubPathToPageToQuery)
            assertEquals(entity.getAttribute(JavaWebAppSoftwareProcess.DEPLOYED_WARS), ImmutableSet.of("/myartifactname"))
            true
        }
        
        // And undeploying
        entity.undeploy("/myartifactname")
        executeUntilSucceeds(abortOnError:false, timeout:60*SECONDS) {
            // TODO get this URL from a WAR file entity
            assertEquals(urlRespondsStatusCode(entity.getAttribute(WebAppService.ROOT_URL)+"myartifactname"+urlSubPathToPageToQuery), 404);
            assertEquals(entity.getAttribute(JavaWebAppSoftwareProcess.DEPLOYED_WARS), ImmutableSet.of())
            true
        }
    }
    
	public static void main(String ...args) {
		def t = new WebAppIntegrationTest();
		t.canStartAndStop(null)
	}
	
    private void sleep(long millis) {
        if (millis > 0) Thread.sleep(millis);
    }    
}
