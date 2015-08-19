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
package org.apache.brooklyn.entity.software.base.test.core.mgmt.usage;

import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.test.Asserts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.usage.UsageListener;
import org.apache.brooklyn.core.mgmt.usage.UsageManager;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class UsageListenerTest {

    // Also see {Application|Location}UsageTrackingTest for listener functionality
    
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationUsageTrackingTest.class);

    protected TestApplication app;
    protected ManagementContextInternal mgmt;

    protected boolean shouldSkipOnBoxBaseDirResolution() {
        return true;
    }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        RecordingStaticUsageListener.clearInstances();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (mgmt != null) Entities.destroyAll(mgmt);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDown method", t);
        } finally {
            mgmt = null;
            RecordingStaticUsageListener.clearInstances();
        }
    }

    @Test
    public void testAddUsageListenerViaProperties() throws Exception {
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(UsageManager.USAGE_LISTENERS, RecordingStaticUsageListener.class.getName());
        mgmt = LocalManagementContextForTests.newInstance(brooklynProperties);
        
        app = TestApplication.Factory.newManagedInstanceForTests(mgmt);
        app.start(ImmutableList.<Location>of());

        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = RecordingStaticUsageListener.getInstance().getApplicationEvents();
                assertTrue(events.size() > 0, "events="+events); // expect some events
            }});
    }
    
    public static class RecordingStaticUsageListener extends RecordingUsageListener implements UsageListener {
        private static final List<RecordingStaticUsageListener> STATIC_INSTANCES = Lists.newCopyOnWriteArrayList();
        
        public static RecordingStaticUsageListener getInstance() {
            return Iterables.getOnlyElement(STATIC_INSTANCES);
        }

        public static void clearInstances() {
            STATIC_INSTANCES.clear();
        }
        
        public RecordingStaticUsageListener() {
            // Bad to leak a ref to this before constructor finished, but we'll live with it because
            // it's just test code!
            STATIC_INSTANCES.add(this);
        }
    }
}
