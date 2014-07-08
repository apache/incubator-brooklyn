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
package brooklyn.qa.longevity;

import org.testng.annotations.Test;

import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.util.javalang.JavaClassNames;

/**
 * This test is NOT definitive because GC is not guaranteed.
 */
public class EntityCleanupLongevityTest extends EntityCleanupLongevityTestFixture {

    @Override
    protected int numIterations() {
        return 10*1000;
    }
    
    @Override
    protected boolean checkMemoryLeaks() {
        return true;
    }

    @Test(groups={"Longevity","Acceptance"})
    public void testAppCreatedStartedAndStopped() throws Exception {
        doTestStartAppThenThrowAway(JavaClassNames.niceClassAndMethod(), true);
    }
    
    @Test(groups={"Longevity","Acceptance"})
    public void testAppCreatedStartedAndUnmanaged() throws Exception {
        doTestStartAppThenThrowAway(JavaClassNames.niceClassAndMethod(), false);
    }

    @Test(groups={"Longevity","Acceptance"})
    public void testLocationCreatedAndUnmanaged() throws Exception {
        doTestManyTimesAndAssertNoMemoryLeak(JavaClassNames.niceClassAndMethod(), new Runnable() {
            public void run() {
                loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
                managementContext.getLocationManager().unmanage(loc);
            }
        });
    }
    
}
