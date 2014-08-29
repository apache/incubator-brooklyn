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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

/** Building on {@link AbstractAggregator} for a single source sensor (on multiple children and/or members) */
@SuppressWarnings("serial")
public class Aggregator<T,U> extends AbstractAggregator<T,U> implements SensorEventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Aggregator.class);

    public static final ConfigKey<Sensor<?>> SOURCE_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.sourceSensor");
    public static final ConfigKey<Function<? super Collection<?>, ?>> TRANSFORMATION = ConfigKeys.newConfigKey(new TypeToken<Function<? super Collection<?>, ?>>() {}, "enricher.transformation");

    protected Sensor<T> sourceSensor;
    protected Function<? super Collection<T>, ? extends U> transformation;
    
    /**
     * Users of values should either on it synchronize when iterating over its entries or use
     * copyOfValues to obtain an immutable copy of the map.
     */
    // We use a synchronizedMap over a ConcurrentHashMap for entities that store null values.
    protected final Map<Entity, T> values = Collections.synchronizedMap(new LinkedHashMap<Entity, T>());

    public Aggregator() {}

    @SuppressWarnings("unchecked")
    protected void setEntityLoadingConfig() {
        super.setEntityLoadingConfig();
        this.sourceSensor = (Sensor<T>) getRequiredConfig(SOURCE_SENSOR);
        this.transformation = (Function<? super Collection<T>, ? extends U>) getRequiredConfig(TRANSFORMATION);
    }
        
    @Override
    protected void setEntityBeforeSubscribingProducerChildrenEvents() {
        if (LOG.isDebugEnabled()) LOG.debug("{} subscribing to children of {}", new Object[] {this, producer });
        subscribeToChildren(producer, sourceSensor, this);
    }

    @Override
    protected void addProducerHardcoded(Entity producer) {
        subscribe(producer, sourceSensor, this);
        onProducerAdded(producer);
    }

    @Override
    protected void addProducerChild(Entity producer) {
        // no subscription needed here, due to the subscribeToChildren call
        onProducerAdded(producer);
    }

    @Override
    protected void addProducerMember(Entity producer) {
        subscribe(producer, sourceSensor, this);
        onProducerAdded(producer);
    }

    @Override
    protected void onProducerAdded(Entity producer) {
        if (LOG.isDebugEnabled()) LOG.debug("{} listening to {}", new Object[] {this, producer});
        synchronized (values) {
            T vo = values.get(producer);
            if (vo==null) {
                T initialVal;
                if (sourceSensor instanceof AttributeSensor) {
                    initialVal = producer.getAttribute((AttributeSensor<T>)sourceSensor);
                } else {
                    initialVal = null;
                }
                values.put(producer, initialVal != null ? initialVal : defaultMemberValue);
                //we might skip in onEvent in the short window while !values.containsKey(producer)
                //but that's okay because the put which would have been done there is done here now
            } else {
                //vo will be null unless some weird race with addProducer+removeProducer is occuring
                //(and that's something we can tolerate i think)
                if (LOG.isDebugEnabled()) LOG.debug("{} already had value ({}) for producer ({}); but that producer has just been added", new Object[] {this, vo, producer});
            }
        }
    }
    
    @Override
    protected void onProducerRemoved(Entity producer) {
        values.remove(producer);
        onUpdated();
    }

    @Override
    public void onEvent(SensorEvent<T> event) {
        Entity e = event.getSource();
        synchronized (values) {
            if (values.containsKey(e)) {
                values.put(e, event.getValue());
            } else {
                if (LOG.isDebugEnabled()) LOG.debug("{} received event for unknown producer ({}); presumably that producer has recently been removed", this, e);
            }
        }
        onUpdated();
    }

    protected void onUpdated() {
        try {
            emit(targetSensor, compute());
        } catch (Throwable t) {
            LOG.warn("Error calculating and setting aggregate for enricher "+this, t);
            throw Exceptions.propagate(t);
        }
    }
    
    @Override
    protected Object compute() {
        synchronized (values) {
            // TODO Could avoid copying when filter not needed
            List<T> vs = MutableList.copyOf(Iterables.filter(values.values(), valueFilter));
            return transformation.apply(vs);
        }
    }
    
    protected Map<Entity, T> copyOfValues() {
        // Don't use ImmutableMap, as can contain null values
        synchronized (values) {
            return Collections.unmodifiableMap(MutableMap.copyOf(values));
        }
    }

}
