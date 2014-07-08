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

import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Changeable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

public class Aggregator<T,U> extends AbstractEnricher implements SensorEventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Aggregator.class);

    public static final ConfigKey<Function<? super Collection<?>, ?>> TRANSFORMATION = ConfigKeys.newConfigKey(new TypeToken<Function<? super Collection<?>, ?>>() {}, "enricher.transformation");

    public static final ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");

    public static final ConfigKey<Sensor<?>> SOURCE_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.sourceSensor");

    public static final ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");

    public static final ConfigKey<?> DEFAULT_MEMBER_VALUE = ConfigKeys.newConfigKey(Object.class, "enricher.defaultMemberValue");

    public static final ConfigKey<Set<? extends Entity>> FROM_HARDCODED_PRODUCERS = ConfigKeys.newConfigKey(new TypeToken<Set<? extends Entity>>() {}, "enricher.aggregating.fromHardcodedProducers");

    public static final ConfigKey<Boolean> FROM_MEMBERS = ConfigKeys.newBooleanConfigKey("enricher.aggregating.fromMembers");

    public static final ConfigKey<Boolean> FROM_CHILDREN = ConfigKeys.newBooleanConfigKey("enricher.aggregating.fromChildren");

    public static final ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = ConfigKeys.newConfigKey(new TypeToken<Predicate<? super Entity>>() {}, "enricher.aggregating.entityFilter");

    public static final ConfigKey<Predicate<?>> VALUE_FILTER = ConfigKeys.newConfigKey(new TypeToken<Predicate<?>>() {}, "enricher.aggregating.valueFilter");

    protected Function<? super Collection<T>, ? extends U> transformation;
    protected Entity producer;
    protected Sensor<T> sourceSensor;
    protected Sensor<U> targetSensor;
    protected T defaultMemberValue;
    protected Set<? extends Entity> fromHardcodedProducers;
    protected Boolean fromMembers;
    protected Boolean fromChildren;
    protected Predicate<? super Entity> entityFilter;
    protected Predicate<? super T> valueFilter;
    
    /**
     * Users of values should either on it synchronize when iterating over its entries or use
     * copyOfValues to obtain an immutable copy of the map.
     */
    // We use a synchronizedMap over a ConcurrentHashMap for entities that store null values.
    protected final Map<Entity, T> values = Collections.synchronizedMap(new LinkedHashMap<Entity, T>());

    public Aggregator() {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        this.transformation = (Function<? super Collection<T>, ? extends U>) getRequiredConfig(TRANSFORMATION);
        this.producer = getConfig(PRODUCER);
        this.fromHardcodedProducers= getConfig(FROM_HARDCODED_PRODUCERS);
        this.sourceSensor = (Sensor<T>) getRequiredConfig(SOURCE_SENSOR);
        this.targetSensor = (Sensor<U>) getRequiredConfig(TARGET_SENSOR);
        this.defaultMemberValue = (T) getConfig(DEFAULT_MEMBER_VALUE);
        this.fromMembers = getConfig(FROM_MEMBERS);
        this.fromChildren = getConfig(FROM_CHILDREN);
        this.entityFilter = (Predicate<? super Entity>) (getConfig(ENTITY_FILTER) == null ? Predicates.alwaysTrue() : getConfig(ENTITY_FILTER));
        this.valueFilter = (Predicate<? super T>) (getConfig(VALUE_FILTER) == null ? Predicates.alwaysTrue() : getConfig(VALUE_FILTER));

        if (fromHardcodedProducers == null && producer == null) producer = entity;
        checkState(fromHardcodedProducers != null ^ producer != null, "must specify one of %s (%s) or %s (%s)", 
                PRODUCER.getName(), producer, FROM_HARDCODED_PRODUCERS.getName(), fromHardcodedProducers);
        checkState(producer != null ? (Boolean.TRUE.equals(fromMembers) ^ Boolean.TRUE.equals(fromChildren)) : true, 
                "when specifying producer, must specify one of fromMembers (%s) or fromChildren (%s)", fromMembers, fromChildren);

        if (fromHardcodedProducers != null) {
            for (Entity producer : Iterables.filter(fromHardcodedProducers, entityFilter)) {
                addProducer(producer);
            }
            onUpdated();
        }
        
        if (Boolean.TRUE.equals(fromMembers)) {
            checkState(producer instanceof Group, "must be a group when fromMembers true: producer=%s; entity=%s; "
                    + "hardcodedProducers=%s", getConfig(PRODUCER), entity, fromHardcodedProducers);

            subscribe(producer, Changeable.MEMBER_ADDED, new SensorEventListener<Entity>() {
                @Override public void onEvent(SensorEvent<Entity> event) {
                    if (entityFilter.apply(event.getValue())) addProducer(event.getValue());
                }
            });
            subscribe(producer, Changeable.MEMBER_REMOVED, new SensorEventListener<Entity>() {
                @Override public void onEvent(SensorEvent<Entity> event) {
                    removeProducer(event.getValue());
                }
            });
            
            if (producer instanceof Group) {
                for (Entity member : Iterables.filter(((Group)producer).getMembers(), entityFilter)) {
                    addProducer(member);
                }
            }
            onUpdated();
        }
        
        if (Boolean.TRUE.equals(fromChildren)) {
            if (LOG.isDebugEnabled()) LOG.debug("{} linked (children of {}, {}) to {}", new Object[] {this, producer, sourceSensor, targetSensor});
            subscribeToChildren(producer, sourceSensor, this);

            subscribe(producer, AbstractEntity.CHILD_REMOVED, new SensorEventListener<Entity>() {
                @Override public void onEvent(SensorEvent<Entity> event) {
                    onProducerRemoved(event.getValue());
                }
            });
            subscribe(producer, AbstractEntity.CHILD_ADDED, new SensorEventListener<Entity>() {
                @Override public void onEvent(SensorEvent<Entity> event) {
                    if (entityFilter.apply(event.getValue())) onProducerAdded(event.getValue());
                }
            });

            for (Entity child : Iterables.filter(producer.getChildren(), entityFilter)) {
                onProducerAdded(child, false);
            }
            onUpdated();
        }
    }

    protected void addProducer(Entity producer) {
        if (LOG.isDebugEnabled()) LOG.debug("{} linked ({}, {}) to {}", new Object[] {this, producer, sourceSensor, targetSensor});
        subscribe(producer, sourceSensor, this);
        onProducerAdded(producer);
    }
    
    // TODO If producer removed but then get (queued) event from it after this method returns,  
    protected T removeProducer(Entity producer) {
        if (LOG.isDebugEnabled()) LOG.debug("{} unlinked ({}, {}) from {}", new Object[] {this, producer, sourceSensor, targetSensor});
        unsubscribe(producer);
        return onProducerRemoved(producer);
    }

    protected void onProducerAdded(Entity producer) {
        onProducerAdded(producer, true);
    }
    
    protected void onProducerAdded(Entity producer, boolean update) {
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
        if (update) {
            onUpdated();
        }
    }
    
    // TODO If producer removed but then get (queued) event from it after this method returns,  
    protected T onProducerRemoved(Entity producer) {
        T removed = values.remove(producer);
        onUpdated();
        return removed;
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

    /**
     * Called whenever the values for the set of producers changes (e.g. on an event, or on a member added/removed).
     */
    protected void onUpdated() {
        try {
            Object v = compute();
            if (v == Entities.UNCHANGED) {
                // nothing
            } else {
                emit(targetSensor, TypeCoercions.coerce(v, targetSensor.getTypeToken()));
            }
        } catch (Throwable t) {
            LOG.warn("Error calculating and setting aggregate for enricher "+this, t);
            throw Exceptions.propagate(t);
        }
    }
    
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
