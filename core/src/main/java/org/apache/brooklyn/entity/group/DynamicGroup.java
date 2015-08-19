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

import groovy.lang.Closure;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.effector.core.MethodEffector;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.base.Predicate;
import com.google.common.reflect.TypeToken;

@ImplementedBy(DynamicGroupImpl.class)
public interface DynamicGroup extends AbstractGroup {

    @SuppressWarnings("serial")
    @SetFromFlag("entityFilter")
    ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = ConfigKeys.newConfigKey(new TypeToken<Predicate<? super Entity>>() { },
            "dynamicgroup.entityfilter", "Filter for entities which will automatically be in the group");

    AttributeSensor<Boolean> RUNNING = Sensors.newBooleanSensor(
            "dynamicgroup.running", "Whether the entity is running, and will automatically update group membership");

    MethodEffector<Void> RESCAN_EFFECTOR = new MethodEffector<Void>(DynamicGroup.class, "rescanEntities");

    /**
     * Stops this group.
     * <p>
     * Does not stop any of its members. De-activates the filter and unsubscribes to
     * entity-updates, so the membership of the group will not change.
     *
     * @deprecated since 0.7; no longer supported (was only used in tests, and by classes that
     *             also implemented {@link Startable#stop()}!)
     */
    @Deprecated
    void stop();

    /** Rescans <em>all</em> entities to determine whether they match the filter. */
    @Effector(description = "Rescans all entities to determine whether they match the configured filter.")
    void rescanEntities();

    /** Sets {@link #ENTITY_FILTER}, overriding (and rescanning all) if already set. */
    void setEntityFilter(Predicate<? super Entity> filter);

    /** @deprecated since 0.7.0; use {@link #setEntityFilter(Predicate)} */
    @Deprecated
    void setEntityFilter(Closure<Boolean> filter);

    /** As {@link #addSubscription(Entity, Sensor)} but with an additional filter. */
    <T> void addSubscription(Entity producer, Sensor<T> sensor, Predicate<? super SensorEvent<? super T>> filter);

    /**
     * Indicates an entity and/or sensor this group should monitor
     * <p>
     * Setting either to {@literal null} indicates everything should be monitored. Note that subscriptions
     * do not <em>restrict</em> what can be added, they merely ensure prompt membership checking (via
     * {@link #ENTITY_FILTER}) for those entities so subscribed.
     */
    <T> void addSubscription(Entity producer, Sensor<T> sensor);

    Predicate<? super Entity> entityFilter();

}
