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
package org.apache.brooklyn.enricher.stock;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.BrooklynLogging;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.enricher.stock.Enrichers.ComputingAverage;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.StringPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

/** Building on {@link AbstractAggregator} for a single source sensor (on multiple children and/or members) */
@SuppressWarnings("serial")
//@Catalog(name="Aggregator", description="Aggregates attributes from multiple entities into a single attribute value; see Enrichers.builder().aggregating(...)")
public class Aggregator<T,U> extends AbstractAggregator<T,U> implements SensorEventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Aggregator.class);

    public static final ConfigKey<Sensor<?>> SOURCE_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.sourceSensor");
    
    @SetFromFlag("transformation")
    public static final ConfigKey<Object> TRANSFORMATION_UNTYPED = ConfigKeys.newConfigKey(Object.class, "enricher.transformation.untyped",
        "Specifies a transformation, as a function from a collection to the value, or as a string matching a pre-defined named transformation, "
        + "such as 'average' (for numbers), 'sum' (for numbers), or 'list' (the default, putting any collection of items into a list)");
    public static final ConfigKey<Function<? super Collection<?>, ?>> TRANSFORMATION = ConfigKeys.newConfigKey(new TypeToken<Function<? super Collection<?>, ?>>() {}, "enricher.transformation");
    
    public static final ConfigKey<Boolean> EXCLUDE_BLANK = ConfigKeys.newBooleanConfigKey("enricher.aggregator.excludeBlank", "Whether explicit nulls or blank strings should be excluded (default false); this only applies if no value filter set", false);

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
        
        this.transformation = (Function<? super Collection<T>, ? extends U>) config().get(TRANSFORMATION);
        
        Object t1 = config().get(TRANSFORMATION_UNTYPED);
        Function<? super Collection<?>, ?> t2 = null;
        if (t1 instanceof String) {
            t2 = lookupTransformation((String)t1);
            if (t2==null) {
                LOG.warn("Unknown transformation '"+t1+"' for "+this+"; will use default transformation");
            }
        }
        
        if (this.transformation==null) {
            this.transformation = (Function<? super Collection<T>, ? extends U>) t2;
        } else if (t1!=null && !Objects.equals(t2, this.transformation)) {
            throw new IllegalStateException("Cannot supply both "+TRANSFORMATION_UNTYPED+" and "+TRANSFORMATION+" unless they are equal.");
        }
    }
        
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Function<? super Collection<?>, ?> lookupTransformation(String t1) {
        if ("average".equalsIgnoreCase(t1)) return new Enrichers.ComputingAverage(null, null, targetSensor.getTypeToken());
        if ("sum".equalsIgnoreCase(t1)) return new Enrichers.ComputingAverage(null, null, targetSensor.getTypeToken());
        if ("list".equalsIgnoreCase(t1)) return new ComputingList();
        return null;
    }

    private class ComputingList<TT> implements Function<Collection<TT>, List<TT>> {
        @Override
        public List<TT> apply(Collection<TT> input) {
            if (input==null) return null;
            return MutableList.copyOf(input).asUnmodifiable();
        }
        
    }
    
    @Override
    protected void setEntityBeforeSubscribingProducerChildrenEvents() {
        BrooklynLogging.log(LOG, BrooklynLogging.levelDebugOrTraceIfReadOnly(producer),
            "{} subscribing to children of {}", this, producer);
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
        BrooklynLogging.log(LOG, BrooklynLogging.levelDebugOrTraceIfReadOnly(producer),
            "{} listening to {}", this, producer);
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
    protected Predicate<?> getDefaultValueFilter() {
        if (getConfig(EXCLUDE_BLANK))
            return StringPredicates.isNonBlank();
        else
            return Predicates.alwaysTrue();
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
            if (transformation==null) return vs;
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
