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
package org.apache.brooklyn.entity.machine.pool;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.location.dynamic.DynamicLocation;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

public class ServerPoolLocation extends AbstractLocation implements MachineProvisioningLocation<MachineLocation>,
        DynamicLocation<ServerPool, ServerPoolLocation> {

    private static final Logger LOG = LoggerFactory.getLogger(ServerPoolLocation.class);

    @SetFromFlag("owner")
    public static final ConfigKey<ServerPool> OWNER = ConfigKeys.newConfigKey(
            ServerPool.class, "pool.location.owner");

    @Override
    public void init() {
        LOG.debug("Initialising. Owner is: {}", checkNotNull(getConfig(OWNER), OWNER.getName()));
        super.init();
    }

    @Override
    public ServerPool getOwner() {
        return getConfig(OWNER);
    }

    @Override
    public MachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
        // Call server pool and try to obtain one of its machines
        return getOwner().claimMachine(flags);
    }

    @Override
    public MachineProvisioningLocation<MachineLocation> newSubLocation(Map<?, ?> newFlags) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void release(MachineLocation machine) {
        getOwner().releaseMachine(machine);
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        return Maps.newLinkedHashMap();
    }
}
