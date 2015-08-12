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
package brooklyn.entity.basic;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.management.TaskAdaptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.DynamicTasks;

public class SameServerDriverLifecycleEffectorTasks extends MachineLifecycleEffectorTasks {

    private static final Logger LOG = LoggerFactory.getLogger(SameServerDriverLifecycleEffectorTasks.class);

    @Override
    protected SameServerEntityImpl entity() {
        return (SameServerEntityImpl) super.entity();
    }

    /**
     * @return the ports that this entity wants to use, aggregated for all its child entities.
     */
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> result = Sets.newLinkedHashSet();
        result.addAll(getRequiredOpenPorts(entity()));
        LOG.debug("getRequiredOpenPorts detected aggregated default {} for {}", result, this);
        return result;
    }

    /** @return the ports required for a specific child entity */
    protected Collection<Integer> getRequiredOpenPorts(Entity entity) {
        Set<Integer> ports = MutableSet.of(22);
        /* TODO: This won't work if there's a port collision, which will cause the corresponding port attribute
           to be incremented until a free port is found. In that case the entity will use the free port, but the
           firewall will open the initial port instead. Mostly a problem for SameServerEntity, localhost location.
        */
        // TODO: Remove duplication between this and SoftwareProcessImpl.getRequiredOpenPorts
        for (ConfigKey<?> k: entity.getEntityType().getConfigKeys()) {
            Object value;
            if (PortRange.class.isAssignableFrom(k.getType()) || k.getName().matches(".*\\.port")) {
                value = entity.config().get(k);
            } else {
                // config().get() will cause this to block until all config has been resolved
                // using config().getRaw(k) means that we won't be able to use e.g. 'http.port: $brooklyn:component("x").attributeWhenReady("foo")'
                // but that's unlikely to be used
                Maybe<Object> maybeValue = ((AbstractEntity.BasicConfigurationSupport)entity.config()).getRaw(k);
                value = maybeValue.isPresent() ? maybeValue.get() : null;
            }

            Maybe<PortRange> maybePortRange = TypeCoercions.tryCoerce(value, TypeToken.of(PortRange.class));

            if (maybePortRange.isPresentAndNonNull()) {
                PortRange p = maybePortRange.get();
                if (p != null && !p.isEmpty()) ports.add(p.iterator().next());
            }
        }
        LOG.debug("getRequiredOpenPorts detected default {} for {}", ports, entity);

        for (Entity child : entity.getChildren()) {
            ports.addAll(getRequiredOpenPorts(child));
        }
        return ports;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Map<String, Object> obtainProvisioningFlags(MachineProvisioningLocation<?> location) {
        Map<String, Object> result = super.obtainProvisioningFlags(location);
        result.putAll(obtainProvisioningFlags(entity(), location));
        Collection<Integer> ports = getRequiredOpenPorts();

        if (result.containsKey("inboundPorts")) {
            ports.addAll((Collection<Integer>) result.get("inboundPorts"));
        }
        if (!ports.isEmpty()) {
            result.put("inboundPorts", ports);
        }

        result.put(LocationConfigKeys.CALLER_CONTEXT.getName(), entity());
        return result;
    }

    /** @return provisioning flags for the given entity */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Map<String,Object> obtainProvisioningFlags(Entity entity, MachineProvisioningLocation location) {
        Map<String,Object> result = Maps.newLinkedHashMap();
        result.putAll(Maps.newLinkedHashMap(location.getProvisioningFlags(ImmutableList.of(entity.getEntityType().getName()))));
        result.putAll(entity.getConfig(SameServerEntity.PROVISIONING_PROPERTIES));

        for (Entity child : entity.getChildren()) {
            result.putAll(obtainProvisioningFlags(child, location));
        }
        return result;
    }

    @Override
    protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
        DynamicTasks.queueIfPossible(StartableMethods.startingChildren(entity(), machineS.get()))
                .orSubmitAsync(entity())
                .getTask()
                .getUnchecked();
        DynamicTasks.waitForLast();
        return "children started";
    }

    // Also see stopProcessesAtMachine in SoftwareProcessDriverLifecycleEffectorTasks.
    // Any fixes made there should probably be applied here too.
    @Override
    protected String stopProcessesAtMachine() {
        TaskAdaptable<?> children = StartableMethods.stoppingChildren(entity());
        DynamicTasks.queue(children);
        Exception childException = null;
        try {
            DynamicTasks.waitForLast();
        } catch (Exception e) {
            childException = e;
        }

        try {
            children.asTask().get();
        } catch (Exception e) {
            childException = e;
            LOG.debug("Error stopping children; continuing and will rethrow if no other errors", e);
        }

        if (childException != null)
            throw new IllegalStateException("error stopping child", childException);

        return "children stopped";
    }

}
