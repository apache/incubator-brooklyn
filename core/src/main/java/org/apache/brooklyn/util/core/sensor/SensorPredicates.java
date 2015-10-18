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
package org.apache.brooklyn.util.core.sensor;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.brooklyn.api.sensor.Sensor;

import com.google.common.base.Predicate;

public class SensorPredicates {

    private SensorPredicates() {
        // not instantiable
    }
    
    public static Predicate<Sensor<?>> nameEqualTo(String sensorName) {
        return new SensorNameEquals(checkNotNull(sensorName, "sensorName"));
    }

    private static class SensorNameEquals implements Predicate<Sensor<?>> {
        
        private final String sensor;

        public SensorNameEquals(String sensor) {
            this.sensor = sensor;
        }

        @Override
        public boolean apply(Sensor<?> other) {
            return this.sensor.equals(other.getName());
        }
    }

}
