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

import brooklyn.event.AttributeSensor;

import com.google.common.reflect.TypeToken;

public class Sensors {

    public static <T> AttributeSensor<T> newSensor(Class<T> type, String name) {
        return new BasicAttributeSensor<T>(type, name);
    }

    public static <T> AttributeSensor<T> newSensor(Class<T> type, String name, String description) {
        return new BasicAttributeSensor<T>(type, name, description);
    }

    public static <T> AttributeSensor<T> newSensor(TypeToken<T> type, String name, String description) {
        return new BasicAttributeSensor<T>(type, name, description);
    }

    public static AttributeSensor<String> newStringSensor(String name) {
        return newSensor(String.class, name);
    }

    public static AttributeSensor<String> newStringSensor(String name, String description) {
        return newSensor(String.class, name, description);
    }

    public static AttributeSensor<Integer> newIntegerSensor(String name) {
        return newSensor(Integer.class, name);
    }

    public static AttributeSensor<Integer> newIntegerSensor(String name, String description) {
        return newSensor(Integer.class, name, description);
    }

    public static AttributeSensor<Long> newLongSensor(String name) {
        return newSensor(Long.class, name);
    }

    public static AttributeSensor<Long> newLongSensor(String name, String description) {
        return newSensor(Long.class, name, description);
    }

    public static AttributeSensor<Double> newDoubleSensor(String name) {
        return newSensor(Double.class, name);
    }

    public static AttributeSensor<Double> newDoubleSensor(String name, String description) {
        return newSensor(Double.class, name, description);
    }

    public static AttributeSensor<Boolean> newBooleanSensor(String name) {
        return newSensor(Boolean.class, name);
    }

    public static AttributeSensor<Boolean> newBooleanSensor(String name, String description) {
        return newSensor(Boolean.class, name, description);
    }

    // ---- extensions to sensors
    
    public static <T> AttributeSensor<T> newSensorRenamed(String newName, AttributeSensor<T> sensor) {
        return new BasicAttributeSensor<T>(sensor.getTypeToken(), newName, sensor.getDescription());
    }

    public static <T> AttributeSensor<T> newSensorWithPrefix(String prefix, AttributeSensor<T> sensor) {
        return newSensorRenamed(prefix+sensor.getName(), sensor);
    }

}
