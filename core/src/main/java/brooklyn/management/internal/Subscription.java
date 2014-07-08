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

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionHandle;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Predicate;

class Subscription<T> implements SubscriptionHandle {
    public final String id = Identifiers.makeRandomId(8);
    
    public Object subscriber;
    public Object subscriberExecutionManagerTag;
    /** whether the tag was supplied by user, in which case we should not clear execution semantics */
    public boolean subscriberExecutionManagerTagSupplied;
    public final Entity producer;
    public final Sensor<T> sensor;
    public final SensorEventListener<? super T> listener;
    public Map<String,Object> flags;
    public Predicate<SensorEvent<T>> eventFilter;

    public Subscription(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        this.producer = producer;
        this.sensor = sensor;
        this.listener = listener;
    }
    
    @Override
    public boolean equals(Object other) {
        return (other instanceof Subscription && ((Subscription<?>)other).id==id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return "Subscription["+id+";"+subscriber+"@"+LocalSubscriptionManager.makeEntitySensorToken(producer,sensor)+"]";
    }
}
