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
package brooklyn.management;

import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;

/**
 * The management interface for subscriptions.
 * 
 * Different implementations will handle entities subscribing and unsubscribing to {@link SensorEvent}s
 * and their delivery.
 * 
 * @see SubscriptionContext
 */
public interface SubscriptionManager {
    /**
     * Subscribe to {@link Sensor} data changes and events on a given {@link Entity}, supplying the {@link SensorEventListener}
     * to invoke when they occur.
     * 
     * The method returns an id which can be used to {@link #unsubscribe(SubscriptionHandle)} later.
     * <p>
     * The listener callback is in-order single-threaded and synchronized on this object. The flags
     * parameters can include the following:
     * <ul>
     * <li>subscriber - object to identify the subscriber (e.g. entity, or console session uid) 
     * <li><i>in future</i> - control parameters for the subscription (period, minimum delta for updates, etc)
     * </ul>
     * 
     * @param flags optional parameters to be associated with the subscription
     * @param producer entity to listen to, or null to listen to all entities
     * @param sensor sensor channel of events to listen to, or null for all sensors from the given producer(s)
     * @param listener callback to invoke when an event occurs
     * @return an id for this subscription
     * 
     * @see #unsubscribe(SubscriptionHandle)
     */
    <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);

    /**
     * Subscribe to {@link Sensor} data changes and events on all children of a given {@link Entity}, supplying the
     * {@link SensorEventListener} to invoke when they occur.
     * 
     * The subscriptions will be created recursively for all children, and the same listener callback will be invoked for each
     * sensor datum. The semantics are otherwise identical to the {@link #subscribe(Map, Entity, Sensor, SensorEventListener)} method.
     *
     * @see #subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    <T> SubscriptionHandle subscribeToChildren(Map<String, Object> flags, Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);

    /**
     * Subscribe to {@link Sensor} data changes and events on all members of a given {@link Group}, supplying the
     * {@link SensorEventListener} to invoke when they occur.
     * 
     * The subscriptions will be created recursively for all children, and the same listener callback will be invoked for each
     * sensor datum. The semantics are otherwise identical to the {@link #subscribe(Map, Entity, Sensor, SensorEventListener)} method.
     *
     * @see #subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    <T> SubscriptionHandle subscribeToMembers(Map<String, Object> flags, Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribeToChildren(Map, Group, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener);

    /**
     * Unsubscribe the given subscription id.
     * 
     * @return true if such a subscription was present (and it will now be removed)
     * 
     * @see #subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    boolean unsubscribe(SubscriptionHandle subscriptionId);

    /**
     * Deliver a {@link SensorEvent} to all subscribed listeners.
     */
    <T> void publish(SensorEvent<T> event);

    /** Return the subscriptions requested by a given subscriber */
    Set<SubscriptionHandle> getSubscriptionsForSubscriber(Object subscriber);
    
    /** Return the subscriptions on a given source-sensor pair */
    Set<SubscriptionHandle> getSubscriptionsForEntitySensor(Entity source, Sensor<?> sensor);
}
