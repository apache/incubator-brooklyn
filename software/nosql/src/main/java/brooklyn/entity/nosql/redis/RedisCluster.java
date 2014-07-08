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

import brooklyn.catalog.Catalog;
import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;

/**
 * A cluster of {@link RedisStore}s with one master and a group of slaves.
 *
 * The slaves are contained in a {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated Redis statistics from cluster
 */
@Catalog(name="Redis Cluster", description="Redis is an open-source, networked, in-memory, key-value data store with optional durability", iconUrl="classpath:///redis-logo.png")
@ImplementedBy(RedisClusterImpl.class)
public interface RedisCluster extends Entity, Startable {
    
    public RedisStore getMaster();
    
    public DynamicCluster getSlaves();
}
