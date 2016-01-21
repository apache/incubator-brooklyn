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
package org.apache.brooklyn.core.sensor;

import java.util.Collection;
import java.util.Iterator;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.location.PortSupplier;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.internal.BrooklynInitialization;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

/**
 * A {@link Sensor} describing a port on a system,
 * with a {@link ConfigKey} which can be configured with a port range
 * (either a number e.g. 80, or a string e.g. "80" or "8080-8089" or even "80, 8080-8089, 8800+", or a list of these).
 * <p>
 * To convert at runtime a single port is chosen, respecting the entity.
 */
public class PortAttributeSensorAndConfigKey extends AttributeSensorAndConfigKey<PortRange,Integer> {

    private static final long serialVersionUID = 4680651022807491321L;

    public static final Logger LOG = LoggerFactory.getLogger(PortAttributeSensorAndConfigKey.class);

    static { BrooklynInitialization.initAll(); }

    public PortAttributeSensorAndConfigKey(String name) {
        this(name, name, null);
    }
    public PortAttributeSensorAndConfigKey(String name, String description) {
        this(name, description, null);
    }
    public PortAttributeSensorAndConfigKey(String name, String description, Object defaultValue) {
        super(PortRange.class, Integer.class, name, description, defaultValue);
    }
    public PortAttributeSensorAndConfigKey(PortAttributeSensorAndConfigKey orig, Object defaultValue) {
        super(orig, TypeCoercions.coerce(defaultValue, PortRange.class));
    }
    public PortAttributeSensorAndConfigKey(BasicConfigKey.Builder<PortRange> builder) {
        super(builder, TypeToken.of(Integer.class));
    }
    
    @Override
    protected Integer convertConfigToSensor(PortRange value, Entity entity) {
        if (value==null) return null;
        Collection<? extends Location> locations = entity.getLocations();
        if (!locations.isEmpty()) {
            Maybe<? extends Location> lo = Locations.findUniqueMachineLocation(locations);
            if (!lo.isPresent()) {
                // Try a unique location which isn't a machine provisioner
                Iterator<? extends Location> li = Iterables.filter(locations,
                        Predicates.not(Predicates.instanceOf(MachineProvisioningLocation.class))).iterator();
                if (li.hasNext()) lo = Maybe.of(li.next());
                if (li.hasNext()) lo = Maybe.absent();
            }
            // Fall back to selecting the single location
            if (!lo.isPresent() && locations.size() == 1) {
                lo = Maybe.of(locations.iterator().next());
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Convert config to sensor for {} found locations: {}. Selected: {}", new Object[] {entity, locations, lo});
            }
            if (lo.isPresent()) {
                Location l = lo.get();
                Optional<Boolean> locationRunning = Optional.fromNullable(l.getConfig(BrooklynConfigKeys.SKIP_ENTITY_START_IF_RUNNING));
                Optional<Boolean> entityRunning = Optional.fromNullable(entity.getConfig(BrooklynConfigKeys.SKIP_ENTITY_START_IF_RUNNING));
                Optional<Boolean> locationInstalled = Optional.fromNullable(l.getConfig(BrooklynConfigKeys.SKIP_ENTITY_INSTALLATION));
                Optional<Boolean> entityInstalled = Optional.fromNullable(entity.getConfig(BrooklynConfigKeys.SKIP_ENTITY_INSTALLATION));
                Optional<Boolean> entityStarted = Optional.fromNullable(entity.getConfig(BrooklynConfigKeys.SKIP_ENTITY_START));
                boolean skipCheck = locationRunning.or(entityRunning).or(locationInstalled).or(entityInstalled).or(entityStarted).or(false);
                if (l instanceof PortSupplier) {
                    int p = ((PortSupplier) l).obtainPort(value);
                    if (p != -1) {
                        LOG.debug("{}: choosing port {} for {}", new Object[] { entity, p, getName() });
                        return p;
                    }
                    // If we are not skipping install or already started, fail now
                    if (!skipCheck) {
                        int rangeSize = Iterables.size(value);
                        if (rangeSize == 0) {
                            LOG.warn("{}: no port available for {} (empty range {})", new Object[] { entity, getName(), value });
                        } else if (rangeSize == 1) {
                            Integer pp = value.iterator().next();
                            if (pp > 1024) {
                                LOG.warn("{}: port {} not available for {}", new Object[] { entity, pp, getName() });
                            } else {
                                LOG.warn("{}: port {} not available for {} (root may be required?)", new Object[] { entity, pp, getName() });
                            }
                        } else {
                            LOG.warn("{}: no port available for {} (tried range {})", new Object[] { entity, getName(), value });
                        }
                        return null; // Definitively, no ports available
                    }
                }
                // Ports may be available, we just can't tell from the location
                Integer v = (value.isEmpty() ? null : value.iterator().next());
                LOG.debug("{}: choosing port {} (unconfirmed) for {}", new Object[] { entity, v, getName() });
                return v;
            } else {
                LOG.warn("{}: ports not applicable, or not yet applicable, because has multiple locations {}; ignoring ", new Object[] { entity, locations, getName() });
            }
        } else {
            LOG.warn("{}: ports not applicable, or not yet applicable, bacause has no locations; ignoring {}", entity, getName());
        }
        return null;
    }

    @Override
    protected Integer convertConfigToSensor(PortRange value, ManagementContext managementContext) {
        LOG.warn("ports not applicable, because given managementContext rather than entity; ignoring {}", getName());
        return null;
    }
}
