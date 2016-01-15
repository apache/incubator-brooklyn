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
package org.apache.brooklyn.policy.loadbalancing;

import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicNotificationSensor;
import org.apache.brooklyn.entity.group.AbstractGroup;
import org.apache.brooklyn.util.collections.QuorumCheck;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;

/**
 * Contains worker items that can be moved between this container and others to effect load balancing.
 * Membership of a balanceable container does not imply a parent-child relationship in the Brooklyn
 * management sense.
 */
public interface BalanceableContainer<ItemType extends Movable> extends Entity, AbstractGroup {
    
    public static BasicNotificationSensor<Entity> ITEM_ADDED = new BasicNotificationSensor<Entity>(
            Entity.class, "balanceablecontainer.item.added", "Movable item added to balanceable container");
    public static BasicNotificationSensor<Entity> ITEM_REMOVED = new BasicNotificationSensor<Entity>(
            Entity.class, "balanceablecontainer.item.removed", "Movable item removed from balanceable container");
    
    public static final ConfigKey<QuorumCheck> UP_QUORUM_CHECK = ConfigKeys.newConfigKeyWithDefault(AbstractGroup.UP_QUORUM_CHECK, 
        "Up check from members; default one for container overrides usual check to always return true, "
        + "i.e. not block service up simply because the container is empty or something in the container has failed",
        QuorumChecks.alwaysTrue());

    public Set<ItemType> getBalanceableItems();
    
}
