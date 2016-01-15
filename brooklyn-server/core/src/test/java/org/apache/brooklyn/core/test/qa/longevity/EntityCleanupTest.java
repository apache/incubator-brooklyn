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
package org.apache.brooklyn.core.test.qa.longevity;

import org.testng.annotations.Test;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.util.javalang.JavaClassNames;

public class EntityCleanupTest extends EntityCleanupLongevityTestFixture {

    @Override
    protected int numIterations() {
        return 1;
    }

    @Override
    protected boolean checkMemoryLeaks() {
        return false;
    }
    
    @Test
    public void testAppCreatedStartedAndStopped() throws Exception {
        doTestStartAppThenThrowAway(JavaClassNames.niceClassAndMethod(), true);
    }
    
    @Test
    public void testAppCreatedStartedAndUnmanaged() throws Exception {
        doTestStartAppThenThrowAway(JavaClassNames.niceClassAndMethod(), false);
    }

    @Test
    public void testLocationCreatedAndUnmanaged() throws Exception {
        doTestManyTimesAndAssertNoMemoryLeak(JavaClassNames.niceClassAndMethod(), new Runnable() {
            public void run() {
                loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
                managementContext.getLocationManager().unmanage(loc);
            }
        });
    }
    
}
