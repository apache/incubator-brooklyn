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
package brooklyn.event;

import brooklyn.entity.Entity;

/**
 * A tuple representing a piece of data from a {@link Sensor} on an {@link Entity}.
 */
public interface SensorEvent<T> {
    /**
     * The {@link Entity} where the data originated.
     */
    Entity getSource();
 
    /**
     * The {@link Sensor} describing the data.
     */
    Sensor<T> getSensor();
 
    /**
     * The value for the {@link Sensor} data.
     */
    T getValue();

    /**
     * The time this data was published, as a UTC time in milliseconds (e.g. as returned
     * by {@link System#currentTimeMillis()}.
     */
    long getTimestamp();
}