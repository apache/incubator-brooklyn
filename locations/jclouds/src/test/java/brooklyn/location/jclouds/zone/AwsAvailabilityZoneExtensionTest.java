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
package brooklyn.location.jclouds.zone;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.jclouds.domain.LocationScope;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.jclouds.AbstractJcloudsTest;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class AwsAvailabilityZoneExtensionTest {

    public static final String PROVIDER = AbstractJcloudsTest.AWS_EC2_PROVIDER;
    public static final String REGION_NAME = AbstractJcloudsTest.AWS_EC2_USEAST_REGION_NAME;
    public static final String LOCATION_SPEC = PROVIDER + (REGION_NAME == null ? "" : ":" + REGION_NAME);
    public static final String SMALL_HARDWARE_ID = AbstractJcloudsTest.AWS_EC2_SMALL_HARDWARE_ID;
    
    public static final String US_EAST_IMAGE_ID = "us-east-1/ami-7d7bfc14"; // centos 6.3
    
    private LocalManagementContext mgmt;
    private JcloudsLocation loc;
    private AwsAvailabilityZoneExtension zoneExtension;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = new LocalManagementContext();
        loc = (JcloudsLocation) mgmt.getLocationRegistry().resolve(LOCATION_SPEC);
        zoneExtension = new AwsAvailabilityZoneExtension(mgmt, loc);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
    }
    
    @Test(groups={"Live", "Sanity"})
    public void testFindsZones() throws Exception {
        List<Location> subLocations = zoneExtension.getSubLocations(Integer.MAX_VALUE);
        List<String> zoneNames = getRegionsOf(subLocations);
        assertTrue(subLocations.size() >= 3, "zones="+subLocations);
        assertTrue(zoneNames.containsAll(ImmutableList.of(REGION_NAME+"a", REGION_NAME+"b", REGION_NAME+"c")), "zoneNames="+zoneNames);
    }
    
    @Test(groups={"Live", "Sanity"})
    public void testFiltersZones() throws Exception {
        List<Location> subLocations = zoneExtension.getSubLocationsByName(Predicates.containsPattern(REGION_NAME+"[ab]"), Integer.MAX_VALUE);
        List<String> zoneNames = getRegionsOf(subLocations);
        assertTrue(subLocations.size() == 2, "zones="+subLocations);
        assertTrue(zoneNames.containsAll(ImmutableList.of(REGION_NAME+"a", REGION_NAME+"b")), "zoneNames="+zoneNames);
    }
    
    // TODO choosing a specific availability zone looks dangerous!
    // TODO report this on brooklyn issues
    //      org.jclouds.aws.AWSResponseException: request POST https://ec2.us-east-1.amazonaws.com/ HTTP/1.1 failed with code 400, 
    //      error: AWSError{requestId='5d360cc7-9c43-4683-8093-de3b081de87d', requestToken='null', code='Unsupported', 
    //      message='The requested Availability Zone is currently constrained and we are no longer accepting new customer requests for t1/m1/c1/m2/m3 instance types. 
    //              Please retry your request by not specifying an Availability Zone or choosing us-east-1e, us-east-1b, us-east-1c.', context='{Response=, Errors=}'}
    @Test(groups={"Live"})
    public void testSubLocationIsUsable() throws Exception {
        String zoneName = REGION_NAME+"b";
        List<Location> subLocations = zoneExtension.getSubLocationsByName(Predicates.equalTo(zoneName), Integer.MAX_VALUE);
        JcloudsLocation subLocation = (JcloudsLocation) Iterables.getOnlyElement(subLocations);
        JcloudsSshMachineLocation machine = null;
        try {
            machine = subLocation.obtain(ImmutableMap.builder()
                    .put(JcloudsLocation.IMAGE_ID, US_EAST_IMAGE_ID)
                    .put(JcloudsLocation.HARDWARE_ID, SMALL_HARDWARE_ID)
                    .put(JcloudsLocation.INBOUND_PORTS, ImmutableList.of(22))
                    .build());
            
            org.jclouds.domain.Location machineLoc = machine.getNode().getLocation();
            assertEquals(machineLoc.getScope(), LocationScope.ZONE, "machineLoc="+machineLoc);
            assertEquals(machineLoc.getId(), zoneName, "machineLoc="+machineLoc);
        } finally {
            if (machine != null) {
                subLocation.release(machine);
            }
        }
    }
    
    protected List<String> getRegionsOf(List<Location> locs) {
        List<String> result = Lists.newArrayList();
        for (Location loc : locs) {
            result.add(((JcloudsLocation)loc).getRegion());
        }
        return result;
    }
}
