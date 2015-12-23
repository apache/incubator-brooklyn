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
package org.apache.brooklyn.core.mgmt.rebind;

import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestApplicationNoEnrichersImpl;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.testng.annotations.Test;

public class RebindManagerExceptionHandlerTest extends RebindTestFixtureWithApp {

    @Test(expectedExceptions = {IllegalStateException.class, IllegalArgumentException.class})
    public void testAddConfigFailure() throws Throwable {
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure("test.confMapThing", ImmutableMap.of("keyWithMapValue", ImmutableMap.of("minRam", 4))));

        try {
            RebindTestUtils.waitForPersisted(origApp);
            RebindOptions rebindOptions = RebindOptions.create();
            // Use the original context with empty properties so the test doesn't depend on the local properties file
            rebindOptions.newManagementContext = origManagementContext;
            rebind(rebindOptions);
        } catch (Exception e) {
            throw Exceptions.getFirstInteresting(e);
        }
    }

    @Test
    public void testAddConfigContinue() throws Throwable {
        ManagementContext m = createManagementContextWithAddConfigContinue();
        origApp = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class, TestApplicationNoEnrichersImpl.class), m);
        origApp.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure("test.confMapThing", ImmutableMap.of("keyWithMapValue", ImmutableMap.of("minRam", 4))));

        RebindTestUtils.waitForPersisted(origApp);
        RebindOptions rebindOptions = RebindOptions.create();
        rebindOptions.newManagementContext = m;
        TestApplication rebindedApp = rebind(rebindOptions);
        EntityTestUtils.assertConfigEquals(rebindedApp, TestEntity.CONF_MAP_THING, null);
    }

    private LocalManagementContext createManagementContextWithAddConfigContinue() {
        BrooklynProperties bp = BrooklynProperties.Factory.newEmpty();
        bp.putIfAbsent("rebind.failureMode.addConfig", "continue");
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .properties(bp)
                .persistPeriodMillis(getPersistPeriodMillis())
                .forLive(useLiveManagementContext())
                .emptyCatalog(useEmptyCatalog())
                .buildStarted();
    }

    @Override
    protected LocalManagementContext createOrigManagementContext() {
        return RebindTestUtils.managementContextBuilder(mementoDir, classLoader)
                .properties(BrooklynProperties.Factory.newEmpty())
                .persistPeriodMillis(getPersistPeriodMillis())
                .forLive(useLiveManagementContext())
                .emptyCatalog(useEmptyCatalog())
                .buildStarted();
    }
}
