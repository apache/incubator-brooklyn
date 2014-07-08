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
package brooklyn.entity.group;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.reflect.TypeToken;

@Beta
@ImplementedBy(DynamicMultiGroupImpl.class)
@SuppressWarnings("serial")
public interface DynamicMultiGroup extends DynamicGroup {

    /**
     * Implements the mapping from {@link Entity} to bucket name.
     *
     * @see DynamicMultiGroupImpl#bucketFromAttribute(brooklyn.event.AttributeSensor)
     * @see DynamicMultiGroupImpl#bucketFromAttribute(brooklyn.event.AttributeSensor, String)
     */
    @SetFromFlag("bucketFunction")
    ConfigKey<Function<Entity, String>> BUCKET_FUNCTION = ConfigKeys.newConfigKey(
            new TypeToken<Function<Entity, String>>(){},
            "brooklyn.multigroup.bucketFunction",
            "Implements the mapping from entity to bucket (name)"
    );

    /**
     * Determines the type of {@link Group} used for the buckets.
     *
     * @see BasicGroup
     */
    @SetFromFlag("bucketSpec")
    ConfigKey<EntitySpec<? extends BasicGroup>> BUCKET_SPEC = ConfigKeys.newConfigKey(
            new TypeToken<EntitySpec<? extends BasicGroup>>(){},
            "brooklyn.multigroup.groupSpec",
            "Determines the entity type used for the 'bucket' groups",
            EntitySpec.create(BasicGroup.class)
    );


    /**
     * Interval (in seconds) between scans of all entities for membership and distribution into buckets.
     */
    @SetFromFlag("rescanInterval")
    ConfigKey<Long> RESCAN_INTERVAL = ConfigKeys.newLongConfigKey(
            "brooklyn.multigroup.rescanInterval",
            "Interval (in seconds) between scans of all entities for membership. Set to null (default) or zero to disable.");

    /** Notification that a rescan has taken place. */
    AttributeSensor<Void> RESCAN = Sensors.newSensor(Void.class, "brooklyn.multigroup.rescan", "Notification of entity rescan");

    /**
     * Distribute entities accepted by the {@link DynamicGroup#ENTITY_FILTER} into uniquely-named
     * buckets according to the {@link #BUCKET_FUNCTION}.
     * <p>
     * A {@link Group} entity is created for each required bucket and added as a managed child of
     * this component. Entities for a given bucket are added as members of the corresponding group.
     * By default {@link BasicGroup} instances will be created for the buckets, however any group
     * entity can be used instead (e.g. with custom effectors) by specifying the relevant entity
     * spec via the {@link #BUCKET_SPEC} config key.
     * <p>
     * Entities for which the bucket function returns {@code null} are not allocated to any
     * bucket and are thus effectively excluded. Buckets that become empty following re-evaluation
     * are removed.
     *
     * @see #ENTITY_FILTER
     * @see #BUCKET_FUNCTION
     * @see #GROUP_SPEC
     */
    void distributeEntities();

}
