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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.management.SubscriptionHandle;

import com.google.common.base.Objects;

@SuppressWarnings("rawtypes")
public class QueueingSubscriptionManager extends AbstractSubscriptionManager {

    static class QueuedSubscription<T> {
        Map<String, Object> flags;
        Subscription<T> s;
    }

    public AbstractSubscriptionManager delegate = null;
    public boolean useDelegateForSubscribing = false;
    public boolean useDelegateForPublishing = false;
    
    List<QueuedSubscription> queuedSubscriptions = new ArrayList<QueuedSubscription>();
    List<SensorEvent> queuedSensorEvents = new ArrayList<SensorEvent>();
    
    @Override
    protected synchronized <T> SubscriptionHandle subscribe(Map<String, Object> flags, Subscription<T> s) {
        if (useDelegateForSubscribing)
            return delegate.subscribe(flags, s);
        
        QueuedSubscription<T> qs = new QueuedSubscription<T>();
        qs.flags = flags;
        s.subscriber = getSubscriber(flags, s);
        qs.s = s;
        queuedSubscriptions.add(qs);
        return s;
    }

    @Override
    public synchronized <T> void publish(SensorEvent<T> event) {
        if (useDelegateForPublishing) {
            delegate.publish(event);
            return;
        }
        
        queuedSensorEvents.add(event);
    }

    public void setDelegate(AbstractSubscriptionManager delegate) {
        this.delegate = delegate;
    }
    
    @SuppressWarnings("unchecked")
    public synchronized void startDelegatingForSubscribing() {
        assert delegate!=null;
        for (QueuedSubscription s: queuedSubscriptions) {
            delegate.subscribe(s.flags, s.s);
        }
        queuedSubscriptions.clear();
        useDelegateForSubscribing = true;
    }
    
    @SuppressWarnings("unchecked")
    public synchronized void startDelegatingForPublishing() {
        assert delegate!=null;
        for (SensorEvent evt: queuedSensorEvents) {
            delegate.publish(evt);
        }
        queuedSensorEvents.clear();
        useDelegateForPublishing = true;
    }
    
    public synchronized void stopDelegatingForSubscribing() {
        useDelegateForSubscribing = false;
    }
    
    public synchronized void stopDelegatingForPublishing() {
        useDelegateForPublishing = false;
    }

    @Override
    public synchronized boolean unsubscribe(SubscriptionHandle subscriptionId) {
        if (useDelegateForSubscribing)
            return delegate.unsubscribe(subscriptionId);
        
        Iterator<QueuedSubscription> qi = queuedSubscriptions.iterator();
        while (qi.hasNext()) {
            QueuedSubscription q = qi.next();
            if (Objects.equal(subscriptionId, q.s)) {
                qi.remove();
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public synchronized Set<SubscriptionHandle> getSubscriptionsForSubscriber(Object subscriber) {
        if (useDelegateForSubscribing)
            return delegate.getSubscriptionsForSubscriber(subscriber);
        
        Set<SubscriptionHandle> result = new LinkedHashSet<SubscriptionHandle>();
        for (QueuedSubscription q: queuedSubscriptions) {
            if (Objects.equal(subscriber, getSubscriber(q.flags, q.s))) result.add(q.s);
        }
        return result;
    }

    @Override
    public synchronized Set<SubscriptionHandle> getSubscriptionsForEntitySensor(Entity source, Sensor<?> sensor) {
        if (useDelegateForSubscribing)
            return delegate.getSubscriptionsForEntitySensor(source, sensor);
        
        Set<SubscriptionHandle> result = new LinkedHashSet<SubscriptionHandle>();
        for (QueuedSubscription q: queuedSubscriptions) {
            if ((q.s.sensor==null || Objects.equal(q.s.sensor, sensor)) &&
                    (q.s.producer==null || Objects.equal(q.s.producer, sensor)))
                result.add(q.s);
        }
        return result;
    }

}
