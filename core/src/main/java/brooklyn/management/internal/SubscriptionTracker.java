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
package brooklyn.management.internal;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.SetMultimap;

/**
 * Tracks subscriptions associated that are registered with particular entities. Gives utilities for unsubscribing from all
 * subscriptions on a given entity, etc.
 */
public class SubscriptionTracker {

    // This class is thread-safe. All modifications to subscriptions are synchronized on the 
    // "subscriptions" field. However, calls to alien code (i.e. context.subscribe etc) is
    // done without holding the lock.
    //
    // If two threads do subscribe() and unsubscribeAll() concurrently, then it's non-derministic
    // whether the subscription will be in place at the end (but that's unavoidable). However, it
    // is guaranteed that the internal state of the SubscriptionTracker will be consistent: if
    // the "subscriptions" includes the new subscription then that subscription will really exist,
    // and vice versa.
    
    protected SubscriptionContext context;
    
    private final SetMultimap<Entity, SubscriptionHandle> subscriptions = HashMultimap.create();

    public SubscriptionTracker(SubscriptionContext subscriptionContext) {
        this.context = subscriptionContext;
    }
    
    /** @see SubscriptionContext#subscribe(Entity, Sensor, SensorEventListener) */
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        SubscriptionHandle handle = context.subscribe(producer, sensor, listener);
        synchronized (subscriptions) {
            subscriptions.put(producer, handle);
        }
        return handle;
    }
    
    /** @see SubscriptionContext#subscribeToChildren(Entity, Sensor, SensorEventListener) */
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        SubscriptionHandle handle = context.subscribeToChildren(parent, sensor, listener);
        synchronized (subscriptions) {
            subscriptions.put(parent, handle);
        }
        return handle;
    }

    /**
     * @see SubscriptionContext#subscribeToMembers(Group, Sensor, SensorEventListener)
     */
    public <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        SubscriptionHandle handle = context.subscribeToMembers(parent, sensor, listener);
        synchronized (subscriptions) {
            subscriptions.put(parent, handle);
        }
        return handle;
    }    

    /**
     * Unsubscribes the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    public boolean unsubscribe(Entity producer) {
        Collection<SubscriptionHandle> handles;
        synchronized (subscriptions) {
            handles = subscriptions.removeAll(producer);
        }
        if (handles != null) {
            for (SubscriptionHandle handle : handles) {
                context.unsubscribe(handle);
            }
            return true;
        }
        return false;
    }

    /**
    * Unsubscribes the given producer.
    *
    * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
    */
   public boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
       synchronized (subscriptions) {
           subscriptions.remove(producer, handle);
       }
       return context.unsubscribe(handle);
   }

    /**
    * @return an ordered list of all subscription handles
    */
   public Collection<SubscriptionHandle> getAllSubscriptions() {
       synchronized (subscriptions) {
           return ImmutableList.copyOf(subscriptions.values());
       }
   }
   
   public void unsubscribeAll() {
       Collection<SubscriptionHandle> subscriptionsSnapshot;
       synchronized (subscriptions) {
           subscriptionsSnapshot = ImmutableList.copyOf(subscriptions.values());
           subscriptions.clear();
       }
       for (SubscriptionHandle s: subscriptionsSnapshot) {
           context.unsubscribe(s);
       }
   }
}
