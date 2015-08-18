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
package org.apache.brooklyn.entity.software.base.test.group;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.core.EntityPredicates;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.group.DynamicClusterWithAvailabilityZonesTest;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.basic.MultiLocation;
import org.apache.brooklyn.test.Asserts;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Uses {@link SoftwareProcess}, so test can't be in core project.
 * 
 * Different from {@link DynamicClusterWithAvailabilityZonesTest} in the use of {@link MultiLocation}.
 * However, the difference is important: the {@link SoftwareProcess} entity has two locations
 * (the {@link MachineProvisioningLocation} and the {@link MachineLocation}, which was previously
 * causing a failure - now fixed and tested here.
 */
public class DynamicClusterWithAvailabilityZonesMultiLocationTest extends BrooklynAppUnitTestSupport {
    
    private DynamicCluster cluster;
    
    private LocalhostMachineProvisioningLocation subLoc1;
    private LocalhostMachineProvisioningLocation subLoc2;
    private MultiLocation<?> multiLoc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.ENABLE_AVAILABILITY_ZONES, true)
                .configure(DynamicCluster.INITIAL_SIZE, 0)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));
        
        subLoc1 = app.newLocalhostProvisioningLocation(ImmutableMap.of("displayName", "loc1"));
        subLoc2 = app.newLocalhostProvisioningLocation(ImmutableMap.of("displayName", "loc2"));
        multiLoc = mgmt.getLocationManager().createLocation(LocationSpec.create(MultiLocation.class)
                .configure(MultiLocation.SUB_LOCATIONS, ImmutableList.<MachineProvisioningLocation<?>>of(subLoc1, subLoc2)));
    }

    @Test
    public void testReplacesEntityInSameZone() throws Exception {
        ((EntityLocal)cluster).config().set(DynamicCluster.ENABLE_AVAILABILITY_ZONES, true);
        cluster.start(ImmutableList.of(multiLoc));
        
        cluster.resize(4);
        List<String> locsUsed = getLocationNames(getLocationsOf(cluster.getMembers(), Predicates.instanceOf(MachineProvisioningLocation.class)));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of("loc1", "loc1", "loc2", "loc2"));

        String idToRemove = Iterables.getFirst(cluster.getMembers(), null).getId();
        String idAdded = cluster.replaceMember(idToRemove);
        locsUsed = getLocationNames(getLocationsOf(cluster.getMembers(), Predicates.instanceOf(MachineProvisioningLocation.class)));
        Asserts.assertEqualsIgnoringOrder(locsUsed, ImmutableList.of("loc1", "loc1", "loc2", "loc2"));
        assertNull(Iterables.find(cluster.getMembers(), EntityPredicates.idEqualTo(idToRemove), null));
        assertNotNull(Iterables.find(cluster.getMembers(), EntityPredicates.idEqualTo(idAdded), null));
    }
    
    protected List<Location> getLocationsOf(Iterable<? extends Entity> entities, Predicate<? super Location> filter) {
        List<Location> result = Lists.newArrayList();
        for (Entity entity : entities) {
            Iterables.addAll(result, Iterables.filter(entity.getLocations(), filter));
        }
        return result;
    }

    protected List<String> getLocationNames(Iterable<? extends Location> locs) {
        List<String> result = Lists.newArrayList();
        for (Location subLoc : locs) {
            result.add(subLoc.getDisplayName());
        }
        return result;
    }
}
