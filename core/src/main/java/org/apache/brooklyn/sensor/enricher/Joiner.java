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

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicSensorEvent;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.text.StringEscapes;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.reflect.TypeToken;

//@Catalog(name="Transformer", description="Transforms attributes of an entity; see Enrichers.builder().transforming(...)")
@SuppressWarnings("serial")
public class Joiner<T> extends AbstractEnricher implements SensorEventListener<T> {

    private static final Logger LOG = LoggerFactory.getLogger(Joiner.class);

    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");
    public static ConfigKey<Sensor<?>> SOURCE_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.sourceSensor");
    public static ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");
    @SetFromFlag("separator")
    public static ConfigKey<String> SEPARATOR = ConfigKeys.newStringConfigKey("enricher.joiner.separator", "Separator string to insert between each argument", ",");
    @SetFromFlag("quote")
    public static ConfigKey<Boolean> QUOTE = ConfigKeys.newBooleanConfigKey("enricher.joiner.quote", "Whether to bash-escape each parameter and wrap in double-quotes, defaulting to true", true);
    @SetFromFlag("minimum")
    public static ConfigKey<Integer> MINIMUM = ConfigKeys.newIntegerConfigKey("enricher.joiner.minimum", "Minimum number of elements to join; if fewer than this, sets null; default 0 (no minimum)");
    @SetFromFlag("maximum")
    public static ConfigKey<Integer> MAXIMUM = ConfigKeys.newIntegerConfigKey("enricher.joiner.maximum", "Maximum number of elements to join; default null means all elements always taken");
    
    protected Entity producer;
    protected AttributeSensor<T> sourceSensor;
    protected Sensor<String> targetSensor;

    public Joiner() {
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);

        this.producer = getConfig(PRODUCER) == null ? entity: getConfig(PRODUCER);
        this.sourceSensor = (AttributeSensor<T>) getRequiredConfig(SOURCE_SENSOR);
        this.targetSensor = (Sensor<String>) getRequiredConfig(TARGET_SENSOR);
        
        subscribe(producer, sourceSensor, this);
        
        Object value = producer.getAttribute((AttributeSensor<?>)sourceSensor);
        // TODO would be useful to have a convenience to "subscribeAndThenIfItIsAlreadySetRunItOnce"
        if (value!=null) {
            onEvent(new BasicSensorEvent(sourceSensor, producer, value, -1));
        }
    }

    @Override
    public void onEvent(SensorEvent<T> event) {
        emit(targetSensor, compute(event));
    }

    protected Object compute(SensorEvent<T> event) {
        Object v = event.getValue();
        Object result = null;
        if (v!=null) {
            if (v instanceof Map) {
                v = ((Map<?,?>)v).values();
            }
            if (!(v instanceof Iterable)) {
                LOG.warn("Enricher "+this+" received a non-iterable value "+v.getClass()+" "+v+"; refusing to join");
            } else {
                MutableList<Object> c1 = MutableList.of();
                Integer maximum = getConfig(MAXIMUM);
                for (Object ci: (Iterable<?>)v) {
                    if (maximum!=null && maximum>=0) {
                        if (c1.size()>=maximum) break;
                    }
                    c1.appendIfNotNull(Strings.toString(ci));
                }
                Integer minimum = getConfig(MINIMUM);
                if (minimum!=null && c1.size() < minimum) {
                    // use default null return value
                } else {
                    if (getConfig(QUOTE)) {
                        MutableList<Object> c2 = MutableList.of();
                        for (Object ci: c1) {
                            c2.add(StringEscapes.BashStringEscapes.wrapBash((String)ci));
                        }
                        c1 = c2;
                    }
                    result = Strings.join(c1, getConfig(SEPARATOR));
                }
            }
        }
        if (LOG.isTraceEnabled())
            LOG.trace("Enricher "+this+" computed "+result+" from "+event);
        return result;
    }
}
