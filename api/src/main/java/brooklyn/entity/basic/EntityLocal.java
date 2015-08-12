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

import java.util.Map;

import org.apache.brooklyn.management.SubscriptionContext;
import org.apache.brooklyn.management.SubscriptionHandle;
import org.apache.brooklyn.management.SubscriptionManager;
import org.apache.brooklyn.management.Task;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.trait.Configurable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.guava.Maybe;

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
     * Sets the {@link AttributeSensor} data for the given attribute to the specified value.
     * 
     * This can be used to "enrich" the entity, such as adding aggregated information, 
     * rolling averages, etc.
     * 
     * @return the old value for the attribute (possibly {@code null})
     */
    <T> T setAttribute(AttributeSensor<T> attribute, T val);

    /**
     * Atomically modifies the {@link AttributeSensor}, ensuring that only one modification is done
     * at a time.
     * 
     * If the modifier returns {@link Maybe#absent()} then the attribute will be
     * left unmodified, and the existing value will be returned.
     * 
     * For details of the synchronization model used to achieve this, refer to the underlying 
     * attribute store (e.g. AttributeMap).
     * 
     * @return the old value for the attribute (possibly {@code null})
     * @since 0.7.0-M2
     */
    @Beta
    <T> T modifyAttribute(AttributeSensor<T> attribute, Function<? super T, Maybe<T>> modifier);

    /**
     * @deprecated in 0.5; use {@link #getConfig(ConfigKey)}
     */
    <T> T getConfig(ConfigKey<T> key, T defaultValue);
    
    /**
     * @deprecated in 0.5; use {@link #getConfig(HasConfigKey)}
     */
    <T> T getConfig(HasConfigKey<T> key, T defaultValue);

    /**
     * Emits a {@link SensorEvent} event on behalf of this entity (as though produced by this entity).
     * <p>
     * Note that for attribute sensors it is nearly always recommended to use setAttribute, 
     * as this method will not update local values.
     */
    <T> void emit(Sensor<T> sensor, T value);
    
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
