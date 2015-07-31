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
package brooklyn.entity.nosql.redis;

import java.util.Collection;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RedisClusterImpl extends AbstractEntity implements RedisCluster {

    private AttributeSensor<RedisStore> MASTER = Sensors.newSensor(RedisStore.class, "redis.master");
    private AttributeSensor<DynamicCluster> SLAVES = Sensors.newSensor(DynamicCluster.class, "redis.slaves");

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
    public void start(Collection<? extends Location> locations) {
        RedisStore master = getMaster();
        if (master == null) {
            master = addChild(EntitySpec.create(RedisStore.class));
            setAttribute(MASTER, master);
        }

        Entities.manage(master);
        master.invoke(RedisStore.START, ImmutableMap.<String, Object>of("locations", ImmutableList.copyOf(locations))).getUnchecked();

        DynamicCluster slaves = getSlaves();
        if (slaves == null) {
            slaves = addChild(EntitySpec.create(DynamicCluster.class)
                    .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(RedisSlave.class).configure(RedisSlave.MASTER, master)));
            setAttribute(SLAVES, slaves);
        }
        
        slaves.invoke(DynamicCluster.START, ImmutableMap.<String, Object>of("locations", ImmutableList.copyOf(locations))).getUnchecked();

        setAttribute(Startable.SERVICE_UP, calculateServiceUp());

        addEnricher(Enrichers.builder()
                .propagating(RedisStore.HOSTNAME, RedisStore.ADDRESS, RedisStore.SUBNET_HOSTNAME, RedisStore.SUBNET_ADDRESS, RedisStore.REDIS_PORT)
                .from(master)
                .build());
    }

    @Override
    public void stop() {
        DynamicCluster slaves = getSlaves();
        RedisStore master = getMaster(); 
        
        if (slaves != null) slaves.invoke(DynamicCluster.STOP, ImmutableMap.<String, Object>of()).getUnchecked();
        if (master != null) master.invoke(RedisStore.STOP, ImmutableMap.<String, Object>of()).getUnchecked();

        setAttribute(Startable.SERVICE_UP, false);
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }

    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : getSlaves().getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
    }
}
