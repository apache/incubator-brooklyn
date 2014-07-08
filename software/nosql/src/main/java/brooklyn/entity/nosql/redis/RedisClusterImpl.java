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

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;

public class RedisClusterImpl extends AbstractEntity implements RedisCluster {

    protected RedisStore master;
    protected DynamicCluster slaves;

    public RedisClusterImpl() {
    }

    @Override
    public RedisStore getMaster() {
        return master;
    }
    
    @Override
    public DynamicCluster getSlaves() {
        return slaves;
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        master = addChild(EntitySpec.create(RedisStore.class));
        Entities.manage(master);
        master.start(locations);

        slaves = addChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(RedisSlave.class).configure(RedisSlave.MASTER, master)));
        slaves.start(locations);

        setAttribute(Startable.SERVICE_UP, calculateServiceUp());
    }

    @Override
    public void stop() {
        if (slaves != null) slaves.stop();
        if (master != null) master.stop();

        setAttribute(Startable.SERVICE_UP, false);
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }

    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : slaves.getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
    }

}
