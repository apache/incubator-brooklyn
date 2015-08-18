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
package brooklyn.management.usage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.management.internal.ManagementContextInternal;
import org.apache.brooklyn.core.management.internal.UsageListener.ApplicationMetadata;
import org.apache.brooklyn.core.management.usage.ApplicationUsage;
import org.apache.brooklyn.core.management.usage.ApplicationUsage.ApplicationEvent;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxying.EntityProxy;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class ApplicationUsageTrackingTest {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationUsageTrackingTest.class);

    protected TestApplication app;
    protected ManagementContextInternal mgmt;

    protected boolean shouldSkipOnBoxBaseDirResolution() {
        return true;
    }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = LocalManagementContextForTests.newInstance();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (mgmt != null) Entities.destroyAll(mgmt);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDown method", t);
        } finally {
            mgmt = null;
        }
    }

    @Test
    public void testUsageInitiallyEmpty() {
        Set<ApplicationUsage> usage = mgmt.getUsageManager().getApplicationUsage(Predicates.alwaysTrue());
        assertEquals(usage, ImmutableSet.of());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testAddAndRemoveLegacyUsageListener() throws Exception {
        final RecordingLegacyUsageListener listener = new RecordingLegacyUsageListener();
        mgmt.getUsageManager().addUsageListener(listener);
        
        app = TestApplication.Factory.newManagedInstanceForTests(mgmt);
        app.setCatalogItemId("testCatalogItem");
        app.start(ImmutableList.<Location>of());

        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = listener.getApplicationEvents();
                assertEquals(events.size(), 2, "events="+events); // expect STARTING and RUNNING
                
                String appId = (String) events.get(0).get(1);
                String appName = (String) events.get(0).get(2);
                String entityType = (String) events.get(0).get(3);
                String catalogItemId = (String) events.get(0).get(4);
                Map<?,?> metadata = (Map<?, ?>) events.get(0).get(5);
                ApplicationEvent appEvent = (ApplicationEvent) events.get(0).get(6);
                
                assertEquals(appId, app.getId(), "events="+events);
                assertNotNull(appName, "events="+events);
                assertEquals(catalogItemId, app.getCatalogItemId(), "events="+events);
                assertNotNull(entityType, "events="+events);
                assertNotNull(metadata, "events="+events);
                assertEquals(appEvent.getState(), Lifecycle.STARTING, "events="+events);
            }});


        // Remove the listener; will get no more notifications
        listener.clearEvents();
        mgmt.getUsageManager().removeUsageListener(listener);
        
        app.start(ImmutableList.<Location>of());
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = listener.getLocationEvents();
                assertEquals(events.size(), 0, "events="+events);
            }});
    }

    @Test
    public void testAddAndRemoveUsageListener() throws Exception {
        final RecordingUsageListener listener = new RecordingUsageListener();
        mgmt.getUsageManager().addUsageListener(listener);
        
        app = TestApplication.Factory.newManagedInstanceForTests(mgmt);
        app.setCatalogItemId("testCatalogItem");
        app.start(ImmutableList.<Location>of());

        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = listener.getApplicationEvents();
                assertEquals(events.size(), 2, "events="+events); // expect STARTING and RUNNING
                ApplicationMetadata appMetadata = (ApplicationMetadata) events.get(0).get(1);
                ApplicationEvent appEvent = (ApplicationEvent) events.get(0).get(2);
                
                assertEquals(appMetadata.getApplication(), app, "events="+events);
                assertTrue(appMetadata.getApplication() instanceof EntityProxy, "events="+events);
                assertEquals(appMetadata.getApplicationId(), app.getId(), "events="+events);
                assertNotNull(appMetadata.getApplicationName(), "events="+events);
                assertEquals(appMetadata.getCatalogItemId(), app.getCatalogItemId(), "events="+events);
                assertNotNull(appMetadata.getEntityType(), "events="+events);
                assertNotNull(appMetadata.getMetadata(), "events="+events);
                assertEquals(appEvent.getState(), Lifecycle.STARTING, "events="+events);
            }});


        // Remove the listener; will get no more notifications
        listener.clearEvents();
        mgmt.getUsageManager().removeUsageListener(listener);
        
        app.start(ImmutableList.<Location>of());
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = listener.getLocationEvents();
                assertEquals(events.size(), 0, "events="+events);
            }});
    }
    
    @Test
    public void testUsageIncludesStartAndStopEvents() {
        // Start event
        long preStart = System.currentTimeMillis();
        app = TestApplication.Factory.newManagedInstanceForTests(mgmt);
        app.start(ImmutableList.<Location>of());
        long postStart = System.currentTimeMillis();

        Set<ApplicationUsage> usages1 = mgmt.getUsageManager().getApplicationUsage(Predicates.alwaysTrue());
        ApplicationUsage usage1 = Iterables.getOnlyElement(usages1);
        assertApplicationUsage(usage1, app);
        assertApplicationEvent(usage1.getEvents().get(0), Lifecycle.STARTING, preStart, postStart);
        assertApplicationEvent(usage1.getEvents().get(1), Lifecycle.RUNNING, preStart, postStart);

        // Stop events
        long preStop = System.currentTimeMillis();
        app.stop();
        long postStop = System.currentTimeMillis();

        Set<ApplicationUsage> usages2 = mgmt.getUsageManager().getApplicationUsage(Predicates.alwaysTrue());
        ApplicationUsage usage2 = Iterables.getOnlyElement(usages2);
        assertApplicationUsage(usage2, app);
        assertApplicationEvent(usage2.getEvents().get(2), Lifecycle.STOPPING, preStop, postStop);
        assertApplicationEvent(usage2.getEvents().get(3), Lifecycle.STOPPED, preStop, postStop);
        //Apps unmanage themselves on stop
        assertApplicationEvent(usage2.getEvents().get(4), Lifecycle.DESTROYED, preStop, postStop);
        
        assertFalse(mgmt.getEntityManager().isManaged(app), "App should already be unmanaged");
        
        Set<ApplicationUsage> usages3 = mgmt.getUsageManager().getApplicationUsage(Predicates.alwaysTrue());
        ApplicationUsage usage3 = Iterables.getOnlyElement(usages3);
        assertApplicationUsage(usage3, app);
        
        assertEquals(usage3.getEvents().size(), 5, "usage="+usage3);
    }
    
    private void assertApplicationUsage(ApplicationUsage usage, Application expectedApp) {
        assertEquals(usage.getApplicationId(), expectedApp.getId());
        assertEquals(usage.getApplicationName(), expectedApp.getDisplayName());
        assertEquals(usage.getEntityType(), expectedApp.getEntityType().getName());
    }
    
    private void assertApplicationEvent(ApplicationEvent event, Lifecycle expectedState, long preEvent, long postEvent) {
        // Saw times differ by 1ms - perhaps different threads calling currentTimeMillis() can get out-of-order times?!
        final int TIMING_GRACE = 5;
        
        assertEquals(event.getState(), expectedState);
        long eventTime = event.getDate().getTime();
        if (eventTime < (preEvent - TIMING_GRACE) || eventTime > (postEvent + TIMING_GRACE)) {
            fail("for "+expectedState+": event=" + Time.makeDateString(eventTime) + "("+eventTime + "); "
                    + "pre=" + Time.makeDateString(preEvent) + " ("+preEvent+ "); "
                    + "post=" + Time.makeDateString(postEvent) + " ("+postEvent + ")");
        }
    }
}
