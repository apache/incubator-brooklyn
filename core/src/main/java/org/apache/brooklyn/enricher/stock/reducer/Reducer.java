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
package org.apache.brooklyn.enricher.stock.reducer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.sensor.SensorPredicates;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.apache.brooklyn.util.text.StringFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Lists;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
public class Reducer extends AbstractEnricher implements SensorEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(Reducer.class);

    @SetFromFlag("producer")
    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");
    public static ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");
    public static ConfigKey<List<? extends AttributeSensor<?>>> SOURCE_SENSORS = ConfigKeys.newConfigKey(new TypeToken<List<? extends AttributeSensor<?>>>() {}, "enricher.sourceSensors");
    public static ConfigKey<Function<List<?>,?>> REDUCER_FUNCTION = ConfigKeys.newConfigKey(new TypeToken<Function<List<?>, ?>>() {}, "enricher.reducerFunction");
    @SetFromFlag("transformation")
    public static final ConfigKey<String> REDUCER_FUNCTION_TRANSFORMATION = ConfigKeys.newStringConfigKey("enricher.reducerFunction.transformation",
        "A string matching a pre-defined named reducer function, such as joiner");
    public static final ConfigKey<Map<String, Object>> PARAMETERS = ConfigKeys.newConfigKey(new TypeToken<Map<String, Object>>() {}, "enricher.reducerFunction.parameters", 
        "A map of parameters to pass into the reducer function");
   
    protected Entity producer;
    protected List<AttributeSensor<?>> subscribedSensors;
    protected Sensor<?> targetSensor;
    protected Function<Iterable<?>, ?> reducerFunction;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        Preconditions.checkNotNull(getConfig(SOURCE_SENSORS), "source sensors");

        this.producer = getConfig(PRODUCER) == null ? entity : getConfig(PRODUCER);
        List<AttributeSensor<?>> sensorListTemp = Lists.newArrayList();

        for (Object sensorO : getConfig(SOURCE_SENSORS)) {
            AttributeSensor<?> sensor = Tasks.resolving(sensorO).as(AttributeSensor.class).timeout(ValueResolver.REAL_QUICK_WAIT).context(producer).get();
            Optional<? extends Sensor<?>> foundSensor = Iterables.tryFind(sensorListTemp, 
                    SensorPredicates.nameEqualTo(sensor.getName()));
            
            if(!foundSensor.isPresent()) {
                sensorListTemp.add(sensor);
            }
        }
        
        String reducerName = config().get(REDUCER_FUNCTION_TRANSFORMATION);
        Function<Iterable<?>, ?> reducerFunction = (Function) config().get(REDUCER_FUNCTION);
        if(reducerFunction == null){
            Map<String, ?> parameters = config().get(PARAMETERS);
            reducerFunction = createReducerFunction(reducerName, parameters);
        }

        this.reducerFunction = reducerFunction;
        Preconditions.checkState(sensorListTemp.size() > 0, "Nothing to reduce");

        for (Sensor<?> sensor : sensorListTemp) {
            subscribe(producer, sensor, this);
        }

        subscribedSensors = ImmutableList.copyOf(sensorListTemp);
    }
    
    // Default implementation, subclasses should override
    protected Function<Iterable<?>, ?> createReducerFunction(String reducerName, Map<String, ?> parameters){
        if(Objects.equals(reducerName, "joiner")){
            String separator = (String) parameters.get("separator");
            return StringFunctions.joiner(separator == null ? ", " : separator);
        }
        
        if (Objects.equals(reducerName, "formatString")){
            String format = Preconditions.checkNotNull((String)parameters.get("format"), "format");
            return StringFunctions.formatterForIterable(format);
        }
        throw new IllegalStateException("unknown function: " + reducerName);
    }
    
    @Override
    public void onEvent(SensorEvent<Object> event) {
        Sensor<?> destinationSensor = getConfig(TARGET_SENSOR);

        List<Object> values = Lists.newArrayList();

        for (AttributeSensor<?> sourceSensor : subscribedSensors) {
            Object resolvedSensorValue = entity.sensors().get(sourceSensor);
            values.add(resolvedSensorValue);
        }
        Object result = reducerFunction.apply(values);

        if (LOG.isTraceEnabled()) LOG.trace("enricher {} got {}, propagating via {} as {}",
                new Object[] {this, event, entity, reducerFunction, destinationSensor});

        emit(destinationSensor, result);
    }
}