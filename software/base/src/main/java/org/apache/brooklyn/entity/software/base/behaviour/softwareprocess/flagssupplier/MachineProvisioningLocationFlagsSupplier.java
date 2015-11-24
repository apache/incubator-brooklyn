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
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class MachineProvisioningLocationFlagsSupplier extends AbstractLocationFlagsSupplier {

    private static final Logger log = LoggerFactory.getLogger(MachineProvisioningLocationFlagsSupplier.class);


    public MachineProvisioningLocationFlagsSupplier(AbstractEntity entity) {
        super(entity);
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
            Collection<Integer> ports = getRequiredOpenPorts();
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

    /** returns the ports that this entity wants to use;
     * default implementation returns {@link SoftwareProcess#REQUIRED_OPEN_LOGIN_PORTS} plus first value
     * for each {@link org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey} config key {@link PortRange}
     * plus any ports defined with a config keys ending in {@code .port}.
     */
    @SuppressWarnings("serial")
    public Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = MutableSet
                .copyOf(entity().getConfig(SoftwareProcessImpl.REQUIRED_OPEN_LOGIN_PORTS));
        Map<ConfigKey<?>, ?> allConfig = ((BrooklynObjectInternal.ConfigurationSupportInternal)entity().config()).getBag().getAllConfigAsConfigKeyMap();
        Set<ConfigKey<?>> configKeys = Sets.newHashSet(allConfig.keySet());
        configKeys.addAll(entity().getEntityType().getConfigKeys());

        /* TODO: This won't work if there's a port collision, which will cause the corresponding port attribute
           to be incremented until a free port is found. In that case the entity will use the free port, but the
           firewall will open the initial port instead. Mostly a problem for SameServerEntity, localhost location.
         */
        for (ConfigKey<?> k: configKeys) {
            if (PortRange.class.isAssignableFrom(k.getType()) || k.getName().matches(".*\\.port")) {
                Object value = ((BrooklynObjectInternal.ConfigurationSupportInternal)entity().config()).get(k);
                Maybe<PortRange> maybePortRange = TypeCoercions.tryCoerce(value, new TypeToken<PortRange>() {
                });
                if (maybePortRange.isPresentAndNonNull()) {
                    PortRange p = maybePortRange.get();
                    if (p != null && !p.isEmpty())
                        ports.add(p.iterator().next());
                }
            }
        }

        log.debug("getRequiredOpenPorts detected default {} for {}", ports, entity());
        return ports;
    }

}
