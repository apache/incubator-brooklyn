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

import java.util.Map;

import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.entity.proxying.EntityInitializer;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.Boxing;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/**
 * Creates a new {@link AttributeSensor} on an entity.
 * <p>
 * The configuration can include the sensor {@code name}, {@code period} and {@code targetType}.
 * For the targetType, currently this only supports classes on the initial classpath, not those in
 * OSGi bundles added at runtime.
 *
 * @since 0.7.0
 */
@Beta
public class AddSensor<T> implements EntityInitializer {

    public static final ConfigKey<String> SENSOR_NAME = ConfigKeys.newStringConfigKey("name", "The name of the sensor to create");
    public static final ConfigKey<Duration> SENSOR_PERIOD = ConfigKeys.newConfigKey(Duration.class, "period", "Period, including units e.g. 1m or 5s or 200ms; default 5 minutes", Duration.FIVE_MINUTES);
    public static final ConfigKey<String> SENSOR_TYPE = ConfigKeys.newStringConfigKey("targetType", "Target type for the value; default String", "java.lang.String");

    protected final String name;
    protected final Duration period;
    protected final String type;
    protected final AttributeSensor<T> sensor;

    public AddSensor(Map<String, String> params) {
        this(ConfigBag.newInstance(params));
    }

    public AddSensor(final ConfigBag params) {
        this.name = Preconditions.checkNotNull(params.get(SENSOR_NAME), "Name must be supplied when defining a sensor");
        this.period = params.get(SENSOR_PERIOD);
        this.type = params.get(SENSOR_TYPE);
        this.sensor = newSensor();
    }

    @Override
    public void apply(EntityLocal entity) {
        ((EntityInternal) entity).getMutableEntityType().addSensor(sensor);
    }

    private AttributeSensor<T> newSensor() {
        String className = getFullClassName(type);
        Class<T> clazz = getType(className);
        return Sensors.newSensor(clazz, name);
    }

    @SuppressWarnings("unchecked")
    protected Class<T> getType(String className) {
        try {
            // TODO use OSGi loader (low priority however); also ensure that allows primitives
            Maybe<Class<?>> primitive = Boxing.getPrimitiveType(className);
            if (primitive.isPresent()) return (Class<T>) primitive.get();
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            if (!className.contains(".")) {
                // could be assuming "java.lang" package; try again with that
                try {
                    return (Class<T>) Class.forName("java.lang."+className);
                } catch (ClassNotFoundException e2) {
                    throw new IllegalArgumentException("Invalid target type for sensor "+name+": " + className+" (also tried java.lang."+className+")");
                }
            } else {
                throw new IllegalArgumentException("Invalid target type for sensor "+name+": " + className);
            }
        }
    }

    protected String getFullClassName(String className) {
        if (className.equalsIgnoreCase("string")) {
            return "java.lang.String";
        } else if (className.equalsIgnoreCase("int") || className.equalsIgnoreCase("integer")) {
            return "java.lang.Integer";
        } else if (className.equalsIgnoreCase("long")) {
            return "java.lang.Long";
        } else if (className.equalsIgnoreCase("float")) {
            return "java.lang.Float";
        } else if (className.equalsIgnoreCase("double")) {
            return "java.lang.Double";
        } else if (className.equalsIgnoreCase("bool") || className.equalsIgnoreCase("boolean")) {
            return "java.lang.Boolean";
        } else if (className.equalsIgnoreCase("byte")) {
            return "java.lang.Byte";
        } else if (className.equalsIgnoreCase("char") || className.equalsIgnoreCase("character")) {
            return "java.lang.Character";
        } else if (className.equalsIgnoreCase("object")) {
            return "java.lang.Object";
        } else {
            return className;
        }
    }

}
