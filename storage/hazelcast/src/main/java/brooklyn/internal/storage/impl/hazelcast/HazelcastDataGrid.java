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
package brooklyn.internal.storage.impl.hazelcast;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import brooklyn.entity.Entity;
import brooklyn.internal.storage.DataGrid;
import brooklyn.management.internal.ManagementContextInternal;

import com.google.common.collect.ImmutableMap;
import com.hazelcast.config.Config;
import com.hazelcast.config.SerializerConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;

public class HazelcastDataGrid implements DataGrid {

    private final HazelcastInstance hz;
    private final ManagementContextInternal managementContext;

    public HazelcastDataGrid(ManagementContextInternal managementContext, HazelcastInstance hazelcastInstance) {
        this.managementContext = managementContext;
        if (hazelcastInstance == null) {
            Config config = new Config();
            SerializerConfig entitySerializeConfig = new SerializerConfig();
            entitySerializeConfig.setTypeClassName(Entity.class.getName());
            entitySerializeConfig.setImplementation(new EntityStreamSerializer(this));
            config.getSerializationConfig().addSerializerConfig(entitySerializeConfig);
            this.hz = Hazelcast.newHazelcastInstance(config);
        } else {
            this.hz = hazelcastInstance;
        }
    }

    public ManagementContextInternal getManagementContext() {
        return managementContext;
    }

    @Override
    public <K, V> ConcurrentMap<K, V> getMap(String id) {
        return hz.getMap(id);
    }

    @Override
    public void remove(String id) {
        hz.getMap(id).destroy();
    }

    @Override
    public void terminate() {
        try {
            hz.getLifecycleService().shutdown();
        } catch (HazelcastInstanceNotActiveException ignore) {
        }
    }
    
    @Override
    public Map<String, Object> getDatagridMetrics() {
        // TODO would like to have better metrics
        return ImmutableMap.<String,Object>of("name", hz.getName(), "isRunning", hz.getLifecycleService().isRunning());
    }
    
    @Override
    public Set<String> getKeys() {
        // TODO would like to have all known keys (for tests)
        return Collections.emptySet();
    }
}
