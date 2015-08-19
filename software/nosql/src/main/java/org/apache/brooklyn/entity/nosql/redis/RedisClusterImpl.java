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
package org.apache.brooklyn.entity.nosql.redis;

import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.entity.core.AbstractEntity;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.entity.lifecycle.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import org.apache.brooklyn.entity.lifecycle.ServiceStateLogic.ServiceProblemsLogic;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.sensor.enricher.Enrichers;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;
import org.apache.brooklyn.util.exceptions.CompoundRuntimeException;
import org.apache.brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class RedisClusterImpl extends AbstractEntity implements RedisCluster {

    private static final AttributeSensor<RedisStore> MASTER = Sensors.newSensor(RedisStore.class, "redis.master");
    private static final AttributeSensor<DynamicCluster> SLAVES = Sensors.newSensor(DynamicCluster.class, "redis.slaves");

    public RedisClusterImpl() {
    }

    @Override
    public RedisStore getMaster() {
        return getAttribute(MASTER);
    }
    
    @Override
    public DynamicCluster getSlaves() {
        return getAttribute(SLAVES);
    }

    @Override
    public void init() {
        super.init();

        RedisStore master = addChild(EntitySpec.create(RedisStore.class));
        setAttribute(MASTER, master);

        DynamicCluster slaves = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(RedisSlave.class).configure(RedisSlave.MASTER, master)));
        setAttribute(SLAVES, slaves);

        addEnricher(Enrichers.builder()
                .propagating(RedisStore.HOSTNAME, RedisStore.ADDRESS, RedisStore.SUBNET_HOSTNAME, RedisStore.SUBNET_ADDRESS, RedisStore.REDIS_PORT)
                .from(master)
                .build());
    }

    @Override
    protected void initEnrichers() {
        super.initEnrichers();
        ServiceStateLogic.newEnricherFromChildrenUp().
            checkChildrenOnly().
            requireUpChildren(QuorumChecks.all()).
            configure(ComputeServiceIndicatorsFromChildrenAndMembers.IGNORE_ENTITIES_WITH_THESE_SERVICE_STATES, ImmutableSet.<Lifecycle>of()).
            addTo(this);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        ServiceProblemsLogic.clearProblemsIndicator(this, START);
        try {
            doStart(locations);
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        } catch (Exception e) {
            ServiceProblemsLogic.updateProblemsIndicator(this, START, "Start failed with error: "+e);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }
    }

    private void doStart(Collection<? extends Location> locations) {
        RedisStore master = getMaster();
        master.invoke(RedisStore.START, ImmutableMap.<String, Object>of("locations", ImmutableList.copyOf(locations))).getUnchecked();

        DynamicCluster slaves = getSlaves();
        slaves.invoke(DynamicCluster.START, ImmutableMap.<String, Object>of("locations", ImmutableList.copyOf(locations))).getUnchecked();
    }

    @Override
    public void stop() {
        ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPING);
        try {
            doStop();
            ServiceStateLogic.setExpectedState(this, Lifecycle.STOPPED);
        } catch (Exception e) {
            ServiceProblemsLogic.updateProblemsIndicator(this, STOP, "Stop failed with error: "+e);
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(e);
        }
    }

    private void doStop() {
        StringBuilder message = new StringBuilder();
        List<Exception> exceptions = Lists.newLinkedList();

        try {
            getSlaves().invoke(DynamicCluster.STOP, ImmutableMap.<String, Object>of()).getUnchecked();
        } catch (Exception e) {
            message.append("Failed to stop Redis slaves");
            exceptions.add(e);
        }

        try {
            getMaster().invoke(RedisStore.STOP, ImmutableMap.<String, Object>of()).getUnchecked();
        } catch (Exception e) {
            message.append((message.length() == 0) ? "Failed to stop Redis master" : " and master");
            exceptions.add(e);
        }

        if (!exceptions.isEmpty()) {
            throw new CompoundRuntimeException(message.toString(), exceptions);
        }
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }
}
