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
package brooklyn.event.basic;

import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.management.ManagementContext;

/**
 * A {@link Sensor} describing an attribute that can be configured with a default value.
 *
 * The {@link ConfigKey} has the same type, name and description as the sensor,
 * and is typically used to populate the sensor's value at runtime.
 */
public class BasicAttributeSensorAndConfigKey<T> extends AttributeSensorAndConfigKey<T,T> {
    
    private static final long serialVersionUID = -2204916730008559688L;

    public BasicAttributeSensorAndConfigKey(Class<T> type, String name) {
        this(type, name, name, null);
    }
    public BasicAttributeSensorAndConfigKey(Class<T> type, String name, String description) {
        this(type, name, description, null);
    }
    public BasicAttributeSensorAndConfigKey(Class<T> type, String name, String description, T defaultValue) {
        super(type, type, name, description, defaultValue);
    }

    public BasicAttributeSensorAndConfigKey(TypeToken<T> type, String name) {
        super(type, type, name);
    }

    public BasicAttributeSensorAndConfigKey(TypeToken<T> type, String name, String description) {
        super(type, type, name, description);
    }

    public BasicAttributeSensorAndConfigKey(TypeToken<T> type, String name, String description, Object defaultValue) {
        super(type, type, name, description, defaultValue);
    }

    public BasicAttributeSensorAndConfigKey(AttributeSensorAndConfigKey<T,T> orig, T defaultValue) {
        super(orig, defaultValue);
    }
    
    @Override
    protected T convertConfigToSensor(T value, Entity entity) { return value; }

    @Override
    protected T convertConfigToSensor(T value, ManagementContext managementContext) { return value; }
    
    public static class StringAttributeSensorAndConfigKey extends BasicAttributeSensorAndConfigKey<String> {

        private static final long serialVersionUID = 810512615528081865L;

        public StringAttributeSensorAndConfigKey(AttributeSensorAndConfigKey<String,String> orig, String defaultValue) {
            super(orig, defaultValue);
        }

        public StringAttributeSensorAndConfigKey(String name, String description, String defaultValue) {
            super(String.class, name, description, defaultValue);
        }

        public StringAttributeSensorAndConfigKey(String name, String description) {
            super(String.class, name, description);
        }

        public StringAttributeSensorAndConfigKey(String name) {
            super(String.class, name);
        }
        
    }
    
    public static class IntegerAttributeSensorAndConfigKey extends BasicAttributeSensorAndConfigKey<Integer> {

        private static final long serialVersionUID = 7159564523829723929L;

        public IntegerAttributeSensorAndConfigKey(AttributeSensorAndConfigKey<Integer,Integer> orig, Integer defaultValue) {
            super(orig, defaultValue);
        }

        public IntegerAttributeSensorAndConfigKey(String name, String description, Integer defaultValue) {
            super(Integer.class, name, description, defaultValue);
        }

        public IntegerAttributeSensorAndConfigKey(String name, String description) {
            super(Integer.class, name, description);
        }

        public IntegerAttributeSensorAndConfigKey(String name) {
            super(Integer.class, name);
        }
        
    }

}
