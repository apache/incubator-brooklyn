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
package brooklyn.enricher.basic;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.api.event.Sensor;
import org.apache.brooklyn.api.event.SensorEvent;
import org.apache.brooklyn.api.event.SensorEventListener;
import org.apache.brooklyn.core.config.BrooklynLogging;
import org.apache.brooklyn.core.util.flags.TypeCoercions;
import org.apache.brooklyn.util.collections.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/** Building on {@link AbstractAggregator} for a single source sensor (on multiple children and/or members) */
public abstract class AbstractMultipleSensorAggregator<U> extends AbstractAggregator<Object,U> implements SensorEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMultipleSensorAggregator.class);

    
    /** access via {@link #getValues(Sensor)} */
    private final Map<String, Map<Entity,Object>> values = Collections.synchronizedMap(new LinkedHashMap<String, Map<Entity,Object>>());

    public AbstractMultipleSensorAggregator() {}

    protected abstract Collection<Sensor<?>> getSourceSensors();
    
    @Override
    protected void setEntityLoadingConfig() {
        super.setEntityLoadingConfig();
        Preconditions.checkNotNull(getSourceSensors(), "sourceSensors must be set");
    }
    
    @Override
    protected void setEntityBeforeSubscribingProducerChildrenEvents() {
        BrooklynLogging.log(LOG, BrooklynLogging.levelDebugOrTraceIfReadOnly(producer),
            "{} subscribing to children of {}", this, producer);
        for (Sensor<?> sourceSensor: getSourceSensors()) {
            subscribeToChildren(producer, sourceSensor, this);
        }
    }

    @Override
    protected void addProducerHardcoded(Entity producer) {
        for (Sensor<?> sourceSensor: getSourceSensors()) {
            subscribe(producer, sourceSensor, this);
        }
        onProducerAdded(producer);
    }

    @Override
    protected void addProducerChild(Entity producer) {
        // no `subscribe` call needed here, due to previous subscribeToChildren call
        onProducerAdded(producer);
    }

    @Override
    protected void addProducerMember(Entity producer) {
        addProducerHardcoded(producer);
    }

    @Override
    protected void onProducerAdded(Entity producer) {
        BrooklynLogging.log(LOG, BrooklynLogging.levelDebugOrTraceIfReadOnly(producer),
            "{} listening to {}", this, producer);
        synchronized (values) {
            for (Sensor<?> sensor: getSourceSensors()) {
                Map<Entity,Object> vs = values.get(sensor.getName());
                if (vs==null) {
                    vs = new LinkedHashMap<Entity,Object>();
                    values.put(sensor.getName(), vs);
                }
                
                Object vo = vs.get(producer);
                if (vo==null) {
                    Object initialVal;
                    if (sensor instanceof AttributeSensor) {
                        initialVal = producer.getAttribute((AttributeSensor<?>)sensor);
                    } else {
                        initialVal = null;
                    }
                    vs.put(producer, initialVal != null ? initialVal : defaultMemberValue);
                    // NB: see notes on possible race, in Aggregator#onProducerAdded
                }
                
            }
        }
    }
    
    @Override
    protected void onProducerRemoved(Entity producer) {
        synchronized (values) {
            for (Sensor<?> sensor: getSourceSensors()) {
                Map<Entity,Object> vs = values.get(sensor.getName());
                if (vs!=null)
                    vs.remove(producer);
            }
        }
        onUpdated();
    }

    @Override
    public void onEvent(SensorEvent<Object> event) {
        Entity e = event.getSource();
        synchronized (values) {
            Map<Entity,Object> vs = values.get(event.getSensor().getName());
            if (vs==null) {
                LOG.debug(this+" received event when no entry for sensor ("+event+"); likely just added or removed, and will initialize subsequently if needed");
            } else {
                vs.put(e, event.getValue());
            }
        }
        onUpdated();
    }

    public <T> Map<Entity,T> getValues(Sensor<T> sensor) {
        Map<Entity, T> valuesCopy = copyValues(sensor);
        return coerceValues(valuesCopy, sensor.getType());
    }

    private <T> Map<Entity, T> coerceValues(Map<Entity, T> values, Class<? super T> type) {
        Map<Entity, T> typedValues = MutableMap.of();
        for (Entry<Entity, T> entry : values.entrySet()) {
            @SuppressWarnings("unchecked")
            T typedValue = (T) TypeCoercions.coerce(entry.getValue(), type);
            typedValues.put(entry.getKey(), typedValue);
        }
        return typedValues;
    }

    private <T> Map<Entity, T> copyValues(Sensor<T> sensor) {
        synchronized (values) {
            @SuppressWarnings("unchecked")
            Map<Entity, T> sv = (Map<Entity, T>) values.get(sensor.getName());
            //use MutableMap because of potentially null values
            return MutableMap.copyOf(sv).asUnmodifiable();
        }
    }
    
    @Override
    protected abstract Object compute();
}
