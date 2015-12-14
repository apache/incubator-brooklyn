/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.AttributeSensorAndConfigKey;

/**
 * Entity that can target another entity for the purpouse of testing
 *
 * @author m4rkmckenna
 */
@ImplementedBy(value = TargetableTestComponentImpl.class)
public interface TargetableTestComponent extends Entity, Startable {

    /**
     * The target entity to test (optional, use either this or targetId).
     */
    AttributeSensorAndConfigKey<Entity, Entity> TARGET_ENTITY = ConfigKeys.newSensorAndConfigKey(Entity.class, "target", "Entity under test");

    /**
     * Id of the target entity to test (optional, use either this or target).
     */
    AttributeSensorAndConfigKey<String, String> TARGET_ID = ConfigKeys.newStringSensorAndConfigKey("targetId", "Id of the entity under test");

    /**
     * Get the target of the test.
     *
     * @return The target.
     * @throws IllegalArgumentException if the target cannot be found.
     */
    Entity resolveTarget();

}
