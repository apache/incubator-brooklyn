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
package brooklyn.event.basic;

import java.util.Collection;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.PortRange;
import brooklyn.location.PortSupplier;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.ManagementContext;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

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

    static {
        // ensure type coercions are registered
        PortRanges.init();
    }

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
                LOG.trace("Convert config to sensor for {} found locations: {}. Selected: {}",
                        new Object[] {entity, locations, lo});
            }
            if (lo.isPresent()) {
                Location l = lo.get();
                if (l instanceof PortSupplier) {
                    int p = ((PortSupplier)l).obtainPort(value);
                    if (p!=-1) {
                        LOG.debug(""+entity+" choosing port "+p+" for "+getName());
                        return p;
                    }
                    int rangeSize = Iterables.size(value);
                    if (rangeSize==0)
                        LOG.warn(""+entity+" no port available for "+getName()+" (empty range "+value+")");
                    else if (rangeSize==1) {
                        Integer pp = value.iterator().next();
                        if (pp>1024)
                            LOG.warn(""+entity+" port "+pp+" not available for "+getName());
                        else
                            LOG.warn(""+entity+" port "+pp+" not available for "+getName()+" (root may be required?)");
                    } else {
                        LOG.warn(""+entity+" no port available for "+getName()+" (tried range "+value+")");
                    }
                    // definitively, no ports available
                    return null;
                }
                // ports may be available, we just can't tell from the location
                Integer v = (value.isEmpty() ? null : value.iterator().next());
                LOG.debug(""+entity+" choosing port "+v+" (unconfirmed) for "+getName());
                return v;
            } else {
                LOG.warn(""+entity+" ports not applicable, or not yet applicable, because has multiple locations "+locations+"; ignoring "+getName());
            }
        } else {
            LOG.warn(""+entity+" ports not applicable, or not yet applicable, bacause has no locations; ignoring "+getName());
        }
        return null;
    }
    
    @Override
    protected Integer convertConfigToSensor(PortRange value, ManagementContext managementContext) {
        LOG.warn("ports not applicable, bacause given managementContext rather than entity; ignoring "+getName());
        return null;
    }
}
