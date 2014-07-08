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
package brooklyn.entity.basic;

import java.util.Collection;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.trait.Changeable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

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

    AttributeSensor<Collection<Entity>> GROUP_MEMBERS = Sensors.newSensor(
            new TypeToken<Collection<Entity>>() { }, "group.members", "Members of the group");

    ConfigKey<Boolean> MEMBER_DELEGATE_CHILDREN = ConfigKeys.newBooleanConfigKey(
            "group.members.delegate", "Add delegate child entities for members of the group", Boolean.FALSE);

    ConfigKey<String> MEMBER_DELEGATE_NAME_FORMAT = ConfigKeys.newStringConfigKey(
            "group.members.delegate.nameFormat", "Delegate members name format string (Use %s for the original entity display name)", "%s");

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
