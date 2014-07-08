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
 * This is the context through which an {@link Entity} can manage its subscriptions.
 */
public interface SubscriptionContext {
    /**
     * As {@link SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)} with default subscription parameters for this context
     */
    <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);
    
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToChildren(Map<String, Object> flags, Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
    
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToMembers(Map<String, Object> flags, Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
    
    /** @see SubscriptionManager#unsubscribe(SubscriptionHandle) */
    boolean unsubscribe(SubscriptionHandle subscriptionId);
    
    /** causes all subscriptions to be deregistered
     * @return number of subscriptions removed */
    int unsubscribeAll();

    /** @see SubscriptionManager#publish(SensorEvent) */
    <T> void publish(SensorEvent<T> event);

    /** Return the subscriptions associated with this context */
    Set<SubscriptionHandle> getSubscriptions();
}