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
package org.apache.brooklyn.entity.proxy;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.util.collections.MutableMap;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class StubAppServer extends AbstractEntity implements Startable {
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    public static final PortAttributeSensorAndConfigKey HTTP_PORT = Attributes.HTTP_PORT;
    public static AtomicInteger nextPort = new AtomicInteger(1234);

    public StubAppServer(Map flags) {
        super(flags);
    }
    
    public StubAppServer(Map flags, Entity parent) {
        super(flags, parent);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        Location location = Iterables.getOnlyElement(locations);
        if (location instanceof MachineProvisioningLocation) {
            startInLocation((MachineProvisioningLocation)location);
        } else {
            startInLocation((MachineLocation)location);
        }
    }

    private void startInLocation(MachineProvisioningLocation loc) {
        try {
            startInLocation(loc.obtain(MutableMap.of()));
        } catch (NoMachinesAvailableException e) {
            throw Throwables.propagate(e);
        }
    }
    
    private void startInLocation(MachineLocation loc) {
        addLocations(ImmutableList.of((Location)loc));
        sensors().set(HOSTNAME, loc.getAddress().getHostName());
        sensors().set(HTTP_PORT, nextPort.getAndIncrement());
        sensors().set(SERVICE_UP, true);
    }

    public void stop() {
        sensors().set(SERVICE_UP, false);
    }
    
    @Override
    public void restart() {
    }
}