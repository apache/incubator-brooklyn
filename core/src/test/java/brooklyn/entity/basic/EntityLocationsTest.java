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
package brooklyn.entity.basic;

import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;

public class EntityLocationsTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testDuplicateLocationOnlyAddedOnce() {
        Location l = new SimulatedLocation();
        app.addLocations(Arrays.asList(l, l));
        app.addLocations(Arrays.asList(l, l));
        Assert.assertEquals(app.getLocations().size(), 1);
    }
    
}
