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
package brooklyn.policy.basic;

import groovy.lang.Closure;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;

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
