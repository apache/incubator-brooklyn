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

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

import com.google.common.reflect.TypeToken;

@ImplementedBy(MockItemEntityImpl.class)
public interface MockItemEntity extends Entity, Movable {

    public static final AttributeSensor<Integer> TEST_METRIC = Sensors.newIntegerSensor(
            "test.metric", "Dummy workrate for test entities");

    public static final AttributeSensor<Map<Entity, Double>> ITEM_USAGE_METRIC = Sensors.newSensor(
            new TypeToken<Map<Entity, Double>>() {}, "test.itemUsage.metric", "Dummy item usage for test entities");

    public boolean isStopped();

    public void moveNonEffector(Entity rawDestination);
    
    public void stop();
}
