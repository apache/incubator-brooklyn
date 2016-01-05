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
package org.apache.brooklyn.core.location.geo;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.location.geo.HostGeoInfo;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

public class HostGeoInfoTest {
    private static final String IP = "192.168.0.1";
    
    private static final Location CUSTOM_LOCATION = new SimulatedLocation(MutableMap.of("name", "custom", "latitude", 50d, "longitude", 0d));
    private static final Location CUSTOM_LOCATION_CHILD = new SimulatedLocation(MutableMap.of("name", "custom-child", "address", IP, "parentLocation", CUSTOM_LOCATION));
        
    @Test
    public void testCustomLocationCoordinates() {
        HostGeoInfo hgi = HostGeoInfo.fromLocation(CUSTOM_LOCATION);
        assertNotNull(hgi);
        assertEquals(50.0d, hgi.latitude);
        assertEquals(0.0d, hgi.longitude);
    }
    
    @Test
    public void testCustomLocationChildCoordinates() {
        HostGeoInfo hgi = HostGeoInfo.fromLocation(CUSTOM_LOCATION_CHILD);
        assertNotNull(hgi);
        assertEquals(50.0d, hgi.latitude);
        assertEquals(0.0d, hgi.longitude);
    }
    
}
