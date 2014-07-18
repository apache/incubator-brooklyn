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
package brooklyn.entity.effector;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * Creates a new sensor. The configuration can include the sensor {@code name} and {@code targetType}.
 * For the targetType, currently this only supports classes on the initial classpath
 * (e.g. not those in OSGi bundles added at runtime).
 * @since 0.7.0
 * */
@Beta
public class AddSensor<RT,T extends Sensor<RT>> implements EntityInitializer {
    protected final T sensor;
    public static final ConfigKey<String> SENSOR_NAME = ConfigKeys.newStringConfigKey("name");
    public static final ConfigKey<Duration> SENSOR_PERIOD = ConfigKeys.newConfigKey(Duration.class, "period", "Period, including units e.g. 1m or 5s or 200ms");
    public static final ConfigKey<String> SENSOR_TYPE = ConfigKeys.newStringConfigKey("targetType", "Target type for the value", "string");

    public AddSensor(T sensor) {
        this.sensor = Preconditions.checkNotNull(sensor, "sensor");
    }
    
    @Override
    public void apply(EntityLocal entity) {
        ((EntityInternal)entity).getMutableEntityType().addSensor(sensor);
    }

    public static <T> AttributeSensor<T> newSensor(Class<T> type, ConfigBag params){
        String name = Preconditions.checkNotNull(params.get(SENSOR_NAME), "name must be supplied when defining a sensor");
        return Sensors.newSensor(type, name);
    }

    @SuppressWarnings("unchecked")
    public static <T> AttributeSensor<T> newSensor(ConfigBag params) {
        String name = Preconditions.checkNotNull(params.get(SENSOR_NAME), "name must be supplied when defining a sensor");
        String className = getFullClassName(params.get(SENSOR_TYPE));
        Class<T> type = null;

        try {
            type = (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Invalid target type for sensor "+name+": " + className);
        }
        return Sensors.newSensor(type, name);
    }

    private static String getFullClassName(String className) {
        if(className.equalsIgnoreCase("string")){
            return "java.lang.String";
        }else if(className.equalsIgnoreCase("int") || className.equalsIgnoreCase("integer")){
            return "java.lang.Integer";
        }else if(className.equalsIgnoreCase("long")){
            return "java.lang.Long";
        }else if(className.equalsIgnoreCase("float")){
            return "java.lang.Float";
        }else if(className.equalsIgnoreCase("double")){
            return "java.lang.Double";
        }else if(className.equalsIgnoreCase("bool") || className.equalsIgnoreCase("boolean")){
            return "java.lang.Boolean";
        }else if(className.equalsIgnoreCase("object")){
            return "java.lang.Object";
        }else{
            return className;
        }
    }

}
