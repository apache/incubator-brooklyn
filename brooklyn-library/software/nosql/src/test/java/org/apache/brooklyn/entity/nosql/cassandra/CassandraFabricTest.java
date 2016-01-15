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
package org.apache.brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.software.base.EmptySoftwareProcess;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class CassandraFabricTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(CassandraFabricTest.class);
    
    private LocalhostMachineProvisioningLocation loc1;
    private LocalhostMachineProvisioningLocation loc2;
    private CassandraFabric fabric;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc1 = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        loc2 = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
    }
    
    @Test
    public void testPopulatesInitialSeeds() throws Exception {
        fabric = app.createAndManageChild(EntitySpec.create(CassandraFabric.class)
                .configure(CassandraFabric.INITIAL_QUORUM_SIZE, 2)
                .configure(CassandraDatacenter.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraFabric.MEMBER_SPEC, EntitySpec.create(CassandraDatacenter.class)
                        .configure(CassandraDatacenter.INITIAL_SIZE, 2)
                        .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class))));

        app.start(ImmutableList.of(loc1, loc2));
        CassandraDatacenter d1 = (CassandraDatacenter) Iterables.get(fabric.getMembers(), 0);
        CassandraDatacenter d2 = (CassandraDatacenter) Iterables.get(fabric.getMembers(), 1);

        final EmptySoftwareProcess d1a = (EmptySoftwareProcess) Iterables.get(d1.getMembers(), 0);
        final EmptySoftwareProcess d1b = (EmptySoftwareProcess) Iterables.get(d1.getMembers(), 1);

        final EmptySoftwareProcess d2a = (EmptySoftwareProcess) Iterables.get(d2.getMembers(), 0);
        final EmptySoftwareProcess d2b = (EmptySoftwareProcess) Iterables.get(d2.getMembers(), 1);

        Predicate<Set<Entity>> predicate = new Predicate<Set<Entity>>() {
            @Override public boolean apply(Set<Entity> input) {
                return input != null && input.size() >= 2 &&
                        Sets.intersection(input, ImmutableSet.of(d1a, d1b)).size() == 1 &&
                        Sets.intersection(input, ImmutableSet.of(d2a, d2b)).size() == 1;
            }
        };
        EntityTestUtils.assertAttributeEventually(fabric, CassandraFabric.CURRENT_SEEDS, predicate);
        EntityTestUtils.assertAttributeEventually(d1, CassandraDatacenter.CURRENT_SEEDS, predicate);
        EntityTestUtils.assertAttributeEventually(d2, CassandraDatacenter.CURRENT_SEEDS, predicate);
        
        Set<Entity> seeds = fabric.getAttribute(CassandraFabric.CURRENT_SEEDS);
        assertEquals(d1.getAttribute(CassandraDatacenter.CURRENT_SEEDS), seeds);
        assertEquals(d2.getAttribute(CassandraDatacenter.CURRENT_SEEDS), seeds);
        log.info("Seeds="+seeds);
    }

    @Test
    public void testPopulatesInitialSeedsWhenNodesOfOneClusterComeUpBeforeTheOtherCluster() throws Exception {
        fabric = app.createAndManageChild(EntitySpec.create(CassandraFabric.class)
                .configure(CassandraFabric.INITIAL_QUORUM_SIZE, 2)
                .configure(CassandraDatacenter.DELAY_BEFORE_ADVERTISING_CLUSTER, Duration.ZERO)
                .configure(CassandraFabric.MEMBER_SPEC, EntitySpec.create(CassandraDatacenter.class)
                        .configure(CassandraDatacenter.INITIAL_SIZE, 2)
                        .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(DummyCassandraNode.class))));

        Thread t = new Thread() {
            public void run() {
                app.start(ImmutableList.of(loc1, loc2));
            }
        };
        t.start();
        try {
            EntityTestUtils.assertGroupSizeEqualsEventually(fabric, 2);
            CassandraDatacenter d1 = (CassandraDatacenter) Iterables.get(fabric.getMembers(), 0);
            CassandraDatacenter d2 = (CassandraDatacenter) Iterables.get(fabric.getMembers(), 1);
    
            EntityTestUtils.assertGroupSizeEqualsEventually(d1, 2);
            final DummyCassandraNode d1a = (DummyCassandraNode) Iterables.get(d1.getMembers(), 0);
            final DummyCassandraNode d1b = (DummyCassandraNode) Iterables.get(d1.getMembers(), 1);
    
            EntityTestUtils.assertGroupSizeEqualsEventually(d2, 2);
            final DummyCassandraNode d2a = (DummyCassandraNode) Iterables.get(d2.getMembers(), 0);
            final DummyCassandraNode d2b = (DummyCassandraNode) Iterables.get(d2.getMembers(), 1);

            d1a.sensors().set(Attributes.HOSTNAME, "d1a");
            d1b.sensors().set(Attributes.HOSTNAME, "d1b");
            
            Thread.sleep(1000);
            d2a.sensors().set(Attributes.HOSTNAME, "d2a");
            d2b.sensors().set(Attributes.HOSTNAME, "d2b");
            
            Predicate<Set<Entity>> predicate = new Predicate<Set<Entity>>() {
                @Override public boolean apply(Set<Entity> input) {
                    return input != null && input.size() >= 2 &&
                            Sets.intersection(input, ImmutableSet.of(d1a, d1b)).size() == 1 &&
                            Sets.intersection(input, ImmutableSet.of(d2a, d2b)).size() == 1;
                }
            };
            EntityTestUtils.assertAttributeEventually(fabric, CassandraFabric.CURRENT_SEEDS, predicate);
            EntityTestUtils.assertAttributeEventually(d1, CassandraDatacenter.CURRENT_SEEDS, predicate);
            EntityTestUtils.assertAttributeEventually(d2, CassandraDatacenter.CURRENT_SEEDS, predicate);
            
            Set<Entity> seeds = fabric.getAttribute(CassandraFabric.CURRENT_SEEDS);
            assertEquals(d1.getAttribute(CassandraDatacenter.CURRENT_SEEDS), seeds);
            assertEquals(d2.getAttribute(CassandraDatacenter.CURRENT_SEEDS), seeds);
            log.info("Seeds="+seeds);
        } finally {
            log.info("Failed seeds; fabric="+fabric.getAttribute(CassandraFabric.CURRENT_SEEDS));
            t.interrupt();
        }
    }
    
    
    @ImplementedBy(DummyCassandraNodeImpl.class)
    public interface DummyCassandraNode extends Entity, Startable, EntityLocal, EntityInternal {
    }
    
    public static class DummyCassandraNodeImpl extends AbstractEntity implements DummyCassandraNode {

        @Override
        public void start(Collection<? extends Location> locations) {
            ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        }

        @Override
        public void stop() {
            ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        }

        @Override
        public void restart() {
        }
    }
}
