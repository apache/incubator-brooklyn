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

/** Entity initializer which adds a sensor to an entity's type definition.
 * Subclasses must add the feed; this class does not do that. 
 * @since 0.7.0 */
@Beta
public class AddSensor<RT,T extends Sensor<RT>> implements EntityInitializer {
    protected final T sensor;
    public static final ConfigKey<String> SENSOR_NAME = ConfigKeys.newStringConfigKey("name");
    public static final ConfigKey<Duration> SENSOR_PERIOD = ConfigKeys.newConfigKey(Duration.class, "period", "Period, including units e.g. 1m or 5s or 200ms");
    
    // TODO
    public static final ConfigKey<String> SENSOR_TYPE = ConfigKeys.newStringConfigKey("targetType");

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

    public static <T> AttributeSensor<T> newSensor(ConfigBag params) {
        String name = Preconditions.checkNotNull(params.get(SENSOR_NAME), "name must be supplied when defining a sensor");
        String className = Preconditions.checkNotNull(params.get(SENSOR_TYPE), "target class must be supplied when defining a sensor");
        Class<T> type = null;

        try {
            type = (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Invalid target type");
        }
        return Sensors.newSensor(type, name);
    }

}
