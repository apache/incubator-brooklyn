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
package org.apache.brooklyn.sensor.enricher;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
//@Catalog(name="Propagator", description="Propagates attributes from one entity to another; see Enrichers.builder().propagating(...)")
public class Propagator extends AbstractEnricher implements SensorEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(Propagator.class);

    public static final Set<Sensor<?>> SENSORS_NOT_USUALLY_PROPAGATED = ImmutableSet.<Sensor<?>>of(
        Attributes.SERVICE_UP, Attributes.SERVICE_NOT_UP_INDICATORS, 
        Attributes.SERVICE_STATE_ACTUAL, Attributes.SERVICE_STATE_EXPECTED, Attributes.SERVICE_PROBLEMS);

    @SetFromFlag("producer")
    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");

    @SetFromFlag("propagatingAllBut")
    public static ConfigKey<Collection<Sensor<?>>> PROPAGATING_ALL_BUT = ConfigKeys.newConfigKey(new TypeToken<Collection<Sensor<?>>>() {}, "enricher.propagating.propagatingAllBut");

    @SetFromFlag("propagatingAll")
    public static ConfigKey<Boolean> PROPAGATING_ALL = ConfigKeys.newBooleanConfigKey("enricher.propagating.propagatingAll");

    @SetFromFlag("propagating")
    public static ConfigKey<Collection<? extends Sensor<?>>> PROPAGATING = ConfigKeys.newConfigKey(new TypeToken<Collection<? extends Sensor<?>>>() {}, "enricher.propagating.inclusions");

    @SetFromFlag("sensorMapping")
    public static ConfigKey<Map<? extends Sensor<?>, ? extends Sensor<?>>> SENSOR_MAPPING = ConfigKeys.newConfigKey(new TypeToken<Map<? extends Sensor<?>, ? extends Sensor<?>>>() {}, "enricher.propagating.sensorMapping");

    protected Entity producer;
    protected Map<? extends Sensor<?>, ? extends Sensor<?>> sensorMapping;
    protected boolean propagatingAll;
    protected Collection<Sensor<?>> propagatingAllBut;
    protected Predicate<Sensor<?>> sensorFilter;

    public Propagator() {
    }

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        
        this.producer = getConfig(PRODUCER) == null ? entity : getConfig(PRODUCER);
        boolean sensorMappingSet = getConfig(SENSOR_MAPPING)!=null;
        MutableMap<Sensor<?>,Sensor<?>> sensorMappingTemp = MutableMap.copyOf(getConfig(SENSOR_MAPPING)); 
        this.propagatingAll = Boolean.TRUE.equals(getConfig(PROPAGATING_ALL)) || getConfig(PROPAGATING_ALL_BUT)!=null;
        
        if (getConfig(PROPAGATING) != null) {
            if (propagatingAll) {
                throw new IllegalStateException("Propagator enricher "+this+" must not have 'propagating' set at same time as either 'propagatingAll' or 'propagatingAllBut'");
            }
            
            for (Object sensorO : getConfig(PROPAGATING)) {
                Sensor<?> sensor = Tasks.resolving(sensorO).as(Sensor.class).timeout(ValueResolver.REAL_QUICK_WAIT).context(producer).get();
                if (!sensorMappingTemp.containsKey(sensor)) {
                    sensorMappingTemp.put(sensor, sensor);
                }
            }
            this.sensorMapping = ImmutableMap.copyOf(sensorMappingTemp);
            this.sensorFilter = new Predicate<Sensor<?>>() {
                @Override public boolean apply(Sensor<?> input) {
                    // TODO kept for deserialization of inner classes, but shouldn't be necessary, as with other inner classes (qv);
                    // NB: previously this did this check:
//                    return input != null && sensorMapping.keySet().contains(input);
                    // but those clauses seems wrong (when would input be null?) and unnecessary (we are doing an explicit subscribe in this code path) 
                    return true;
                }
            };
        } else if (sensorMappingSet) {
            if (propagatingAll) {
                throw new IllegalStateException("Propagator enricher "+this+" must not have 'sensorMapping' set at same time as either 'propagatingAll' or 'propagatingAllBut'");
            }
            this.sensorMapping = ImmutableMap.copyOf(sensorMappingTemp);
            this.sensorFilter = Predicates.alwaysTrue();
        } else {
            this.sensorMapping = ImmutableMap.<Sensor<?>, Sensor<?>>of();
            if (!propagatingAll) {
                // default if nothing specified is to do all but the ones not usually propagated
                propagatingAll = true;
                // user specified nothing, so *set* the all_but to the default set
                // if desired, we could allow this to be dynamically reconfigurable, remove this field and always look up;
                // slight performance hit (always looking up), and might need to recompute subscriptions, so not supported currently
                // TODO this default is @Beta behaviour! -- maybe better to throw?
                propagatingAllBut = SENSORS_NOT_USUALLY_PROPAGATED;
            } else {
                propagatingAllBut = getConfig(PROPAGATING_ALL_BUT);
            }
            this.sensorFilter = new Predicate<Sensor<?>>() {
                @Override public boolean apply(Sensor<?> input) {
                    Collection<Sensor<?>> exclusions = propagatingAllBut;
                    // TODO this anonymous inner class and getConfig check kept should be removed / confirmed for rebind compatibility.
                    // we *should* be regenerating these fields on each rebind (calling to this method), 
                    // so serialization of this class shouldn't be needed (and should be skipped), but that needs to be checked.
                    if (propagatingAllBut==null) exclusions = getConfig(PROPAGATING_ALL_BUT);
                    return input != null && (exclusions==null || !exclusions.contains(input));
                }
            };
        }
            
        Preconditions.checkState(propagatingAll ^ sensorMapping.size() > 0,
                "Nothing to propagate; detected: propagatingAll (%s, excluding %s), sensorMapping (%s)", propagatingAll, getConfig(PROPAGATING_ALL_BUT), sensorMapping);

        if (propagatingAll) {
            subscribe(producer, null, this);
        } else {
            for (Sensor<?> sensor : sensorMapping.keySet()) {
                subscribe(producer, sensor, this);
            }
        }
        
        emitAllAttributes();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void onEvent(SensorEvent<Object> event) {
        // propagate upwards
        Sensor<?> sourceSensor = event.getSensor();
        Sensor<?> destinationSensor = getDestinationSensor(sourceSensor);
        
        if (!sensorFilter.apply(sourceSensor)) {
            return; // ignoring excluded sensor
        }
        
        if (LOG.isTraceEnabled()) LOG.trace("enricher {} got {}, propagating via {}{}", 
                new Object[] {this, event, entity, (sourceSensor == destinationSensor ? "" : " (as "+destinationSensor+")")});
        
        emit((Sensor)destinationSensor, event.getValue());
    }

    /** useful once sensors are added to emit all values */
    public void emitAllAttributes() {
        emitAllAttributes(false);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void emitAllAttributes(boolean includeNullValues) {
        Iterable<? extends Sensor<?>> sensorsToPopulate = propagatingAll 
                ? Iterables.filter(producer.getEntityType().getSensors(), sensorFilter)
                : sensorMapping.keySet();

        for (Sensor<?> s : sensorsToPopulate) {
            if (s instanceof AttributeSensor) {
                AttributeSensor destinationSensor = (AttributeSensor<?>) getDestinationSensor(s);
                Object v = producer.getAttribute((AttributeSensor<?>)s);
                // TODO we should keep a timestamp for the source sensor and echo it 
                // (this pretends timestamps are current, which probably isn't the case when we are propagating)
                if (v != null || includeNullValues) entity.setAttribute(destinationSensor, v);
            }
        }
    }

    private Sensor<?> getDestinationSensor(Sensor<?> sourceSensor) {
        return sensorMapping.containsKey(sourceSensor) ? sensorMapping.get(sourceSensor): sourceSensor;
    }
}
