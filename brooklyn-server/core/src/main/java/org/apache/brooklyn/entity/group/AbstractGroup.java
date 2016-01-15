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
package org.apache.brooklyn.entity.group;

import java.util.Collection;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import org.apache.brooklyn.core.entity.trait.Changeable;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.util.collections.QuorumCheck;
import org.apache.brooklyn.util.collections.QuorumCheck.QuorumChecks;

import com.google.common.base.Predicate;
import com.google.common.reflect.TypeToken;

/**
 * Represents a group of entities - sub-classes can support dynamically changing membership, 
 * ad hoc groupings, etc.
 * <p> 
 * Synchronization model. When changing and reading the group membership, this class uses internal 
 * synchronization to ensure atomic operations and the "happens-before" relationship for reads/updates
 * from different threads. Sub-classes should not use this same synchronization mutex when doing 
 * expensive operations - e.g. if resizing a cluster, don't block everyone else from asking for the
 * current number of members.
 */
public interface AbstractGroup extends Entity, Group, Changeable {

    @SuppressWarnings("serial")
    AttributeSensor<Collection<Entity>> GROUP_MEMBERS = Sensors.newSensor(
            new TypeToken<Collection<Entity>>() { }, "group.members", "Members of the group");

    // FIXME should definitely remove this, it is ambiguous if an entity is in multiple clusters.  also should be "is_first" or something to indicate boolean.
    AttributeSensor<Boolean> FIRST_MEMBER = Sensors.newBooleanSensor(
            "cluster.first", "Set on an entity if it is the first member of a cluster");

    // FIXME can we remove this too?
    AttributeSensor<Entity> FIRST = Sensors.newSensor(Entity.class,
            "cluster.first.entity", "The first member of the cluster");

    ConfigKey<Boolean> MEMBER_DELEGATE_CHILDREN = ConfigKeys.newBooleanConfigKey(
            "group.members.delegate", "Add delegate child entities for members of the group", Boolean.FALSE);

    ConfigKey<String> MEMBER_DELEGATE_NAME_FORMAT = ConfigKeys.newStringConfigKey(
            "group.members.delegate.nameFormat", "Delegate members name format string (Use %s for the original entity display name)", "%s");

    public static final ConfigKey<QuorumCheck> UP_QUORUM_CHECK = ConfigKeys.newConfigKeyWithDefault(ComputeServiceIndicatorsFromChildrenAndMembers.UP_QUORUM_CHECK, 
        "Up check, applied by default to members, requiring at least one present and up",
        QuorumChecks.atLeastOne());
    public static final ConfigKey<QuorumCheck> RUNNING_QUORUM_CHECK = ConfigKeys.newConfigKeyWithDefault(ComputeServiceIndicatorsFromChildrenAndMembers.RUNNING_QUORUM_CHECK,
        "Problems check from children actual states (lifecycle), applied by default to members and children, not checking upness, but requiring by default that none are on-fire",
        QuorumChecks.all());

    void setMembers(Collection<Entity> m);

    /**
     * Removes any existing members that do not match the given filter, and adds those entities in
     * the given collection that match the predicate.
     * 
     * @param mm     Entities to test against the filter, and to add 
     * @param filter Filter for entities that are to be members (or null for "all")
     */
    // FIXME Do we really want this method? "setMembers" is a misleading name
    void setMembers(Collection<Entity> mm, Predicate<Entity> filter);

}
