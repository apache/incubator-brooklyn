/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.webapp;

import static brooklyn.test.HttpTestUtils.connectToUrl;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToMultiFile;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

/**
 * Test fixture for implementations of JavaWebApp, checking start up and shutdown, 
 * post request and error count metrics and deploy wars, etc.
 */
public abstract class AbstractWebAppFixtureIntegrationTest {
    
    private static final Logger log = LoggerFactory.getLogger(AbstractWebAppFixtureIntegrationTest.class);
    
    // Don't use 8080 since that is commonly used by testing software
    public static final String DEFAULT_HTTP_PORT = "7880+";
    
    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400;

    // The parent application entity for these tests
    protected ManagementContext mgmt;
    protected List<TestApplication> applications = Lists.newArrayList();
    protected SoftwareProcess entity;
    protected LocalhostMachineProvisioningLocation loc;

    protected synchronized ManagementContext getMgmt() {
        if (mgmt==null)
            mgmt = new LocalManagementContext();
        return mgmt;
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new LocalhostMachineProvisioningLocation(MutableMap.of("name", "london"));
        getMgmt().getLocationManager().manage(loc);
    }
    
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
            Application app = entity.getApplication();
            if (app != null) Entities.destroy(app);
        }
    }

    @AfterClass
    public synchronized void shutdownMgmt() {
        if (mgmt != null) {
            Entities.destroyAll(mgmt);
            mgmt = null;
        }
    }

    /** 
     * Create a new instance of TestApplication and append it to applications list
     * so it can be terminated suitable after each test has run.
     * @return
     */
    protected TestApplication newTestApplication() {
        TestApplication ta = ApplicationBuilder.newManagedApp(TestApplication.class, getMgmt());
        applications.add(ta);
        return ta;
    }

    /**
     * Provides instances of the WebAppServer to test
     * (arrays of 1-element array arguments to some of the other methods) 
     *
     * NB annotation must be placed on concrete impl method
     * 
     * TODO combine the data provider here with live integration test
     * @see WebAppLiveIntegrationTest#basicEntities()
     */
    @DataProvider(name = "basicEntities")
    public abstract Object[][] basicEntities();

    /**
     * Checks an entity can start, set SERVICE_UP to true and shutdown again.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void canStartAndStop(final SoftwareProcess entity) {
        this.entity = entity;
        log.info("test=canStartAndStop; entity="+entity+"; app="+entity.getApplication());
        
        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        Asserts.succeedsEventually(MutableMap.of("timeout", 120*1000), new Runnable() {
            public void run() {
                assertTrue(entity.getAttribute(Startable.SERVICE_UP));
            }});
        
        entity.stop();
        assertFalse(entity.getAttribute(Startable.SERVICE_UP));
    }
    
    /**
     * Checks an entity can start, set SERVICE_UP to true and shutdown again.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void testReportsServiceDownWhenKilled(final SoftwareProcess entity) throws Exception {
        this.entity = entity;
        log.info("test=testReportsServiceDownWithKilled; entity="+entity+"; app="+entity.getApplication());
        
        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", 120*1000), entity, Startable.SERVICE_UP, true);

        // Stop the underlying entity, but without our entity instance being told!
        killEntityBehindBack(entity);
        log.info("Killed {} behind mgmt's back, waiting for service up false in mgmt context", entity);
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, false);
        
        log.info("success getting service up false in primary mgmt universe");
    }
    
    /**
     * Stop the given underlying entity, but without our entity instance being told!
     */
    protected void killEntityBehindBack(Entity tokill) throws Exception {
        // Previously was calling entity.getDriver().kill(); but now our entity instance is a proxy so can't do that
        ManagementContext newManagementContext = null;
        File tempDir = Os.newTempDir(getClass());
        try {
            ManagementContext managementContext = ((EntityInternal)tokill).getManagementContext();
            BrooklynMemento brooklynMemento = MementosGenerators.newBrooklynMemento(managementContext);
            
            BrooklynMementoPersisterToMultiFile oldPersister = new BrooklynMementoPersisterToMultiFile(tempDir , getClass().getClassLoader());
            oldPersister.checkpoint(brooklynMemento, PersistenceExceptionHandlerImpl.builder().build());
            oldPersister.waitForWritesCompleted(30*1000, TimeUnit.MILLISECONDS);

            BrooklynMementoPersisterToMultiFile newPersister = new BrooklynMementoPersisterToMultiFile(tempDir , getClass().getClassLoader());
            newManagementContext = new LocalManagementContextForTests();
            newManagementContext.getRebindManager().setPersister(newPersister, PersistenceExceptionHandlerImpl.builder().build());
            newManagementContext.getRebindManager().rebind(getClass().getClassLoader());
            newManagementContext.getRebindManager().start();
            SoftwareProcess entity2 = (SoftwareProcess) newManagementContext.getEntityManager().getEntity(tokill.getId());
            entity2.stop();
        } finally {
            if (newManagementContext != null) ((ManagementContextInternal)newManagementContext).terminate();
            Os.deleteRecursively(tempDir.getAbsolutePath());
        }
        log.info("called to stop {} in parallel mgmt universe", entity);
    }
    
    /**
     * Checks that an entity correctly sets request and error count metrics by
     * connecting to a non-existent URL several times.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void publishesRequestAndErrorCountMetrics(final SoftwareProcess entity) throws Exception {
        this.entity = entity;
        log.info("test=publishesRequestAndErrorCountMetrics; entity="+entity+"; app="+entity.getApplication());
        
        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        
        Asserts.succeedsEventually(MutableMap.of("timeout", 10*1000), new Runnable() {
            public void run() {
                assertTrue(entity.getAttribute(SoftwareProcess.SERVICE_UP));
            }});
        
        String url = entity.getAttribute(WebAppService.ROOT_URL) + "does_not_exist";
        
        final int n = 10;
        for (int i = 0; i < n; i++) {
            URLConnection connection = HttpTestUtils.connectToUrl(url);
            int status = ((HttpURLConnection) connection).getResponseCode();
            log.info("connection to {} gives {}", url, status);
        }
        
        Asserts.succeedsEventually(MutableMap.of("timeout", 20*1000), new Runnable() {
            public void run() {
                Integer requestCount = entity.getAttribute(WebAppService.REQUEST_COUNT);
                Integer errorCount = entity.getAttribute(WebAppService.ERROR_COUNT);
                log.info("req={}, err={}", requestCount, errorCount);
                
                assertNotNull(errorCount, "errorCount not set yet ("+errorCount+")");
    
                // AS 7 seems to take a very long time to report error counts,
                // hence not using ==.  >= in case error pages include a favicon, etc.
                assertEquals(errorCount, (Integer)n);
                assertTrue(requestCount >= errorCount);
            }});
    }
    
    /**
     * Checks an entity publishes correct requests/second figures and that these figures
     * fall to zero after a period of no activity.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void publishesRequestsPerSecondMetric(final SoftwareProcess entity) throws Exception {
        this.entity = entity;
        log.info("test=publishesRequestsPerSecondMetric; entity="+entity+"; app="+entity.getApplication());
        
        Entities.start(entity.getApplication(), ImmutableList.of(loc));

        log.info("Entity "+entity+" started");
        
        try {
            // reqs/sec initially zero
            log.info("Waiting for initial avg-requests to be zero...");
            Asserts.succeedsEventually(MutableMap.of("timeout", 20*1000), new Runnable() {
                public void run() {
                    Double activityValue = entity.getAttribute(WebAppService.REQUESTS_PER_SECOND_IN_WINDOW);
                    assertNotNull(activityValue, "activity not set yet "+activityValue+")");
                    assertEquals(activityValue.doubleValue(), 0.0d, 0.000001d);
                }});
            
            // apply workload on 1 per sec; reqs/sec should update
            Asserts.succeedsEventually(MutableMap.of("timeout", 30*1000), new Callable<Void>() {
                public Void call() throws Exception {
                    String url = entity.getAttribute(WebAppService.ROOT_URL) + "does_not_exist";
                    final int desiredMsgsPerSec = 10;
                    
                    Stopwatch stopwatch = Stopwatch.createStarted();
                    final AtomicInteger reqsSent = new AtomicInteger();
                    final Integer preRequestCount = entity.getAttribute(WebAppService.REQUEST_COUNT);
                    
                    // need to maintain n requests per second for the duration of the window size
                    log.info("Applying load for "+WebAppServiceMethods.DEFAULT_WINDOW_DURATION);
                    while (stopwatch.elapsed(TimeUnit.MILLISECONDS) < WebAppServiceMethods.DEFAULT_WINDOW_DURATION.toMilliseconds()) {
                        long preReqsTime = stopwatch.elapsed(TimeUnit.MILLISECONDS);
                        for (int i = 0; i < desiredMsgsPerSec; i++) { connectToUrl(url); }
                        sleep(1000 - (stopwatch.elapsed(TimeUnit.MILLISECONDS)-preReqsTime));
                        reqsSent.addAndGet(desiredMsgsPerSec);
                    }
    
                    Asserts.succeedsEventually(MutableMap.of("timeout", 1000), new Runnable() {
                        public void run() {
                            Double avgReqs = entity.getAttribute(WebAppService.REQUESTS_PER_SECOND_IN_WINDOW);
                            Integer requestCount = entity.getAttribute(WebAppService.REQUEST_COUNT);
                            
                            log.info("avg-requests="+avgReqs+"; total-requests="+requestCount);
                            assertEquals(avgReqs.doubleValue(), (double)desiredMsgsPerSec, 3.0d);
                            assertEquals(requestCount.intValue(), preRequestCount+reqsSent.get());
                        }});
                    
                    return null;
                }});
            
            // After suitable delay, expect to again get zero msgs/sec
            log.info("Waiting for avg-requests to drop to zero, for "+WebAppServiceMethods.DEFAULT_WINDOW_DURATION);
            Thread.sleep(WebAppServiceMethods.DEFAULT_WINDOW_DURATION.toMilliseconds());
            
            Asserts.succeedsEventually(MutableMap.of("timeout", 10*1000), new Runnable() {
                public void run() {
                    Double avgReqs = entity.getAttribute(WebAppService.REQUESTS_PER_SECOND_IN_WINDOW);
                    assertNotNull(avgReqs);
                    assertEquals(avgReqs.doubleValue(), 0.0d, 0.00001d);
                }});
        } finally {
            entity.stop();
        }
    }

    /**
     * Tests that we get consecutive events with zero workrate, and with suitably small timestamps between them.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    @SuppressWarnings("rawtypes")
    public void publishesZeroRequestsPerSecondMetricRepeatedly(final SoftwareProcess entity) {
        this.entity = entity;
        log.info("test=publishesZeroRequestsPerSecondMetricRepeatedly; entity="+entity+"; app="+entity.getApplication());
        
        final int MAX_INTERVAL_BETWEEN_EVENTS = 1000; // events should publish every 500ms so this should be enough overhead
        final int NUM_CONSECUTIVE_EVENTS = 3;

        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        
        SubscriptionHandle subscriptionHandle = null;
        SubscriptionContext subContext = ((EntityInternal)entity).getSubscriptionContext();

        try {
            final List<SensorEvent> events = new CopyOnWriteArrayList<SensorEvent>();
            subscriptionHandle = subContext.subscribe(entity, WebAppService.REQUESTS_PER_SECOND_IN_WINDOW, new SensorEventListener<Double>() {
                public void onEvent(SensorEvent<Double> event) {
                    log.info("publishesRequestsPerSecondMetricRepeatedly.onEvent: {}", event);
                    events.add(event);
                }});
            
            
            Asserts.succeedsEventually(new Runnable() {
                public void run() {
                    assertTrue(events.size() > NUM_CONSECUTIVE_EVENTS, "events "+events.size()+" > "+NUM_CONSECUTIVE_EVENTS);
                    long eventTime = 0;
                    
                    for (SensorEvent event : events.subList(events.size()-NUM_CONSECUTIVE_EVENTS, events.size())) {
                        assertEquals(event.getSource(), entity);
                        assertEquals(event.getSensor(), WebAppService.REQUESTS_PER_SECOND_IN_WINDOW);
                        assertEquals(event.getValue(), 0.0d);
                        if (eventTime > 0) assertTrue(event.getTimestamp()-eventTime < MAX_INTERVAL_BETWEEN_EVENTS,
    						"events at "+eventTime+" and "+event.getTimestamp()+" exceeded maximum allowable interval "+MAX_INTERVAL_BETWEEN_EVENTS);
                        eventTime = event.getTimestamp();
                    }
                }});
        } finally {
            if (subscriptionHandle != null) subContext.unsubscribe(subscriptionHandle);
            entity.stop();
        }
    }

    /**
     * Twins the entities given by basicEntities() with links to WAR files
     * they should be able to deploy.  Correct deployment can be checked by
     * pinging the given URL.
     *
     * Everything can deploy hello world. Some subclasses deploy add'l apps.
     */
    @DataProvider(name = "entitiesWithWarAndURL")
    public Object[][] entitiesWithWar() {
        List<Object[]> result = Lists.newArrayList();
        
        for (Object[] entity : basicEntities()) {
            result.add(new Object[] {
                    entity[0],
                    "hello-world.war",
                    "hello-world/",
                    "" // no sub-page path
                    });
        }
        
        return result.toArray(new Object[][] {});
    }

    /**
     * Tests given entity can deploy the given war.  Checks given httpURL to confirm success.
     */
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void initialRootWarDeployments(final SoftwareProcess entity, final String war, 
			final String urlSubPathToWebApp, final String urlSubPathToPageToQuery) {
        this.entity = entity;
        log.info("test=initialRootWarDeployments; entity="+entity+"; app="+entity.getApplication());
        
        URL resource = getClass().getClassLoader().getResource(war);
        assertNotNull(resource);
        
        ((EntityLocal)entity).setConfig(JavaWebAppService.ROOT_WAR, resource.getPath());
        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        
		//tomcat may need a while to unpack everything
        Asserts.succeedsEventually(MutableMap.of("timeout", 60*1000), new Runnable() {
            public void run() {
                // TODO get this URL from a WAR file entity
                HttpTestUtils.assertHttpStatusCodeEquals(entity.getAttribute(WebAppService.ROOT_URL)+urlSubPathToPageToQuery, 200);
                
                assertEquals(entity.getAttribute(JavaWebAppSoftwareProcess.DEPLOYED_WARS), ImmutableSet.of("/"));
            }});
    }
	
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void initialNamedWarDeployments(final SoftwareProcess entity, final String war, 
			final String urlSubPathToWebApp, final String urlSubPathToPageToQuery) {
        this.entity = entity;
        log.info("test=initialNamedWarDeployments; entity="+entity+"; app="+entity.getApplication());
        
        URL resource = getClass().getClassLoader().getResource(war);
        assertNotNull(resource);
        
        ((EntityLocal)entity).setConfig(JavaWebAppService.NAMED_WARS, ImmutableList.of(resource.getPath()));
        Entities.start(entity.getApplication(), ImmutableList.of(loc));

        Asserts.succeedsEventually(MutableMap.of("timeout", 60*1000), new Runnable() {
            public void run() {
                // TODO get this URL from a WAR file entity
                HttpTestUtils.assertHttpStatusCodeEquals(entity.getAttribute(WebAppService.ROOT_URL)+urlSubPathToWebApp+urlSubPathToPageToQuery, 200);
            }});
    }
	
    @Test(groups = "Integration", dataProvider = "entitiesWithWarAndURL")
    public void testWarDeployAndUndeploy(final JavaWebAppSoftwareProcess entity, final String war, 
            final String urlSubPathToWebApp, final String urlSubPathToPageToQuery) {
        this.entity = entity;
        log.info("test=testWarDeployAndUndeploy; entity="+entity+"; app="+entity.getApplication());
        
        URL resource = getClass().getClassLoader().getResource(war);;
        assertNotNull(resource);
        
        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        
        // Test deploying
        entity.deploy(resource.getPath(), "myartifactname.war");
        Asserts.succeedsEventually(MutableMap.of("timeout", 60*1000), new Runnable() {
            public void run() {
                // TODO get this URL from a WAR file entity
                HttpTestUtils.assertHttpStatusCodeEquals(entity.getAttribute(WebAppService.ROOT_URL)+"myartifactname/"+urlSubPathToPageToQuery, 200);
                assertEquals(entity.getAttribute(JavaWebAppSoftwareProcess.DEPLOYED_WARS), ImmutableSet.of("/myartifactname"));
            }});
        
        // And undeploying
        entity.undeploy("/myartifactname");
        Asserts.succeedsEventually(MutableMap.of("timeout", 60*1000), new Runnable() {
            public void run() {
                // TODO get this URL from a WAR file entity
                HttpTestUtils.assertHttpStatusCodeEquals(entity.getAttribute(WebAppService.ROOT_URL)+"myartifactname"+urlSubPathToPageToQuery, 404);
                assertEquals(entity.getAttribute(JavaWebAppSoftwareProcess.DEPLOYED_WARS), ImmutableSet.of());
            }});
    }
    	
    private void sleep(long millis) {
        if (millis > 0) Time.sleep(millis);
    }    
}
