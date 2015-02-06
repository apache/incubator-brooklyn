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

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/** 
 * an enricher policy which just listens for the target sensor(s) on a child entity and passes it up.
 * now superseded by syntax such as:
 * 
 * <pre>{@code Enrichers.builder().propagating(XXX).from(source).build()}</pre>
 * 
 * @deprecated since 0.7.0; use {@link Enrichers#builder()}
 * 
 * @see Propagator if need to sub-class
 */
public class SensorPropagatingEnricher extends AbstractEnricher implements SensorEventListener<Object> {
    
    public static final Logger log = LoggerFactory.getLogger(SensorPropagatingEnricher.class);
        
    /** the entity to listen to */
    private final Entity source;
    
    /** the sensors to listen to */
    private final Set<Sensor<?>> sensors;

    /** the sensors to listen to */
    private final Map<Sensor<?>, Sensor<?>> sensorMappings;

    public static SensorPropagatingEnricher newInstanceListeningToAllSensors(Entity source) {
        return newInstanceListeningToAllSensorsBut(source);
    }
    public static SensorPropagatingEnricher newInstanceListeningToAllSensorsBut(Entity source, Sensor<?>... excludes) {
        Set<Sensor<?>> excluded = ImmutableSet.copyOf(excludes);
        Set<Sensor<?>> includes = Sets.newLinkedHashSet();
        
        for (Sensor<?> it : source.getEntityType().getSensors()) {
            if (!excluded.contains(it)) includes.add(it);
        }
        return new SensorPropagatingEnricher(source, includes);
    }

    public static SensorPropagatingEnricher newInstanceListeningTo(Entity source, Sensor<?>... includes) {
        return new SensorPropagatingEnricher(source, includes);
    }

    /**
     * listens to sensors from source, propagates them here renamed according to the map
     * 
     * Instead, consider calling:
     * <pre>
     * {@code
     * addEnricher(Enrichers.builder()
     *         .propagating(mapOfOldSensorNamesToNewSensorNames)
     *         .from(source)
     *         .build());
     * }
     * </pre>
     *
     * @deprecated since 0.7.0; use {@link Enrichers#builder()}
     */
    public static SensorPropagatingEnricher newInstanceRenaming(Entity source, Map<? extends Sensor<?>, ? extends Sensor<?>> sensors) {
        return new SensorPropagatingEnricher(source, sensors);
    }

    /**
     * @deprecated since 0.7.0; use {@link Enrichers#builder()}
     */
    public SensorPropagatingEnricher(Entity source, Sensor<?>... sensors) {
        this(source, ImmutableList.copyOf(sensors));
    }
    
    /** 
     * Instead, consider calling:
     * <pre>
     * {@code
     * addEnricher(Enrichers.builder()
     *         .propagating(sensors)
     *         .from(source)
     *         .build());
     * }
     * </pre>
     *
     * @deprecated since 0.7.0; use {@link Enrichers#builder()}
     */
    public SensorPropagatingEnricher(Entity source, Collection<Sensor<?>> sensors) {
        this.source = source;
        this.sensors = ImmutableSet.copyOf(sensors);
        this.sensorMappings = ImmutableMap.of();
    }
    
    public SensorPropagatingEnricher(Entity source, Map<? extends Sensor<?>, ? extends Sensor<?>> sensors) {
        this.source = source;
        this.sensors = ImmutableSet.copyOf(sensors.keySet());
        this.sensorMappings = ImmutableMap.copyOf(sensors);
    }
    
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        for (Sensor<?> s: sensors) {
            subscribe(source, s, this);
        }
    }
    
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void onEvent(SensorEvent<Object> event) {
        // propagate upwards
        Sensor<?> sourceSensor = event.getSensor();
        Sensor<?> destinationSensor = getDestinationSensor(sourceSensor);
        
        if (log.isTraceEnabled()) log.trace("policy {} got {}, propagating via {}{}", 
                new Object[] {this, event, entity, (sourceSensor == destinationSensor ? "" : " (as "+destinationSensor+")")});
        
        if (event.getSensor() instanceof AttributeSensor) {
            entity.setAttribute((AttributeSensor)destinationSensor, event.getValue());
        } else {
            entity.emit((Sensor)destinationSensor, event.getValue());
        }       
    }

    /** useful post-addition to emit current values */
    public void emitAllAttributes() {
        emitAllAttributes(false);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void emitAllAttributes(boolean includeNullValues) {
        for (Sensor s: sensors) {
            if (s instanceof AttributeSensor) {
                AttributeSensor destinationSensor = (AttributeSensor<?>) getDestinationSensor(s);
                Object v = source.getAttribute((AttributeSensor)s);
                if (v != null || includeNullValues) entity.setAttribute(destinationSensor, v);
            }
        }
    }

    /** convenience, to be called by the host */
    public SensorPropagatingEnricher addToEntityAndEmitAll(Entity host) {
        host.addEnricher(this);
        emitAllAttributes();
        return this;
    }
    
    private Sensor<?> getDestinationSensor(Sensor<?> sourceSensor) {
        return sensorMappings.containsKey(sourceSensor) ? sensorMappings.get(sourceSensor): sourceSensor;
    }
}
