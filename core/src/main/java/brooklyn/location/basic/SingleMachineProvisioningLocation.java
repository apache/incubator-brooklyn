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
package brooklyn.location.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableMap;

public class SingleMachineProvisioningLocation<T extends MachineLocation> extends FixedListMachineProvisioningLocation<T> {
    private static final long serialVersionUID = -4216528515792151062L;

    private static final Logger log = LoggerFactory.getLogger(SingleMachineProvisioningLocation.class);
    
    @SetFromFlag(nullable=false)
    private String location;
    
    @SetFromFlag(nullable=false)
    private Map<?,?> locationFlags;
    
    private T singleLocation;
    private int referenceCount;
    private MachineProvisioningLocation<T> provisioningLocation;


    public SingleMachineProvisioningLocation() {
    }

    @SuppressWarnings("rawtypes")
    public SingleMachineProvisioningLocation(String location, Map locationFlags) {
        this.locationFlags = locationFlags;
        this.location = location;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public synchronized T obtain(Map flags) throws NoMachinesAvailableException {
        log.info("Flags {} passed to newLocationFromString will be ignored, using {}", flags, locationFlags);
        return obtain();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public synchronized T obtain() throws NoMachinesAvailableException {
        if (singleLocation == null) {
            if (provisioningLocation == null) {
                provisioningLocation = (MachineProvisioningLocation) getManagementContext().getLocationRegistry().resolve(
                    location, locationFlags);
            }
            singleLocation = provisioningLocation.obtain(ImmutableMap.of());
            inUse.add(singleLocation);
        }
        referenceCount++;
        return singleLocation;
    }

    @Override
    public synchronized void release(T machine) {
        if (!machine.equals(singleLocation)) {
            throw new IllegalArgumentException("Invalid machine " + machine + " passed to release, expecting: " + singleLocation);
        }
        if (--referenceCount == 0) {
            provisioningLocation.release(machine);
            singleLocation = null;
        }
        inUse.remove(machine);
    };

}
