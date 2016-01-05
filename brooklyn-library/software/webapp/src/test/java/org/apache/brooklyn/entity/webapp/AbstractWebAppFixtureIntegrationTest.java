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
package org.apache.brooklyn.entity.webapp;

import static org.apache.brooklyn.test.HttpTestUtils.connectToUrl;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.crypto.FluentKeySigner;
import org.apache.brooklyn.util.core.crypto.SecureKeys;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

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
            mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
        return mgmt;
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = getMgmt().getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
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

    @AfterClass(alwaysRun=true)
    public synchronized void shutdownMgmt() {
        try {
            if (mgmt != null) Entities.destroyAll(mgmt);
        } finally {
            mgmt = null;
        }
    }

    public static File createTemporaryKeyStore(String alias, String password) throws Exception {
        FluentKeySigner signer = new FluentKeySigner("brooklyn-test").selfsign();

        KeyStore ks = SecureKeys.newKeyStore();
        ks.setKeyEntry(
                alias,
                signer.getKey().getPrivate(),
                password.toCharArray(),
                new Certificate[]{signer.getAuthorityCertificate()});

        File file = File.createTempFile("test", "keystore");
        FileOutputStream fos = new FileOutputStream(file);
        try {
            ks.store(fos, password.toCharArray());
            return file;
        } finally {
            Streams.closeQuietly(fos);
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
        EntityAsserts.assertAttributeEqualsEventually(
                MutableMap.of("timeout", 120*1000), entity, Startable.SERVICE_UP, Boolean.TRUE);
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
        EntityAsserts.assertAttributeEqualsEventually(MutableMap.of("timeout", 120*1000), entity, Startable.SERVICE_UP, true);

        // Stop the underlying entity, but without our entity instance being told!
        killEntityBehindBack(entity);
        log.info("Killed {} behind mgmt's back, waiting for service up false in mgmt context", entity);
        
        EntityAsserts.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, false);
        
        log.info("success getting service up false in primary mgmt universe");
    }
    
    /**
     * Stop the given underlying entity, but without our entity instance being told!
     */
    protected void killEntityBehindBack(Entity tokill) throws Exception {
        ((SoftwareProcessImpl) Entities.deproxy(tokill)).getDriver().stop();
        // old method of doing this did some dodgy legacy rebind and failed due to too many dangling refs; above is better in any case
        // but TODO we should have some rebind tests for these!
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
        EntityAsserts.assertAttributeEqualsEventually(
                MutableMap.of("timeout", 120 * 1000), entity, Startable.SERVICE_UP, Boolean.TRUE);

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
                        Time.sleep(1000 - (stopwatch.elapsed(TimeUnit.MILLISECONDS)-preReqsTime));
                        reqsSent.addAndGet(desiredMsgsPerSec);
                    }
    
                    Asserts.succeedsEventually(MutableMap.of("timeout", 4000), new Runnable() {
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
        
        final int maxIntervalBetweenEvents = 4000; // TomcatServerImpl publishes events every 3000ms so this should be enough overhead
        final int consecutiveEvents = 3;

        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        SubscriptionHandle subscriptionHandle = null;
        final CopyOnWriteArrayList<SensorEvent<Double>> events = new CopyOnWriteArrayList<>();
        try {
            subscriptionHandle = recordEvents(entity, WebAppService.REQUESTS_PER_SECOND_IN_WINDOW, events);
            Asserts.succeedsEventually(assertConsecutiveSensorEventsEqual(
                    events, WebAppService.REQUESTS_PER_SECOND_IN_WINDOW, 0.0d, consecutiveEvents, maxIntervalBetweenEvents));
        } finally {
            if (subscriptionHandle != null) entity.subscriptions().unsubscribe(subscriptionHandle);
            entity.stop();
        }
    }

    /**
     * Tests that requests/sec last and windowed decay when the entity can't be contacted for
     * up to date values.
     */
    @Test(groups = "Integration", dataProvider = "basicEntities")
    public void testRequestCountContinuallyPublishedWhenEntityKilled(final SoftwareProcess entity) throws Exception {
        this.entity = entity;
        log.info("test=testRequestCountContinuallyPublishedWhenEntityKilled; entity="+entity+"; app="+entity.getApplication());

        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        EntityAsserts.assertAttributeEqualsEventually(entity, SoftwareProcess.SERVICE_UP, Boolean.TRUE);
        String url = entity.getAttribute(WebAppService.ROOT_URL) + "does_not_exist";

        // Apply load to entity. Assert enriched sensor values.
        HttpTestUtils.connectToUrl(url);
        EntityAsserts.assertAttributeEventually(entity, WebAppServiceMetrics.REQUEST_COUNT, new Predicate<Integer>() {
                @Override public boolean apply(Integer input) {
                    return input > 0;
                }});
        killEntityBehindBack(entity);

        final int requestCountAfterKilled = entity.sensors().get(WebAppServiceMetrics.REQUEST_COUNT);
        final int maxIntervalBetweenEvents = 4000; // TomcatServerImpl publishes events every 3000ms so this should be enough overhead
        final int consecutiveEvents = 3;

        // The entity should be configured to keep publishing request count, so
        SubscriptionHandle subscriptionHandle = null;
        final CopyOnWriteArrayList<SensorEvent<Integer>> events = new CopyOnWriteArrayList<>();
        try {
            subscriptionHandle = recordEvents(entity, WebAppServiceMetrics.REQUEST_COUNT, events);
            Asserts.succeedsEventually(assertConsecutiveSensorEventsEqual(
                    events, WebAppServiceMetrics.REQUEST_COUNT, requestCountAfterKilled, consecutiveEvents, maxIntervalBetweenEvents));
        } finally {
            if (subscriptionHandle != null) entity.subscriptions().unsubscribe(subscriptionHandle);
            entity.stop();
        }
    }

    protected <T> SubscriptionHandle recordEvents(Entity entity, AttributeSensor<T> sensor, final List<SensorEvent<T>> events) {
        SensorEventListener<T> listener = new SensorEventListener<T>() {
            @Override public void onEvent(SensorEvent<T> event) {
                log.info("onEvent: {}", event);
                events.add(event);
            }
        };
        return entity.subscriptions().subscribe(entity, sensor, listener);
    }

    protected <T> Runnable assertConsecutiveSensorEventsEqual(final List<SensorEvent<T>> events,
                final Sensor<T> sensor, final T expectedValue,
                final int numConsecutiveEvents, final int maxIntervalBetweenEvents) {
        return new Runnable() {
            @Override public void run() {
                assertTrue(events.size() > numConsecutiveEvents, "events " + events.size() + " > " + numConsecutiveEvents);
                long eventTime = 0;

                for (SensorEvent event : events.subList(events.size() - numConsecutiveEvents, events.size())) {
                    assertEquals(event.getSource(), entity);
                    assertEquals(event.getSensor(), sensor);
                    assertEquals(event.getValue(), expectedValue);
                    if (eventTime > 0) assertTrue(event.getTimestamp() - eventTime < maxIntervalBetweenEvents,
                            "events at " + eventTime + " and " + event.getTimestamp() + " exceeded maximum allowable interval " + maxIntervalBetweenEvents);
                    eventTime = event.getTimestamp();
                }
            }
        };
    }

    /**
     * Twins the entities given by basicEntities() with links to WAR files
     * they should be able to deploy.  Correct deployment can be checked by
     * pinging the given URL.
     *
     * Everything can deploy hello world. Some subclasses deploy add'l apps.
     * We're using the simplest hello-world (with no URL mapping) because JBoss 6 does not
     * support URL mappings.
     */
    @DataProvider(name = "entitiesWithWarAndURL")
    public Object[][] entitiesWithWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world-no-mapping.war");
        List<Object[]> result = Lists.newArrayList();
        
        for (Object[] entity : basicEntities()) {
            result.add(new Object[] {
                    entity[0],
                    "hello-world-no-mapping.war",
                    "hello-world-no-mapping/",
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
        
        ((EntityLocal)entity).config().set(JavaWebAppService.ROOT_WAR, resource.toString());
        Entities.start(entity.getApplication(), ImmutableList.of(loc));
        
        //tomcat may need a while to unpack everything
        Asserts.succeedsEventually(MutableMap.of("timeout", 60*1000), new Runnable() {
            public void run() {
                // TODO get this URL from a WAR file entity
                HttpTestUtils.assertHttpStatusCodeEquals(Urls.mergePaths(entity.getAttribute(WebAppService.ROOT_URL), urlSubPathToPageToQuery), 200);
                
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
        
        ((EntityLocal)entity).config().set(JavaWebAppService.NAMED_WARS, ImmutableList.of(resource.toString()));
        Entities.start(entity.getApplication(), ImmutableList.of(loc));

        Asserts.succeedsEventually(MutableMap.of("timeout", 60*1000), new Runnable() {
            public void run() {
                // TODO get this URL from a WAR file entity
                HttpTestUtils.assertHttpStatusCodeEquals(Urls.mergePaths(entity.getAttribute(WebAppService.ROOT_URL), urlSubPathToWebApp, urlSubPathToPageToQuery), 200);
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
        entity.deploy(resource.toString(), "myartifactname.war");
        Asserts.succeedsEventually(MutableMap.of("timeout", 60*1000), new Runnable() {
            public void run() {
                // TODO get this URL from a WAR file entity
                HttpTestUtils.assertHttpStatusCodeEquals(Urls.mergePaths(entity.getAttribute(WebAppService.ROOT_URL), "myartifactname/", urlSubPathToPageToQuery), 200);
                assertEquals(entity.getAttribute(JavaWebAppSoftwareProcess.DEPLOYED_WARS), ImmutableSet.of("/myartifactname"));
            }});
        
        // And undeploying
        entity.undeploy("/myartifactname");
        Asserts.succeedsEventually(MutableMap.of("timeout", 60*1000), new Runnable() {
            public void run() {
                // TODO get this URL from a WAR file entity
                HttpTestUtils.assertHttpStatusCodeEquals(Urls.mergePaths(entity.getAttribute(WebAppService.ROOT_URL), "myartifactname", urlSubPathToPageToQuery), 404);
                assertEquals(entity.getAttribute(JavaWebAppSoftwareProcess.DEPLOYED_WARS), ImmutableSet.of());
            }});
    }
}
