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
package brooklyn.policy.loadbalancing;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(MockContainerEntityImpl.class)
public interface MockContainerEntity extends AbstractGroup, BalanceableContainer<Movable>, Startable {

    @SetFromFlag("membership")
    public static final ConfigKey<String> MOCK_MEMBERSHIP = new BasicConfigKey<String>(
            String.class, "mock.container.membership", "For testing ItemsInContainersGroup");

    @SetFromFlag("delay")
    public static final ConfigKey<Long> DELAY = new BasicConfigKey<Long>(
            Long.class, "mock.container.delay", "", 0L);

    public static final Effector<Void> OFFLOAD_AND_STOP = new MethodEffector<Void>(MockContainerEntity.class, "offloadAndStop");

    public void lock();

    public void unlock();

    public int getWorkrate();

    public Map<Entity, Double> getItemUsage();

    public void addItem(Entity item);

    public void removeItem(Entity item);

    public void offloadAndStop(MockContainerEntity otherContainer);
}
