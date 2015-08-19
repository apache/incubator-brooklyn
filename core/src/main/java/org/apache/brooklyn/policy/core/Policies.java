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
package org.apache.brooklyn.policy.core;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.internal.EntityLocal;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.policy.core.AbstractPolicy;

import groovy.lang.Closure;

@SuppressWarnings({"rawtypes","unchecked"})
public class Policies {

    public static SensorEventListener listenerFromValueClosure(final Closure code) {
        return new SensorEventListener() {
            @Override
            public void onEvent(SensorEvent event) {
                code.call(event.getValue());
            }
        };
    }
    
    public static <T> Policy newSingleSensorValuePolicy(final Sensor<T> sensor, final Closure code) {
        return new AbstractPolicy() {
            @Override
            public void setEntity(EntityLocal entity) {
                super.setEntity(entity);
                entity.subscribe(entity, sensor, listenerFromValueClosure(code));
            }
        };
    }
    
    public static <S,T> Policy newSingleSensorValuePolicy(final Entity remoteEntity, final Sensor<T> remoteSensor, 
            final Closure code) {
        return new AbstractPolicy() {
            @Override
            public void setEntity(EntityLocal entity) {
                super.setEntity(entity);
                entity.subscribe(remoteEntity, remoteSensor, listenerFromValueClosure(code));
            }
        };
    }

    public static Lifecycle getPolicyStatus(Policy p) {
        if (p.isRunning()) return Lifecycle.RUNNING;
        if (p.isDestroyed()) return Lifecycle.DESTROYED;
        if (p.isSuspended()) return Lifecycle.STOPPED;
        // TODO could policy be in an error state?
        return Lifecycle.CREATED;        
    }
    
}
