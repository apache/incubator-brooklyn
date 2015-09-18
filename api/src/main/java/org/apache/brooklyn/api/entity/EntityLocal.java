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
package org.apache.brooklyn.api.entity;

import java.util.Map;

import org.apache.brooklyn.api.mgmt.SubscriptionContext;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.api.mgmt.SubscriptionManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;

/** 
 * Extended Entity interface for use in places where the caller should have certain privileges,
 * such as setting attribute values, adding policies, etc.
 * 
 * FIXME Moved from core project to api project because of bug in groovy's covariant return types.
 * EntityDriver needs to return EntityLocal rather than Entity, to avoid changing a whole load
 * of sub-types.
 * FIXME Add {@link setAttribute(AttributeSensorAndConfigKey<?,T>)} back in if/when move it back,
 * or if we extract an interface for AttributeSensorAndConfigKey.
 */
public interface EntityLocal extends Entity {
    
    // FIXME Rename to something other than EntityLocal.
    // Separate out what is specific to "local jvm", and what is here for an SPI rather than API.

    /**
     * Sets the entity's display name.
     * Must be called before the entity is managed.
     */
    void setDisplayName(String displayName);

    /**
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code entity.config().set(key, val)}
     */
    @Deprecated
    <T> T setConfig(ConfigKey<T> key, T val);
    
    /**
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code entity.config().set(key, val)}
     */
    @Deprecated
    <T> T setConfig(ConfigKey<T> key, Task<T> val);
    
    /**
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code entity.config().set(key, val)}
     */
    @Deprecated
    <T> T setConfig(HasConfigKey<T> key, T val);
    
    /**
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code entity.config().set(key, val)}
     */
    @Deprecated
    <T> T setConfig(HasConfigKey<T> key, Task<T> val);

    /**
     * @deprecated since 0.8.0; use {@link SensorSupport#set(AttributeSensor, Object)} via code like {@code sensors().set(attribute, val)}.
     */
    <T> T setAttribute(AttributeSensor<T> attribute, T val);

    /**
     * @deprecated since 0.8.0; use {@link SensorSupport#modify(AttributeSensor, Function)} via code like {@code sensors().modify(attribute, modifier)}.
     */
    @Beta
    <T> T modifyAttribute(AttributeSensor<T> attribute, Function<? super T, Maybe<T>> modifier);

    /**
     * @deprecated since 0.8.0; use {@link SensorSupport#emit(Sensor, Object)} via code like {@code sensors().emit(sensor, val)}.
     */
    <T> void emit(Sensor<T> sensor, T value);
    
    /**
     * @deprecated in 0.5; use {@link #getConfig(ConfigKey)}
     */
    <T> T getConfig(ConfigKey<T> key, T defaultValue);
    
    /**
     * @deprecated in 0.5; use {@link #getConfig(HasConfigKey)}
     */
    <T> T getConfig(HasConfigKey<T> key, T defaultValue);

    /**
     * Allow us to subscribe to data from a {@link Sensor} on another entity.
     * 
     * @return a subscription id which can be used to unsubscribe
     *
     * @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    // FIXME remove from interface?
    @Beta
    <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);

    /**
     * Allow us to subscribe to data from a {@link Sensor} on another entity.
     * 
     * @return a subscription id which can be used to unsubscribe
     *
     * @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    // FIXME remove from interface?
    @Beta
    <T> SubscriptionHandle subscribe(Map<String, ?> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);

    /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
    // FIXME remove from interface?
    @Beta
    <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see SubscriptionManager#subscribeToMembers(Group, Sensor, SensorEventListener) */
    // FIXME remove from interface?
    @Beta
    <T> SubscriptionHandle subscribeToMembers(Group group, Sensor<T> sensor, SensorEventListener<? super T> listener);

    /**
     * Unsubscribes from the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    @Beta
    boolean unsubscribe(Entity producer);

    /**
     * Unsubscribes the given handle.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    @Beta
    boolean unsubscribe(Entity producer, SubscriptionHandle handle);

    /**
     * Removes all policy from this entity. 
     * @return True if any policies existed at this entity; false otherwise
     */
    boolean removeAllPolicies();
    
    /**
     * Removes all enricher from this entity.
     * Use with caution as some entities automatically register enrichers; this will remove those enrichers as well.
     * @return True if any enrichers existed at this entity; false otherwise
     */
    boolean removeAllEnrichers();
}
