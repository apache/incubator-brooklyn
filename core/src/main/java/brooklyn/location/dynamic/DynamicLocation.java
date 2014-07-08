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
package brooklyn.location.dynamic;

import com.google.common.annotations.Beta;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.Location;
import brooklyn.util.flags.SetFromFlag;

/**
 * A location that is created and owned by an entity at runtime.
 * <p>
 * The lifecycle of the location is managed by the owning entity.
 *
 * @param E the entity type
 * @param L the location type
 */
@Beta
public interface DynamicLocation<E extends Entity & LocationOwner<L, E>, L extends Location & DynamicLocation<E, L>> {

    @SetFromFlag("owner")
    ConfigKey<Entity> OWNER =
            ConfigKeys.newConfigKey(Entity.class, "owner", "The entity owning this location");

    @SetFromFlag("maxLocations")
    ConfigKey<Integer> MAX_SUB_LOCATIONS =
            ConfigKeys.newIntegerConfigKey("maxLocations", "The maximum number of sub-locations that can be created; 0 for unlimited", 0);

    E getOwner();

}
