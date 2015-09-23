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

import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.core.task.ValueResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.util.Lists;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("serial")
public abstract class Reducer<S, T> extends AbstractEnricher implements SensorEventListener<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(Reducer.class);

    @SetFromFlag("producer")
    public static ConfigKey<Entity> PRODUCER = ConfigKeys.newConfigKey(Entity.class, "enricher.producer");
    public static ConfigKey<Sensor<?>> TARGET_SENSOR = ConfigKeys.newConfigKey(new TypeToken<Sensor<?>>() {}, "enricher.targetSensor");
    public static ConfigKey<List<? extends AttributeSensor<?>>> SOURCE_SENSORS = ConfigKeys.newConfigKey(new TypeToken<List<? extends AttributeSensor<?>>>() {}, "enricher.sourceSensors");
    public static ConfigKey<Function<List<?>,?>> REDUCER_FUNCTION = ConfigKeys.newConfigKey(new TypeToken<Function<List<?>, ?>>() {}, "enricher.reducerFunction");
    @SetFromFlag("transformation")
    public static final ConfigKey<String> REDUCER_FUNCTION_UNTYPED = ConfigKeys.newStringConfigKey("enricher.reducerFunction.untyped",
        "A string matching a pre-defined named reducer function, such as join");
    public static final ConfigKey<Map<String, Object>> PARAMETERS = ConfigKeys.newConfigKey(new TypeToken<Map<String, Object>>() {}, "enricher.reducerFunction.parameters", 
        "A map of parameters to pass into the reducer function");
   
    protected Entity producer;
    protected List<AttributeSensor<S>> subscribedSensors;
    protected Sensor<T> targetSensor;
    protected Function<List<S>, T> reducerFunction;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        Preconditions.checkNotNull(getConfig(SOURCE_SENSORS), "source sensors");

        this.producer = getConfig(PRODUCER) == null ? entity : getConfig(PRODUCER);
        List<AttributeSensor<S>> sensorListTemp = Lists.newArrayList();

        for (Object sensorO : getConfig(SOURCE_SENSORS)) {
            AttributeSensor<S> sensor = Tasks.resolving(sensorO).as(AttributeSensor.class).timeout(ValueResolver.REAL_QUICK_WAIT).context(producer).get();
            if(!sensorListTemp.contains(sensor)) {
                sensorListTemp.add(sensor);
            }
        }
        
        String reducerName = config().get(REDUCER_FUNCTION_UNTYPED);
        Function<List<S>, T> reducerFunction = (Function) config().get(REDUCER_FUNCTION);
        if(reducerFunction == null){
            Map<String, ?> parameters = config().get(PARAMETERS);
            reducerFunction = createReducerFunction(reducerName, parameters);
        }

        this.reducerFunction = reducerFunction;
        Preconditions.checkState(sensorListTemp.size() > 0, "Nothing to reduce");

        for (Sensor<S> sensor : sensorListTemp) {
            subscribe(producer, sensor, this);
        }

        subscribedSensors = ImmutableList.copyOf(sensorListTemp);
    }

    protected abstract Function<List<S>, T> createReducerFunction(String reducerName, Map<String, ?> parameters);
    
    @SuppressWarnings("unchecked")
    @Override
    public void onEvent(SensorEvent<Object> event) {
        Sensor<T> destinationSensor = (Sensor<T>) getConfig(TARGET_SENSOR);

        List<S> values = Lists.newArrayList();

        for (AttributeSensor<S> sourceSensor : subscribedSensors) {
            S resolvedSensorValue = entity.sensors().get(sourceSensor);
            if (resolvedSensorValue == null) {
                // only apply function if all values are resolved
                return;
            }

            values.add(resolvedSensorValue);
        }

        Object result = reducerFunction.apply(values);

        if (LOG.isTraceEnabled()) LOG.trace("enricher {} got {}, propagating via {} as {}",
                new Object[] {this, event, entity, reducerFunction, destinationSensor});

        emit((Sensor<T>)destinationSensor, result);
    }
   
    public static class JoinerReducerFunction<A> implements Function<List<A>, String> {
        
        private Object separator;

        public JoinerReducerFunction(Object separator) {
            this.separator = (separator == null) ? ", " : separator;
        }

        @Override
        public String apply(List<A> input) {
            
            StringBuilder sb = new StringBuilder();
            Iterator<A> it = input.iterator();
            while(it.hasNext()) {
                sb.append(it.next().toString());
                if(it.hasNext()){
                    sb.append(separator);
                }
            }
            return sb.toString();
        }
        
    }

    public static class JoinerFunction extends JoinerReducerFunction<String>{
        
        public JoinerFunction(Object separator) {
            super(separator);
        }
    }

    public static class ToStringReducerFunction<A> implements Function<List<A>, String> {

        @Override
        public String apply(List<A> input) {
            return input.toString();
        }
        
    }
    
    public static class FormatStringReducerFunction<T> implements Function<List<T>, String> {
        
        private String format;

        public FormatStringReducerFunction(String format) {
            this.format = Preconditions.checkNotNull(format, "format");
        }

        @Override
        public String apply(List<T> input) {
            return String.format(format, input.toArray());
        }
        
    }
}