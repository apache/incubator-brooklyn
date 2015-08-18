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
package org.apache.brooklyn.api.sensor;

import com.google.common.annotations.Beta;

/**
 * The interface implemented by attribute sensors.
 */
public interface AttributeSensor<T> extends Sensor<T> {
    
    /**
     * @since 0.7.0
     */
    @Beta
    public enum SensorPersistenceMode {
        /**
         * Indicates that this sensor should be persisted, and its value should be read from
         * persisted state on rebind.
         */
        REQUIRED,
        
        /**
         * Indicates that this sensor should not be persisted; therefore its value for any entity
         * will be null immediately after rebind.
         */
        NONE;
    }
    
    /**
     * The persistence mode of this sensor, to determine its behaviour for rebind.
     * 
     * @since 0.7.0
     */
    SensorPersistenceMode getPersistenceMode();
}
