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
package org.apache.brooklyn.entity.software.base;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;

public class InboundPortsUtils {
    private static final Logger log = LoggerFactory.getLogger(InboundPortsUtils.class);

    /**
     * Returns the required open inbound ports for an Entity.
     * If {@code portsAutoInfer} is {@code true} then
     * return the first value for each {@link org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey}
     * config key {@link PortRange} plus any ports defined with a config key matching the provided regex.
     * @param entity the Entity
     * @param portsAutoInfer if {@code true} then also return the first value for each {@link org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey}
     * config key {@link PortRange} plus any ports defined with a config keys matching the provided regex
     * @param portRegex the regex to match config keys that define inbound ports
     * @return a collection of port numbers
     */
    public static Collection<Integer> getRequiredOpenPorts(Entity entity, Boolean portsAutoInfer, String portRegex) {
        return getRequiredOpenPorts(entity, ImmutableSet.<ConfigKey<?>>of(), portsAutoInfer, portRegex);
    }

    /**
     * Returns the required open inbound ports for an Entity.
     * If {@code portsAutoInfer} is {@code true} then
     * return the first value for each {@link org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey}
     * config key {@link PortRange} plus any ports defined with a config key matching the provided regex.
     * This method also accepts an extra set of config keys in addition to those that are defined in the EntityType of the entity itself.
     * @param entity the Entity
     * @param extraConfigKeys extra set of config key to inspect for inbound ports
     * @param portsAutoInfer if {@code true} then return the first value for each {@link org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey}
     * config key {@link PortRange} plus any ports defined with a config keys matching the provided regex
     * @param portRegex the regex to match config keys that define inbound ports
     * @return a collection of port numbers
     */
    public static Collection<Integer> getRequiredOpenPorts(Entity entity, Set<ConfigKey<?>> extraConfigKeys, Boolean portsAutoInfer, String portRegex) {
        Set<Integer> ports = MutableSet.of();

        /* TODO: This won't work if there's a port collision, which will cause the corresponding port attribute
           to be incremented until a free port is found. In that case the entity will use the free port, but the
           firewall will open the initial port instead. Mostly a problem for SameServerEntity, localhost location.
         */
        if (portsAutoInfer == null || portsAutoInfer.booleanValue()) { // auto-infer defaults to true if not specified
            Set<ConfigKey<?>> configKeys = Sets.newHashSet(extraConfigKeys);
            configKeys.addAll(entity.getEntityType().getConfigKeys());

            if (portRegex == null) portRegex = ".*\\.port"; // defaults to legacy regex if not specified
            Pattern portsPattern = Pattern.compile(portRegex);
            for (ConfigKey<?> k : configKeys) {
                if (PortRange.class.isAssignableFrom(k.getType()) || portsPattern.matcher(k.getName()).matches()) {
                    Object value = entity.config().get(k);
                    Maybe<PortRange> maybePortRange = TypeCoercions.tryCoerce(value, new TypeToken<PortRange>() {
                    });
                    if (maybePortRange.isPresentAndNonNull()) {
                        PortRange p = maybePortRange.get();
                        if (p != null && !p.isEmpty())
                            ports.add(p.iterator().next());
                    }
                }
            }
        }

        log.debug("getRequiredOpenPorts detected default {} for {}", ports, entity);
        return ports;
    }
}
