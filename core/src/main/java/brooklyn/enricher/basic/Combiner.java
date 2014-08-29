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
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
public class Combiner<T,U> extends AbstractEnricher implements SensorEventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Combiner.class);

    public static ConfigKey<Function<?, ?>> TRANSFORMATION = ConfigKeys.newConfigKey(new TypeToken<Function<?, ?>>() {}, "enricher.transformation");

    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");

    public static ConfigKey<Set<Sensor<?>>> SOURCE_SENSORS = ConfigKeys.newConfigKey(new TypeToken<Set<Sensor<?>>>() {}, "enricher.sourceSensors");

    public static ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");

    public static final ConfigKey<Predicate<?>> VALUE_FILTER = ConfigKeys.newConfigKey(new TypeToken<Predicate<?>>() {}, "enricher.aggregating.valueFilter");

    protected Function<? super Collection<T>, ? extends U> transformation;
    protected Entity producer;
    protected Set<Sensor<T>> sourceSensors;
    protected Sensor<U> targetSensor;
    protected Predicate<? super T> valueFilter;

    /**
     * Users of values should either on it synchronize when iterating over its entries or use
     * copyOfValues to obtain an immutable copy of the map.
     */
    // We use a synchronizedMap over a ConcurrentHashMap for entities that store null values.
    protected final Map<Sensor<T>, T> values = Collections.synchronizedMap(new LinkedHashMap<Sensor<T>, T>());

    public Combiner() {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        this.transformation = (Function<? super Collection<T>, ? extends U>) getRequiredConfig(TRANSFORMATION);
        this.producer = getConfig(PRODUCER) == null ? entity: getConfig(PRODUCER);
        this.sourceSensors = (Set) getRequiredConfig(SOURCE_SENSORS);
        this.targetSensor = (Sensor<U>) getRequiredConfig(TARGET_SENSOR);
        this.valueFilter = (Predicate<? super T>) (getConfig(VALUE_FILTER) == null ? Predicates.alwaysTrue() : getConfig(VALUE_FILTER));
        
        checkState(sourceSensors.size() > 0, "must specify at least one sourceSensor");

        for (Sensor<T> sourceSensor : sourceSensors) {
            subscribe(producer, sourceSensor, this);
        }
        
        for (Sensor<T> sourceSensor : sourceSensors) {
            if (sourceSensor instanceof AttributeSensor) {
                Object value = producer.getAttribute((AttributeSensor<?>)sourceSensor);
                // TODO Aled didn't you write a convenience to "subscribeAndRunIfSet" ? (-Alex)
                //      Unfortunately not yet!
                if (value != null) {
                    onEvent(new BasicSensorEvent(sourceSensor, producer, value));
                }
            }
        }
    }

    @Override
    public void onEvent(SensorEvent<T> event) {
        synchronized (values) {
            values.put(event.getSensor(), event.getValue());
        }
        onUpdated();
    }

    /**
     * Called whenever the values for the set of producers changes (e.g. on an event, or on a member added/removed).
     */
    protected void onUpdated() {
        try {
            emit(targetSensor, compute());
        } catch (Throwable t) {
            LOG.warn("Error calculating and setting combination for enricher "+this, t);
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
}
