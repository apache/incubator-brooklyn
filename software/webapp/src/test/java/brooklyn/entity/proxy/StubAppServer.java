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
package brooklyn.entity.proxy;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.util.collections.MutableMap;

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
        setAttribute(HOSTNAME, loc.getAddress().getHostName());
        setAttribute(HTTP_PORT, nextPort.getAndIncrement());
        setAttribute(SERVICE_UP, true);
    }

    public void stop() {
        setAttribute(SERVICE_UP, false);
    }
    
    @Override
    public void restart() {
    }
}