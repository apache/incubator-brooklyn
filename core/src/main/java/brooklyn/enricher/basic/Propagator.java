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
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
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
    protected Predicate<Sensor<?>> sensorFilter;

    public Propagator() {
    }

    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        
        this.producer = getConfig(PRODUCER) == null ? entity : getConfig(PRODUCER);
        if (getConfig(PROPAGATING) != null) {
            if (Boolean.TRUE.equals(getConfig(PROPAGATING_ALL)) || getConfig(PROPAGATING_ALL_BUT) != null) {
                throw new IllegalStateException("Propagator enricher "+this+" must not have 'propagating' set at same time as either 'propagatingAll' or 'propagatingAllBut'");
            }
            
            Map<Sensor<?>, Sensor<?>> sensorMappingTemp = Maps.newLinkedHashMap();
            if (getConfig(SENSOR_MAPPING) != null) {
                sensorMappingTemp.putAll(getConfig(SENSOR_MAPPING));
            }
            for (Sensor<?> sensor : getConfig(PROPAGATING)) {
                if (!sensorMappingTemp.containsKey(sensor)) {
                    sensorMappingTemp.put(sensor, sensor);
                }
            }
            this.sensorMapping = ImmutableMap.copyOf(sensorMappingTemp);
            this.propagatingAll = false;
            this.sensorFilter = new Predicate<Sensor<?>>() {
                @Override public boolean apply(Sensor<?> input) {
                    return input != null && sensorMapping.keySet().contains(input);
                }
            };
        } else if (getConfig(PROPAGATING_ALL_BUT) == null) {
            this.sensorMapping = getConfig(SENSOR_MAPPING) == null ? ImmutableMap.<Sensor<?>, Sensor<?>>of() : getConfig(SENSOR_MAPPING);
            this.propagatingAll = Boolean.TRUE.equals(getConfig(PROPAGATING_ALL));
            this.sensorFilter = Predicates.alwaysTrue();
        } else {
            this.sensorMapping = getConfig(SENSOR_MAPPING) == null ? ImmutableMap.<Sensor<?>, Sensor<?>>of() : getConfig(SENSOR_MAPPING);
            this.propagatingAll = true;
            this.sensorFilter = new Predicate<Sensor<?>>() {
                @Override public boolean apply(Sensor<?> input) {
                    Collection<Sensor<?>> exclusions = getConfig(PROPAGATING_ALL_BUT);
                    return input != null && !exclusions.contains(input);
                }
            };
        }
            
        Preconditions.checkState(propagatingAll ^ sensorMapping.size() > 0,
                "Exactly one must be set of propagatingAll (%s, excluding %s), sensorMapping (%s)", propagatingAll, getConfig(PROPAGATING_ALL_BUT), sensorMapping);

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

    /** useful post-addition to emit current values */
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
                if (v != null || includeNullValues) entity.setAttribute(destinationSensor, v);
            }
        }
    }

    private Sensor<?> getDestinationSensor(Sensor<?> sourceSensor) {
        return sensorMapping.containsKey(sourceSensor) ? sensorMapping.get(sourceSensor): sourceSensor;
    }
}
