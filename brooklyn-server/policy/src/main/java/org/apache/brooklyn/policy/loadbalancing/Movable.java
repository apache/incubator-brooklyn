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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.util.core.flags.SetFromFlag;


/**
 * Represents an item that can be migrated between balanceable containers.
 */
public interface Movable extends Entity {
    
    @SetFromFlag("immovable")
    public static ConfigKey<Boolean> IMMOVABLE = new BasicConfigKey<Boolean>(
        Boolean.class, "movable.item.immovable", "Indicates whether this item instance is immovable, so cannot be moved by policies", false);
    
    public static BasicAttributeSensor<BalanceableContainer> CONTAINER = new BasicAttributeSensor<BalanceableContainer>(
        BalanceableContainer.class, "movable.item.container", "The container that this item is on");
    
    public static final MethodEffector<Void> MOVE = new MethodEffector<Void>(Movable.class, "move");
    
    public String getContainerId();
    
    @Effector(description="Moves this entity to the given container")
    public void move(@EffectorParam(name="destination") Entity destination);
    
}
