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
package org.apache.brooklyn.entity.software.base.behaviour.softwareprocess.flagssupplier;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;

public class MachineProvisioningLocationFlagsSupplier extends AbstractLocationFlagsSupplier {

    private static final Logger log = LoggerFactory.getLogger(MachineProvisioningLocationFlagsSupplier.class);


    public MachineProvisioningLocationFlagsSupplier(SoftwareProcess entity) {
        super(entity);
    }

    public SoftwareProcessImpl entity(){
        return (SoftwareProcessImpl) super.entity();
    }

    @Override
    public Map<String,Object> obtainFlagsForLocation(Location location){
        return obtainProvisioningFlags((MachineProvisioningLocation)location);
    }

    protected Map<String,Object> obtainProvisioningFlags(MachineProvisioningLocation location) {
        ConfigBag result = ConfigBag.newInstance(location.getProvisioningFlags(ImmutableList
        .of(entity().getClass().getName())));
        result.putAll(entity().getConfig(SoftwareProcessImpl.PROVISIONING_PROPERTIES));
        if (result.get(CloudLocationConfig.INBOUND_PORTS) == null) {
            Collection<Integer> ports = entity().getRequiredOpenPorts();
            Object requiredPorts = result.get(CloudLocationConfig.ADDITIONAL_INBOUND_PORTS);
            if (requiredPorts instanceof Integer) {
                ports.add((Integer) requiredPorts);
            } else if (requiredPorts instanceof Iterable) {
                for (Object o : (Iterable<?>) requiredPorts) {
                    if (o instanceof Integer) ports.add((Integer) o);
                }
            }
            if (ports != null && ports.size() > 0) result.put(CloudLocationConfig.INBOUND_PORTS, ports);
        }
        result.put(LocationConfigKeys.CALLER_CONTEXT, entity());
        return result.getAllConfigMutable();
    }

    @SuppressWarnings("serial")
    public Collection<Integer> getRequiredBehaviorOpenPorts() {
        return ImmutableSet.of();
    }

}
