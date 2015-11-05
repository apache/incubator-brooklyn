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

import com.google.common.collect.Maps;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.time.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A base interface for all tests.
 */
public interface BaseTest extends Entity, Startable {

    /**
     * The target entity to test (optional, use either this or targetId).
     */
    ConfigKey<Entity> TARGET_ENTITY = ConfigKeys.newConfigKey(Entity.class, "target", "Entity under test");

    /**
     * Id of the target entity to test (optional, use either this or target).
     */
    ConfigKey<String> TARGET_ID = ConfigKeys.newStringConfigKey("targetId", "Id of the entity under test");

    /**
     * The assertions to be made
     */
    ConfigKey<Map> ASSERTIONS = ConfigKeys.newConfigKey(Map.class, "assert", "Assertions to be evaluated", Maps.newHashMap());

    /**
     * THe duration to wait
     */
    ConfigKey<Duration> TIMEOUT = ConfigKeys.newConfigKey(Duration.class, "timeout", "Time to wait on result", new Duration(1L, TimeUnit.SECONDS));

    /**
     * Get the target of the test.
     *
     * @return The target.
     * @throws IllegalArgumentException if the target cannot be found.
     */
    Entity resolveTarget();

}
