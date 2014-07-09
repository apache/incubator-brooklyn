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
package brooklyn.entity.drivers;

import brooklyn.location.Location;

/**
 * Responsible for creating a driver for a given entity/location. Also used for customizing which 
 * type of driver should be used by entities in given locations.
 * 
 * The idea is that an entity should not be tightly coupled to a specific driver implementation, 
 * so that there is flexibility for driver changes, without changing the entity itself. The 
 * advantage is that drivers can easily be reconfigured, replaced or new drivers for different 
 * environments can be added, without needing to modify Brooklyn.
 * 
 * To obtain an instance of a driver, use {@link #build(DriverDependentEntity, Location)}.
 * This will use the registered driver types, or if one is not registered will fallback to the 
 * default strategy.
 */
public interface EntityDriverManager {

    /**
     * Builds a new {@link EntityDriver} for the given entity/location.
     *
     * @param entity the {@link DriverDependentEntity} to create the {@link EntityDriver} for.
     * @param location the {@link Location} where the {@link DriverDependentEntity} is running.
     * @param <D>
     * @return the creates EntityDriver.
     */
    <D extends EntityDriver> D build(DriverDependentEntity<D> entity, Location location);
    
    <D extends EntityDriver> void registerDriver(Class<D> driverInterface, Class<? extends Location> locationClazz, Class<? extends D> driverClazz);
}
