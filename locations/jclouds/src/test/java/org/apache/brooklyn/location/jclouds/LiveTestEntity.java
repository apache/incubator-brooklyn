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
package org.apache.brooklyn.location.jclouds;

import java.util.Collection;

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.internal.LocationInternal;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

@ImplementedBy(LiveTestEntity.LiveTestEntityImpl.class)
public interface LiveTestEntity extends TestEntity {

    MachineProvisioningLocation<?> getProvisioningLocation();
    JcloudsSshMachineLocation getObtainedLocation();

    public static class LiveTestEntityImpl extends TestEntityImpl implements LiveTestEntity {

        private static final Logger LOG = LoggerFactory.getLogger(LiveTestEntityImpl.class);
        private JcloudsLocation provisioningLocation;
        private JcloudsSshMachineLocation obtainedLocation;

        @Override
        public void start(final Collection<? extends Location> locs) {
            LOG.trace("Starting {}", this);
            callHistory.add("start");
            setAttribute(SERVICE_STATE, Lifecycle.STARTING);
            counter.incrementAndGet();
            addLocations(locs);
            provisioningLocation = (JcloudsLocation) Iterables.find(locs, Predicates.instanceOf(JcloudsLocation.class));
            try {
                obtainedLocation = (JcloudsSshMachineLocation)provisioningLocation.obtain(((LocationInternal)provisioningLocation).config().getBag().getAllConfig());
            } catch (NoMachinesAvailableException e) {
                throw Throwables.propagate(e);
            }
            addLocations(ImmutableList.of(obtainedLocation));
            setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
        }

        @Override
        public void stop() {
            LOG.trace("Stopping {}", this);
            callHistory.add("stop");
            setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
            counter.decrementAndGet();
            if (provisioningLocation != null && obtainedLocation != null) {
                provisioningLocation.release(obtainedLocation);
            }
            setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
        }

        public MachineProvisioningLocation<?> getProvisioningLocation() {
            return provisioningLocation;
        }

        public JcloudsSshMachineLocation getObtainedLocation() {
            return obtainedLocation;
        }
    }

}
